package com.ninabit.bono.modules.payment;

import com.ninabit.bono.modules.sale.VentaCampana;
import com.ninabit.bono.modules.sale.VentaCampanaRepository;
import com.ninabit.bono.modules.vendor.Vendedor;
import com.ninabit.bono.modules.vendor.VendedorRepository;
import com.ninabit.bono.modules.user.Usuario;
import com.ninabit.bono.modules.user.UsuarioRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.HashMap;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Tag(name = "Pagos")
public class PagoController {

    private final PagoRepository pagoRepository;
    private final VendedorRepository vendedorRepository;
    private final VentaCampanaRepository ventaCampanaRepository;
    private final UsuarioRepository usuarioRepository;
    private final com.ninabit.bono.modules.report.PaymentExcelGenerator paymentExcelGenerator;
    private final com.ninabit.bono.modules.audit.AuditoriaService auditoriaService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener vendedores con comisiones pendientes de pago")
    public List<Map<String, Object>> getPendingPayments() {
        // Encontrar todas las VentaCampana (aprobadas) que no tienen pago asignado
        List<VentaCampana> pendientes = ventaCampanaRepository
                .findByPagoIsNullAndVenta_Estado(com.ninabit.bono.modules.sale.Venta.Estado.APROBADA);

        // Agrupar por vendedor
        Map<Vendedor, List<VentaCampana>> porVendedor = pendientes.stream()
                .collect(Collectors
                        .groupingBy(vc -> vendedorRepository.findById(vc.getVenta().getVendedorId()).orElse(null)));

        return porVendedor.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .map(e -> {
                    Vendedor v = e.getKey();
                    int totalBs = e.getValue().stream()
                            .mapToInt(vc -> vc.getBonoGanado() != null ? vc.getBonoGanado() : 0).sum();

                    Map<String, Object> map = new HashMap<>();
                    map.put("vendedorId", v.getId());
                    map.put("vendedorNombre", v.getNombreCompleto());
                    map.put("ciudad", v.getCiudad() != null ? v.getCiudad().getNombre() : null);
                    map.put("fotoQr", v.getFotoQr());
                    map.put("tallaPolera", v.getTallaPolera());
                    map.put("montoTotal", totalBs);
                    map.put("cantidadVentas", e.getValue().size());
                    return map;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/commissions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener reporte avanzado de comisiones con filtros")
    public ResponseEntity<List<CommissionReportDTO>> getCommissionsReport(
            @RequestParam(required = false) Long campanaId,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String estadoPago) {
        if ("Todaslasciudades".equalsIgnoreCase(ciudad) || "Todas".equalsIgnoreCase(ciudad)
                || "Todos".equalsIgnoreCase(ciudad)) {
            ciudad = null;
        }
        if ("Todos".equalsIgnoreCase(estadoPago)) {
            estadoPago = null;
        }
        List<CommissionReportDTO> report = ventaCampanaRepository.getCommissionReport(campanaId, ciudad, startDate,
                endDate, estadoPago);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/export-excel")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exportar reporte de pagos a Excel")
    public ResponseEntity<byte[]> exportCommissionsExcel(
            @RequestParam(required = false) Long campanaId,
            @RequestParam(required = false) String campanaNombre,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String estadoPago,
            @RequestParam(defaultValue = "false") boolean withQr) {
        if ("Todaslasciudades".equalsIgnoreCase(ciudad) || "Todas".equalsIgnoreCase(ciudad)
                || "Todos".equalsIgnoreCase(ciudad)) {
            ciudad = null;
        }
        if ("Todos".equalsIgnoreCase(estadoPago)) {
            estadoPago = null;
        }

        List<CommissionReportDTO> report = ventaCampanaRepository.getCommissionReport(campanaId, ciudad, startDate,
                endDate, estadoPago);

        String nombreCampanaReporte = (campanaNombre != null && !campanaNombre.trim().isEmpty()) ? campanaNombre
                : "Todas las Campañas";
        byte[] excelBytes = paymentExcelGenerator.generatePaymentsReport(report, withQr, nombreCampanaReporte, ciudad,
                startDate, endDate, estadoPago);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType
                .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "Liquidaciones_Comisiones.xlsx");
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return org.springframework.http.ResponseEntity.ok()
                .headers(headers)
                .body(excelBytes);
    }

    @PostMapping("/pay/{vendedorId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Generar pago y subir comprobante para un vendedor")
    public ResponseEntity<Pago> payVendor(
            @PathVariable Long vendedorId,
            @RequestParam MultipartFile fotoComprobante,
            @RequestParam(required = false) Long campanaId,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication auth) {
        if ("Todaslasciudades".equalsIgnoreCase(ciudad) || "Todas".equalsIgnoreCase(ciudad)
                || "Todos".equalsIgnoreCase(ciudad)) {
            ciudad = null;
        }

        Vendedor vendedor = vendedorRepository.findById(vendedorId).orElse(null);
        if (vendedor == null)
            return ResponseEntity.notFound().build();

        List<VentaCampana> pendientes = ventaCampanaRepository.findPendingToPay(
                vendedorId, campanaId, ciudad, startDate, endDate);

        if (pendientes.isEmpty())
            return ResponseEntity.badRequest().build(); // Nada que pagar

        int totalBs = pendientes.stream().mapToInt(vc -> vc.getBonoGanado() != null ? vc.getBonoGanado() : 0).sum();

        try {
            String path = saveFile(fotoComprobante);

            Pago pago = Pago.builder()
                    .vendedor(vendedor)
                    .montoTotal(totalBs)
                    .fecha(LocalDate.now())
                    .fotoComprobante(path)
                    .estado(Pago.Estado.PAGADO)
                    .build();

            Pago saved = pagoRepository.save(pago);

            for (VentaCampana vc : pendientes) {
                vc.setPago(saved);
                ventaCampanaRepository.save(vc);
            }

            auditoriaService.log(auth.getName(), "PAGO_GENERADO", 
                "Pago generado a vendedor " + vendedor.getNombreCompleto() + " por Bs " + totalBs, 
                "Pago", saved.getId().toString());

            return ResponseEntity.ok(saved);
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar comprobante", e);
        }
    }

    @GetMapping("/my-payments")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Ver historial de pagos del vendedor autenticado")
    public ResponseEntity<List<Pago>> getMyPayments(Authentication auth) {
        Usuario usuario = usuarioRepository.findByEmail(auth.getName()).orElse(null);
        if (usuario == null || usuario.getVendedorId() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(pagoRepository.findByVendedorIdOrderByFechaDesc(usuario.getVendedorId()));
    }

    @GetMapping("/vendor/{vendedorId}")
    @PreAuthorize("hasAnyRole('VENDOR', 'ADMIN')")
    @Operation(summary = "Ver historial de pagos por vendedor Id")
    public ResponseEntity<List<Pago>> getPaymentsByVendor(@PathVariable Long vendedorId) {
        return ResponseEntity.ok(pagoRepository.findByVendedorIdOrderByFechaDesc(vendedorId));
    }

    private String saveFile(MultipartFile file) throws IOException {
        File dir = new File(uploadDir);
        if (!dir.exists())
            dir.mkdirs();
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path path = Paths.get(uploadDir, filename);
        Files.write(path, file.getBytes());
        return "uploads/" + filename;
    }
}
