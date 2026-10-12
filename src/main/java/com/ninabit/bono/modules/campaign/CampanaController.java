package com.ninabit.bono.modules.campaign;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.ninabit.bono.modules.product.Producto;
import com.ninabit.bono.modules.product.ProductoRepository;
import com.ninabit.bono.modules.sale.VentaCampanaRepository;
import com.ninabit.bono.modules.tienda.Tienda;
import com.ninabit.bono.modules.tienda.TiendaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campañas")
public class CampanaController {

    private final CampanaRepository campanaRepository;
    private final CampanaProductoRepository campanaProductoRepository;
    private final ProductoRepository productoRepository;
    private final VentaCampanaRepository ventaCampanaRepository;
    private final TiendaRepository tiendaRepository;
    private final com.ninabit.bono.modules.audit.AuditoriaService auditoriaService;

    @GetMapping
    @Operation(summary = "Listar todas las campañas")
    public List<Campana> getAll() {
        return campanaRepository.findAllByOrderByIdDesc();
    }

    @GetMapping("/active")
    @Operation(summary = "Obtener campaña activa")
    public ResponseEntity<Campana> getActive() {
        return campanaRepository.findFirstByActivoTrue()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/public")
    @Operation(summary = "Listar campañas públicas activas")
    public List<Campana> getPublic() {
        return campanaRepository.findAllByOrderByIdDesc().stream()
                .filter(c -> c.isActivo() || c.isRegistroHabilitado())
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear campaña")
    public Campana create(@RequestBody Campana campana) {
        Campana saved = campanaRepository.save(campana);
        auditoriaService.log(getUser(), "CAMPANA_CREADA", 
            "Campaña creada: " + saved.getNombre(), "Campana", saved.getId().toString());
        return saved;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar campaña")
    public ResponseEntity<Campana> update(@PathVariable Long id, @RequestBody Campana campana) {
        if (!campanaRepository.existsById(id))
            return ResponseEntity.notFound().build();
        campana.setId(id);
        Campana saved = campanaRepository.save(campana);
        auditoriaService.log(getUser(), "CAMPANA_ACTUALIZADA", 
            "Campaña actualizada: " + saved.getNombre(), "Campana", id.toString());
        return ResponseEntity.ok(saved);
    }
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar campaña (solo si no tiene ventas registradas)")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return campanaRepository.findById(id).map(campana -> {
            if (ventaCampanaRepository.existsByCampanaId(id)) {
                return ResponseEntity.status(409)
                    .body(Map.of("error", "No se puede eliminar: la campaña tiene ventas registradas."));
            }
            // Eliminar productos de campaña primero
            campanaProductoRepository.findByCampanaId(id).forEach(campanaProductoRepository::delete);
            campanaRepository.delete(campana);
            auditoriaService.log(getUser(), "CAMPANA_ELIMINADA",
                "Campaña eliminada: " + campana.getNombre(), "Campana", id.toString());
            return ResponseEntity.ok(Map.of("message", "Campaña eliminada correctamente"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Duplicar campaña con sus reglas de productos y parámetros sin registros operativos")
    public ResponseEntity<?> duplicate(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> req) {
        return campanaRepository.findById(id).map(original -> {
            String nuevoNombre = req != null && req.get("nombre") != null && !req.get("nombre").toString().isBlank()
                    ? req.get("nombre").toString().trim()
                    : original.getNombre() + " (Copia)";
            
            LocalDate fechaInicio = original.getFechaInicio();
            LocalDate fechaFin = original.getFechaFin();
            if (req != null && req.get("fechaInicio") != null && !req.get("fechaInicio").toString().isBlank()) {
                try { fechaInicio = LocalDate.parse(req.get("fechaInicio").toString()); } catch (Exception ignored) {}
            }
            if (req != null && req.get("fechaFin") != null && !req.get("fechaFin").toString().isBlank()) {
                try { fechaFin = LocalDate.parse(req.get("fechaFin").toString()); } catch (Exception ignored) {}
            }

            Campana nuevaCampana = Campana.builder()
                    .nombre(nuevoNombre)
                    .subtitulo(original.getSubtitulo())
                    .fechaInicio(fechaInicio)
                    .fechaFin(fechaFin)
                    .estadoCampana(Campana.EstadoCampana.BORRADOR) // Inicia en BORRADOR según §1
                    .activo(false) // Safe default: inactiva hasta que el admin decida activarla
                    .registroHabilitado(original.isRegistroHabilitado())
                    .validacionIa(original.isValidacionIa())
                    .tiendasExcluidas(new HashSet<>(original.getTiendasExcluidas() != null ? original.getTiendasExcluidas() : Set.of()))
                    .build();

            Campana savedCampana = campanaRepository.save(nuevaCampana);

            // Copiar reglas de productos asignados (§4.1 y §7)
            List<CampanaProducto> productosOriginales = campanaProductoRepository.findByCampanaIdAndActivoTrue(id);
            for (CampanaProducto cp : productosOriginales) {
                CampanaProducto nuevoCp = CampanaProducto.builder()
                        .campana(savedCampana)
                        .producto(cp.getProducto())
                        .puntos(cp.getPuntos())
                        .bonoBs(cp.getBonoBs())
                        .bonoBsTienda(cp.getBonoBsTienda())
                        .ticketsDigitales(cp.getTicketsDigitales())
                        .cuponesActivados(cp.isCuponesActivados())
                        .puntosTienda(cp.getPuntosTienda())
                        .ticketsDigitalesTienda(cp.getTicketsDigitalesTienda())
                        .cuponesFisicosTienda(cp.isCuponesFisicosTienda())
                        .activo(true)
                        .build();
                campanaProductoRepository.save(nuevoCp);
            }

            auditoriaService.log(getUser(), "CAMPANA_DUPLICADA",
                    "Campaña duplicada desde ID " + id + " (" + original.getNombre() + ") hacia nueva campaña ID " + savedCampana.getId() + " (" + savedCampana.getNombre() + ") con " + productosOriginales.size() + " productos",
                    "Campana", savedCampana.getId().toString());

            return ResponseEntity.ok(savedCampana);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/excluded-stores")
    @Operation(summary = "Obtener tiendas excluidas de una campaña")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getExcludedStores(@PathVariable Long id) {
        return campanaRepository.findById(id).map(campana -> 
            ResponseEntity.ok(campana.getTiendasExcluidas() != null ? campana.getTiendasExcluidas() : Set.of())
        ).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/excluded-stores/{tiendaId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Excluir tienda de una campaña")
    public ResponseEntity<?> addExcludedStore(@PathVariable Long id, @PathVariable Long tiendaId) {
        Campana campana = campanaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaña no encontrada con ID: " + id));
        Tienda tienda = tiendaRepository.findById(tiendaId)
                .orElseThrow(() -> new RuntimeException("Tienda no encontrada con ID: " + tiendaId));

        if (campana.getTiendasExcluidas() == null) {
            campana.setTiendasExcluidas(new HashSet<>());
        }
        campana.getTiendasExcluidas().add(tienda);
        campanaRepository.save(campana);

        auditoriaService.log(getUser(), "CAMPANA_TIENDA_EXCLUIDA",
                "Tienda " + tienda.getNombre() + " (ID: " + tiendaId + ") excluida de campaña " + campana.getNombre(),
                "Campana", id.toString());

        return ResponseEntity.ok(Map.of("message", "Tienda excluida exitosamente", "tiendasExcluidas", campana.getTiendasExcluidas()));
    }

    @DeleteMapping("/{id}/excluded-stores/{tiendaId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Remover tienda de exclusión en una campaña")
    public ResponseEntity<?> removeExcludedStore(@PathVariable Long id, @PathVariable Long tiendaId) {
        Campana campana = campanaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaña no encontrada con ID: " + id));

        if (campana.getTiendasExcluidas() != null) {
            campana.getTiendasExcluidas().removeIf(t -> t.getId().equals(tiendaId));
            campanaRepository.save(campana);
            auditoriaService.log(getUser(), "CAMPANA_TIENDA_INCLUIDA",
                    "Tienda con ID " + tiendaId + " removida de exclusión en campaña " + campana.getNombre(),
                    "Campana", id.toString());
        }

        return ResponseEntity.ok(Map.of("message", "Tienda removida de exclusión exitosamente"));
    }


    @GetMapping("/{id}/products")
    @Operation(summary = "Listar productos asignados a una campaña")
    public List<CampanaProducto> getProducts(@PathVariable Long id) {
        return campanaProductoRepository.findByCampanaIdAndActivoTrue(id);
    }

    @PostMapping("/{id}/products")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Añadir o actualizar producto en campaña")
    public ResponseEntity<CampanaProducto> addProduct(@PathVariable Long id, @RequestBody AddProductoRequest req) {
        Campana campana = campanaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campaña no encontrada"));
        Producto producto = productoRepository.findById(req.getProductoId())
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        CampanaProducto cp = campanaProductoRepository.findByCampanaIdAndProductoId(id, req.getProductoId())
                .orElse(CampanaProducto.builder()
                        .campana(campana)
                        .producto(producto)
                        .build());

        if (req.getPuntos() != null) cp.setPuntos(req.getPuntos());
        if (req.getBonoBs() != null) cp.setBonoBs(req.getBonoBs());
        if (req.getBonoBsTienda() != null) cp.setBonoBsTienda(req.getBonoBsTienda());
        if (req.getTicketsDigitales() != null) cp.setTicketsDigitales(req.getTicketsDigitales());
        if (req.getCuponesActivados() != null) cp.setCuponesActivados(req.getCuponesActivados());
        if (req.getPuntosTienda() != null) cp.setPuntosTienda(req.getPuntosTienda());
        if (req.getTicketsDigitalesTienda() != null) cp.setTicketsDigitalesTienda(req.getTicketsDigitalesTienda());
        if (req.getCuponesFisicosTienda() != null) cp.setCuponesFisicosTienda(req.getCuponesFisicosTienda());
        cp.setActivo(true);
        CampanaProducto saved = campanaProductoRepository.save(cp);
        auditoriaService.log(getUser(), "CAMPANA_PRODUCTO_ASIGNADO", 
            "Producto " + producto.getModelo() + " asignado a campaña " + campana.getNombre(), 
            "CampanaProducto", saved.getId().toString());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}/products/{productoId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Quitar producto de campaña")
    public ResponseEntity<Void> removeProduct(@PathVariable Long id, @PathVariable Long productoId) {
        campanaProductoRepository.findByCampanaIdAndProductoId(id, productoId).ifPresent(cp -> {
            cp.setActivo(false);
            campanaProductoRepository.save(cp);
            auditoriaService.log(getUser(), "CAMPANA_PRODUCTO_REMOVIDO", 
                "Producto con ID " + productoId + " removido de campaña con ID " + id, 
                "CampanaProducto", cp.getId().toString());
        });
        return ResponseEntity.noContent().build();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Ciclo de vida de campaña (§1 Informe Complementario)
    // ────────────────────────────────────────────────────────────────────────────

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Activar campaña (BORRADOR → ACTIVA)")
    public ResponseEntity<?> activate(@PathVariable Long id) {
        return campanaRepository.findById(id).map(c -> {
            c.setEstadoCampana(Campana.EstadoCampana.ACTIVA);
            c.setActivo(true);
            c.setRegistroHabilitado(true);
            Campana saved = campanaRepository.save(c);
            auditoriaService.log(getUser(), "CAMPANA_ACTIVADA", "Campaña activada: " + c.getNombre(), "Campana", id.toString());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/pause")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Pausar campaña (ACTIVA → PAUSADA) — suspensión temporal")
    public ResponseEntity<?> pause(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return campanaRepository.findById(id).map(c -> {
            if (c.getEstadoCampana() == Campana.EstadoCampana.CERRADA || c.getEstadoCampana() == Campana.EstadoCampana.ARCHIVADA) {
                return ResponseEntity.status(409).body(Map.of("error", "No se puede pausar una campaña en estado " + c.getEstadoCampana()));
            }
            c.setEstadoCampana(Campana.EstadoCampana.PAUSADA);
            c.setRegistroHabilitado(false);
            Campana saved = campanaRepository.save(c);
            String motivo = body != null ? body.getOrDefault("motivo", "") : "";
            auditoriaService.log(getUser(), "CAMPANA_PAUSADA", "Campaña pausada: " + c.getNombre() + (motivo.isBlank() ? "" : " | Motivo: " + motivo), "Campana", id.toString());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Cerrar campaña (→ CERRADA) — ya no acepta ventas pero conserva historial")
    public ResponseEntity<?> close(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return campanaRepository.findById(id).map(c -> {
            if (c.getEstadoCampana() == Campana.EstadoCampana.ARCHIVADA) {
                return ResponseEntity.status(409).body(Map.of("error", "No se puede cerrar una campaña ya archivada"));
            }
            c.setEstadoCampana(Campana.EstadoCampana.CERRADA);
            c.setActivo(false);
            c.setRegistroHabilitado(false);
            Campana saved = campanaRepository.save(c);
            String motivo = body != null ? body.getOrDefault("motivo", "") : "";
            auditoriaService.log(getUser(), "CAMPANA_CERRADA", "Campaña cerrada: " + c.getNombre() + (motivo.isBlank() ? "" : " | Motivo: " + motivo), "Campana", id.toString());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Archivar campaña (→ ARCHIVADA) — solo consulta histórica; bloquea si tiene ventas activas")
    public ResponseEntity<?> archive(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return campanaRepository.findById(id).map(c -> {
            boolean tieneVentas = ventaCampanaRepository.existsByCampanaId(id);
            if (tieneVentas && c.getEstadoCampana() != Campana.EstadoCampana.CERRADA) {
                return ResponseEntity.status(409).body(Map.of(
                    "error", "Debe cerrar la campaña antes de archivarla si tiene ventas registradas. Primero use el endpoint /close."
                ));
            }
            c.setEstadoCampana(Campana.EstadoCampana.ARCHIVADA);
            c.setActivo(false);
            c.setRegistroHabilitado(false);
            Campana saved = campanaRepository.save(c);
            String motivo = body != null ? body.getOrDefault("motivo", "") : "";
            auditoriaService.log(getUser(), "CAMPANA_ARCHIVADA", "Campaña archivada: " + c.getNombre() + (motivo.isBlank() ? "" : " | Motivo: " + motivo), "Campana", id.toString());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @Operation(summary = "Reactivar campaña pausada (PAUSADA → ACTIVA)")
    public ResponseEntity<?> reactivate(@PathVariable Long id) {
        return campanaRepository.findById(id).map(c -> {
            if (c.getEstadoCampana() != Campana.EstadoCampana.PAUSADA) {
                return ResponseEntity.status(409).body(Map.of("error", "Solo se puede reactivar una campaña en estado PAUSADA"));
            }
            c.setEstadoCampana(Campana.EstadoCampana.ACTIVA);
            c.setActivo(true);
            c.setRegistroHabilitado(true);
            Campana saved = campanaRepository.save(c);
            auditoriaService.log(getUser(), "CAMPANA_REACTIVADA", "Campaña reactivada: " + c.getNombre(), "Campana", id.toString());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    private String getUser() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @lombok.Data
    public static class AddProductoRequest {
        private Long productoId;
        private Integer puntos;
        private Integer bonoBs;
        private Integer bonoBsTienda;
        private Integer ticketsDigitales;
        private Boolean cuponesActivados;
        private Integer puntosTienda;
        private Integer ticketsDigitalesTienda;
        private Boolean cuponesFisicosTienda;
    }
}

