package com.ninabit.bono.modules.damagedequipment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/equipos-danados")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Equipos Dañados", description = "Gestión de registros de equipos dañados para personal técnico y administrativo")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'TECNICO')")
public class EquipoDanadoController {

    private final EquipoDanadoService equipoDanadoService;
    private final EquipoDanadoExcelGenerator excelGenerator;

    @GetMapping("/clientes")
    @Operation(summary = "Listar catálogo de clientes para autocompletado y búsqueda rápida")
    public ResponseEntity<List<CatalogoClienteDanado>> getClientes(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(equipoDanadoService.getClientesActivos(search));
    }

    @GetMapping("/clientes/todos")
    @Operation(summary = "Listar todos los clientes incluyendo inactivos para administración")
    public ResponseEntity<List<CatalogoClienteDanado>> getTodosLosClientes(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(equipoDanadoService.getTodosLosClientes(search));
    }

    @PostMapping("/clientes")
    @Operation(summary = "Agregar nuevo cliente al catálogo")
    public ResponseEntity<CatalogoClienteDanado> agregarCliente(@RequestBody Map<String, String> payload) {
        String nombre = payload.get("nombre");
        String ciudad = payload.get("ciudad");
        if (nombre == null || nombre.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(equipoDanadoService.agregarCliente(nombre, ciudad));
    }

    @PutMapping("/clientes/{id}")
    @Operation(summary = "Actualizar datos de un cliente existente")
    public ResponseEntity<CatalogoClienteDanado> actualizarCliente(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload
    ) {
        String nombre = (String) payload.get("nombre");
        String ciudad = (String) payload.get("ciudad");
        Boolean activo = payload.get("activo") != null ? Boolean.valueOf(payload.get("activo").toString()) : null;
        return ResponseEntity.ok(equipoDanadoService.actualizarCliente(id, nombre, ciudad, activo));
    }

    @DeleteMapping("/clientes/{id}")
    @Operation(summary = "Deshabilitar un cliente del catálogo")
    public ResponseEntity<Void> eliminarCliente(@PathVariable Long id) {
        equipoDanadoService.eliminarCliente(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/fallas")
    @Operation(summary = "Listar catálogo de fallas comunes activas")
    public ResponseEntity<List<CatalogoFalla>> getFallas() {
        return ResponseEntity.ok(equipoDanadoService.getFallasActivas());
    }

    @GetMapping("/fallas/todas")
    @Operation(summary = "Listar todas las fallas incluyendo inactivas para administración")
    public ResponseEntity<List<CatalogoFalla>> getTodasLasFallas() {
        return ResponseEntity.ok(equipoDanadoService.getTodasLasFallas());
    }

    @PostMapping("/fallas")
    @Operation(summary = "Agregar nueva falla al catálogo")
    public ResponseEntity<CatalogoFalla> agregarFalla(@RequestBody Map<String, String> payload) {
        String nombre = payload.get("nombre");
        String descripcion = payload.get("descripcion");
        if (nombre == null || nombre.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(equipoDanadoService.agregarFalla(nombre, descripcion));
    }

    @PutMapping("/fallas/{id}")
    @Operation(summary = "Actualizar falla existente")
    public ResponseEntity<CatalogoFalla> actualizarFalla(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload
    ) {
        String nombre = (String) payload.get("nombre");
        String descripcion = (String) payload.get("descripcion");
        Boolean activo = payload.get("activo") != null ? Boolean.valueOf(payload.get("activo").toString()) : null;
        return ResponseEntity.ok(equipoDanadoService.actualizarFalla(id, nombre, descripcion, activo));
    }

    @DeleteMapping("/fallas/{id}")
    @Operation(summary = "Deshabilitar una falla del catálogo")
    public ResponseEntity<Void> eliminarFalla(@PathVariable Long id) {
        equipoDanadoService.eliminarFalla(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/serial-info/{serial}")
    @Operation(summary = "Consultar datos del equipo por número de serie")
    public ResponseEntity<Map<String, Object>> getSerialInfo(@PathVariable String serial) {
        return ResponseEntity.ok(equipoDanadoService.lookupSerialInfo(serial));
    }

    @PostMapping(value = "/decode-barcode", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Decodificar código de barras de una imagen subida (fallback backend)")
    public ResponseEntity<Map<String, Object>> decodeBarcode(@RequestParam("foto") MultipartFile foto) {
        String serial = equipoDanadoService.decodeBarcodeFromImage(foto);
        if (serial != null) {
            Map<String, Object> info = equipoDanadoService.lookupSerialInfo(serial);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "serial", serial,
                    "info", info
            ));
        }
        return ResponseEntity.ok(Map.of(
                "success", false,
                "mensaje", "No se detectó código de barras legible en la imagen"
        ));
    }

    @GetMapping
    @Operation(summary = "Listar registros de equipos dañados con filtros y paginación")
    public ResponseEntity<Page<EquipoDanado>> getEquipos(
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String serial,
            @RequestParam(required = false) String modelo,
            @RequestParam(required = false) String tipoFalla,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        LocalDateTime dtInicio = fechaInicio != null ? fechaInicio.atStartOfDay() : null;
        LocalDateTime dtFin = fechaFin != null ? fechaFin.atTime(LocalTime.MAX) : null;

        return ResponseEntity.ok(equipoDanadoService.getEquiposPage(
                ciudad, cliente, serial, modelo, tipoFalla, dtInicio, dtFin, pageable
        ));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener detalle de un equipo dañado por ID")
    public ResponseEntity<EquipoDanado> getEquipoById(@PathVariable Long id) {
        return equipoDanadoService.getEquipoById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar un registro de equipo dañado (Solo Administradores)")
    public ResponseEntity<Void> eliminarEquipoDanado(@PathVariable Long id, Authentication auth) {
        equipoDanadoService.eliminarEquipoDanado(id, auth != null ? auth.getName() : "ADMIN");
        return ResponseEntity.noContent().build();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Registrar equipo dañado")
    public ResponseEntity<?> registrarEquipo(
            @RequestParam(value = "registroClienteId", required = false) Long registroClienteId,
            @RequestParam(value = "clienteNombre", required = false) String clienteNombre,
            @RequestParam(value = "ciudad", required = false) String ciudad,
            @RequestParam("serialCaja") String serialCaja,
            @RequestParam("serialTv") String serialTv,
            @RequestParam(value = "serialIngresoManual", required = false, defaultValue = "false") Boolean serialIngresoManual,
            @RequestParam("serialValidado") String serialValidado,
            @RequestParam(value = "modelo", required = false) String modelo,
            @RequestParam(value = "pulgadas", required = false) Integer pulgadas,
            @RequestParam("tipoFalla") String tipoFalla,
            @RequestParam(value = "comentarios", required = false) String comentarios,
            @RequestParam("fotoTagCaja") MultipartFile fotoTagCaja,
            @RequestParam("fotoTagTv") MultipartFile fotoTagTv,
            @RequestParam(value = "fotoFalla1", required = false) MultipartFile fotoFalla1,
            @RequestParam(value = "fotoFalla2", required = false) MultipartFile fotoFalla2,
            @RequestParam(value = "fotoFalla3", required = false) MultipartFile fotoFalla3,
            Authentication authentication
    ) {
        try {
            if (fotoTagCaja == null || fotoTagCaja.isEmpty() || fotoTagTv == null || fotoTagTv.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Las fotos del tag de la caja y del televisor son obligatorias"));
            }

            EquipoDanado saved = equipoDanadoService.registrarEquipoDanado(
                    registroClienteId,
                    clienteNombre,
                    ciudad,
                    serialCaja,
                    serialTv,
                    serialIngresoManual,
                    serialValidado,
                    modelo,
                    pulgadas,
                    tipoFalla,
                    comentarios,
                    fotoTagCaja,
                    fotoTagTv,
                    fotoFalla1,
                    fotoFalla2,
                    fotoFalla3,
                    authentication.getName()
            );

            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("Error guardando archivos de equipo dañado", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al almacenar las imágenes: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error inesperado registrando equipo dañado", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Error interno: " + e.getMessage()));
        }
    }

    @GetMapping("/export-excel")
    @Operation(summary = "Exportar registros de equipos dañados a Excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String serial,
            @RequestParam(required = false) String modelo,
            @RequestParam(required = false) String tipoFalla,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            HttpServletRequest request
    ) {
        LocalDateTime dtInicio = fechaInicio != null ? fechaInicio.atStartOfDay() : null;
        LocalDateTime dtFin = fechaFin != null ? fechaFin.atTime(LocalTime.MAX) : null;

        List<EquipoDanado> list = equipoDanadoService.getEquiposListForExport(
                ciudad, cliente, serial, modelo, tipoFalla, dtInicio, dtFin
        );

        String filename = "Reporte_Equipos_Danados_" + LocalDate.now() + ".xlsx";
        byte[] excelBytes = excelGenerator.generateExcelReport(list, null);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }
}
