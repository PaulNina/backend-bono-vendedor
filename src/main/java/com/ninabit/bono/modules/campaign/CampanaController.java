package com.ninabit.bono.modules.campaign;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.ninabit.bono.modules.product.Producto;
import com.ninabit.bono.modules.product.ProductoRepository;

import java.util.List;

@RestController
@RequestMapping("/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campañas")
public class CampanaController {

    private final CampanaRepository campanaRepository;
    private final CampanaProductoRepository campanaProductoRepository;
    private final ProductoRepository productoRepository;
    private final com.ninabit.bono.modules.audit.AuditoriaService auditoriaService;

    @GetMapping
    @Operation(summary = "Listar todas las campañas")
    public List<Campana> getAll() {
        return campanaRepository.findAll();
    }

    @GetMapping("/active")
    @Operation(summary = "Obtener campaña activa")
    public ResponseEntity<Campana> getActive() {
        return campanaRepository.findFirstByActivoTrue()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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

        cp.setPuntos(req.getPuntos());
        cp.setBonoBs(req.getBonoBs());
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

    private String getUser() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @lombok.Data
    public static class AddProductoRequest {
        private Long productoId;
        private Integer puntos;
        private Integer bonoBs;
    }
}
