package com.ninabit.bono.modules.tienda;

import com.ninabit.bono.modules.sale.VentaRepository;
import com.ninabit.bono.modules.vendor.Vendedor;
import com.ninabit.bono.modules.vendor.VendedorRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/tiendas")
@RequiredArgsConstructor
@Tag(name = "Tiendas")
public class TiendaController {

    private final TiendaRepository tiendaRepository;
    private final com.ninabit.bono.modules.audit.AuditoriaService auditoriaService;
    private final VendedorRepository vendedorRepository;
    private final VentaRepository ventaRepository;
    private final com.ninabit.bono.modules.city.CiudadRepository ciudadRepository;

    @lombok.Data
    public static class TiendaRequest {
        private String nombre;
        private String nombrePropietario;
        private String direccion;
        private String telefono;
        private Long ciudadId;
        private String nit;
    }

    @GetMapping
    @Operation(summary = "Listar todas las tiendas")
    public List<Tienda> getAll(@RequestParam(required = false) String city) {
        if (city != null && !city.isBlank()) {
            return tiendaRepository.findByCiudadNombre(city);
        }
        return tiendaRepository.findAll();
    }

    @GetMapping("/active")
    @Operation(summary = "Listar tiendas activas")
    public List<Tienda> getActive() {
        return tiendaRepository.findByActivoTrue();
    }

    @GetMapping("/by-city/{ciudad}")
    @Operation(summary = "Tiendas activas de una ciudad (para registro de vendedor)")
    public List<Tienda> getActiveByCiudad(@PathVariable String ciudad) {
        return tiendaRepository.findByCiudadNombreAndActivoTrue(ciudad);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Tienda> getById(@PathVariable Long id) {
        return tiendaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear tienda")
    public ResponseEntity<Tienda> create(@RequestBody TiendaRequest body) {
        return ciudadRepository.findById(body.getCiudadId()).map(ciudad -> {
            Tienda tienda = Tienda.builder()
                    .nombre(body.getNombre())
                    .nombrePropietario(body.getNombrePropietario())
                    .direccion(body.getDireccion())
                    .telefono(body.getTelefono())
                    .ciudad(ciudad)
                    .nit(body.getNit())
                    .activo(true)
                    .build();
            Tienda saved = tiendaRepository.save(tienda);
            auditoriaService.log(getUser(), "TIENDA_CREADA",
                "Tienda creada: " + saved.getNombre(), "Tienda", saved.getId().toString());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.badRequest().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar tienda")
    public ResponseEntity<Tienda> update(@PathVariable Long id, @RequestBody TiendaRequest body) {
        return tiendaRepository.findById(id).map(t -> {
            t.setNombre(body.getNombre());
            t.setNombrePropietario(body.getNombrePropietario());
            t.setDireccion(body.getDireccion());
            t.setTelefono(body.getTelefono());
            if (body.getCiudadId() != null) {
                ciudadRepository.findById(body.getCiudadId()).ifPresent(t::setCiudad);
            }
            t.setNit(body.getNit());
            Tienda saved = tiendaRepository.save(t);
            auditoriaService.log(getUser(), "TIENDA_ACTUALIZADA",
                "Tienda actualizada: " + saved.getNombre(), "Tienda", id.toString());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activar/Desactivar tienda")
    public ResponseEntity<Tienda> toggle(@PathVariable Long id) {
        return tiendaRepository.findById(id).map(t -> {
            t.setActivo(!t.isActivo());
            Tienda saved = tiendaRepository.save(t);
            auditoriaService.log(getUser(), "TIENDA_TOGGLE_ACTIVO",
                "Tienda " + (saved.isActivo() ? "activada" : "desactivada") + ": " + saved.getNombre(),
                "Tienda", id.toString());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Estadísticas de ventas y bono de una tienda")
    public ResponseEntity<?> getStats(@PathVariable Long id) {
        return tiendaRepository.findById(id).map(tienda -> {
            List<Vendedor> vendedores = vendedorRepository.findAll().stream()
                    .filter(v -> v.getTienda() != null && v.getTienda().getId().equals(id))
                    .collect(Collectors.toList());

            List<Long> vendedorIds = vendedores.stream().map(Vendedor::getId).collect(Collectors.toList());

            int totalVentas = vendedorIds.stream()
                    .mapToInt(vid -> (int) ventaRepository.findByVendedorId(vid).stream()
                            .filter(vt -> vt.getEstado().name().equals("APROBADA")).count())
                    .sum();

            int totalBono = vendedorIds.stream()
                    .mapToInt(vid -> ventaRepository.findByVendedorId(vid).stream()
                            .filter(vt -> vt.getEstado().name().equals("APROBADA"))
                            .mapToInt(vt -> vt.getBonoBs()).sum())
                    .sum();

            java.util.HashMap<String, Object> result = new java.util.HashMap<>();
            result.put("tiendaId", id);
            result.put("tiendaNombre", tienda.getNombre());
            result.put("totalVendedores", vendedores.size());
            result.put("totalVentasAprobadas", totalVentas);
            result.put("totalBonoBs", totalBono);
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/assign-vendor/{vendedorId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Asignar un vendedor a esta tienda")
    public ResponseEntity<Vendedor> assignVendor(@PathVariable Long id, @PathVariable Long vendedorId) {
        Tienda tienda = tiendaRepository.findById(id).orElse(null);
        if (tienda == null)
            return ResponseEntity.notFound().build();
        return vendedorRepository.findById(vendedorId).map(v -> {
            v.setTienda(tienda);
            Vendedor saved = vendedorRepository.save(v);
            auditoriaService.log(getUser(), "TIENDA_VENDEDOR_ASIGNADO", 
                "Vendedor " + saved.getNombreCompleto() + " asignado a tienda " + tienda.getNombre(), 
                "Vendedor", vendedorId.toString());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    private String getUser() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
