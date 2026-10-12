package com.ninabit.bono.modules.vendorprize;

import com.ninabit.bono.modules.vendor.Vendedor;
import com.ninabit.bono.modules.vendor.VendedorRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Canjes de Puntos Vendedor")
@Slf4j
public class CanjePuntosController {

    private final PuntosVendedorService puntosService;
    private final CanjePuntosRepository canjeRepository;
    private final VendedorRepository vendedorRepository;
    private final TicketVendedorRepository ticketVendedorRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @PostMapping("/canjes")
    @Operation(summary = "Solicitar canje de premio por puntos (Vendedor autenticado)")
    public ResponseEntity<?> solicitarCanje(@RequestBody Map<String, Object> req, Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
            }

            Vendedor vendedor = vendedorRepository.findByEmail(auth.getName())
                    .orElseThrow(() -> new IllegalArgumentException("Vendedor no encontrado con email: " + auth.getName()));

            if (req.get("premioId") == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "El ID del premio es requerido"));
            }

            Long premioId = ((Number) req.get("premioId")).longValue();
            String observaciones = (String) req.get("observaciones");

            CanjePuntos canje = puntosService.solicitarCanje(
                    vendedor.getId(),
                    premioId,
                    observaciones,
                    auth.getName()
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "mensaje", "¡Solicitud de canje enviada con éxito!",
                    "canje", canje
            ));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error al procesar canje", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Error interno al procesar el canje"));
        }
    }

    @GetMapping("/canjes/my")
    @Operation(summary = "Listar canjes del vendedor autenticado")
    public ResponseEntity<?> getMyCanjes(Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        Vendedor vendedor = vendedorRepository.findByEmail(auth.getName()).orElse(null);
        if (vendedor == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(canjeRepository.findByVendedorIdOrderByFechaSolicitudDesc(vendedor.getId()));
    }

    @GetMapping("/canjes/admin")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    @Operation(summary = "Listar todos los canjes para supervisión y administración")
    public List<CanjePuntos> getAllCanjesAdmin(@RequestParam(required = false) CanjePuntos.EstadoCanje estado) {
        if (estado != null) {
            return canjeRepository.findByEstadoOrderByFechaSolicitudDesc(estado);
        }
        return canjeRepository.findAllByOrderByFechaSolicitudDesc();
    }

    @PutMapping("/canjes/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    @Operation(summary = "Cambiar estado de solicitud de canje (APROBADO, RECHAZADO, ENTREGADO)")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> req,
            Authentication auth
    ) {
        try {
            String nuevoEstadoStr = (String) req.get("estado");
            if (nuevoEstadoStr == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "El campo estado es requerido"));
            }

            CanjePuntos.EstadoCanje nuevoEstado = CanjePuntos.EstadoCanje.valueOf(nuevoEstadoStr.toUpperCase());
            String motivoRechazo = (String) req.get("motivoRechazo");
            String fotoComprobante = (String) req.get("fotoComprobante");
            String usuario = auth != null ? auth.getName() : "ADMIN";

            CanjePuntos updated = puntosService.cambiarEstadoCanje(
                    id, nuevoEstado, motivoRechazo, fotoComprobante, usuario
            );

            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error al actualizar estado del canje", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Error interno"));
        }
    }

    @PostMapping("/canjes/{id}/upload-comprobante")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    @Operation(summary = "Subir acta o comprobante de entrega física del premio")
    public ResponseEntity<?> uploadComprobante(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Authentication auth
    ) {
        return canjeRepository.findById(id).map(canje -> {
            try {
                File dir = new File(uploadDir + "/canjes");
                if (!dir.exists()) dir.mkdirs();

                String ext = "";
                String orig = file.getOriginalFilename();
                if (orig != null && orig.contains(".")) {
                    ext = orig.substring(orig.lastIndexOf("."));
                }
                String filename = "comprobante_canje_" + id + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
                Path path = Paths.get(uploadDir, "canjes", filename);
                Files.write(path, file.getBytes());

                String relativeUrl = "uploads/canjes/" + filename;
                canje.setFotoComprobante(relativeUrl);
                canjeRepository.save(canje);

                return ResponseEntity.ok(Map.of("url", relativeUrl, "canje", canje));
            } catch (IOException e) {
                log.error("Error al subir comprobante", e);
                return ResponseEntity.internalServerError().body(Map.of("error", "Error al guardar el archivo"));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/vendor/puntos-balance")
    @Operation(summary = "Obtener balance consolidado de puntos del vendedor autenticado por campaña")
    public ResponseEntity<?> getVendorPuntosBalance(
            @RequestParam(required = false) Long campanaId,
            Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        Vendedor vendedor = vendedorRepository.findByEmail(auth.getName()).orElse(null);
        if (vendedor == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(puntosService.getResumenPuntos(vendedor.getId(), campanaId));
    }

    @GetMapping("/vendor/tickets")
    @Operation(summary = "Listar tickets de rifa/sorteo asignados al vendedor autenticado")
    public ResponseEntity<?> getVendorTickets(Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        Vendedor vendedor = vendedorRepository.findByEmail(auth.getName()).orElse(null);
        if (vendedor == null) {
            return ResponseEntity.notFound().build();
        }
        List<TicketVendedor> tickets = ticketVendedorRepository.findByVendedorIdOrderByCreatedAtDesc(vendedor.getId());
        return ResponseEntity.ok(tickets);
    }
}
