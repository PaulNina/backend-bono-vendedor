package com.ninabit.bono.modules.product;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = "Productos")
public class ProductoController {

    private final ProductoRepository productoRepository;
    private final com.ninabit.bono.modules.audit.AuditoriaService auditoriaService;
    private final com.ninabit.bono.modules.campaign.CampanaProductoRepository campanaProductoRepository;

    @GetMapping
    @Operation(summary = "Listar todos los productos con filtros")
    public List<java.util.Map<String, Object>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long tipoProductoId) {
        
        org.springframework.data.jpa.domain.Specification<Producto> spec = ProductoSpecification.filterBy(search, tipoProductoId);
        List<Producto> productos = productoRepository.findAll(spec);

        return productos.stream().map(p -> {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", p.getId());
            map.put("nombre", p.getNombre());
            map.put("modelo", p.getModelo());
            map.put("tamanoPulgadas", p.getTamanoPulgadas());
            map.put("descripcion", p.getDescripcion());
            map.put("activo", p.isActivo());
            map.put("tipoProducto", p.getTipoProducto());
            map.put("modeloCodigo", p.getModeloCodigo());
            map.put("pulgadas", p.getPulgadas());
            map.put("fechaCreacion", p.getFechaCreacion());

            // Buscar campañas activas
            List<String> campanasActivas = campanaProductoRepository.findByProductoIdAndActivoTrue(p.getId())
                    .stream()
                    .map(cp -> cp.getCampana().getNombre())
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
            map.put("campanasActivas", campanasActivas);

            return map;
        }).collect(java.util.stream.Collectors.toList());
    }

    @GetMapping("/active")
    @Operation(summary = "Listar productos activos")
    public List<Producto> getActive() {
        return productoRepository.findByActivoTrue();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Producto> getById(@PathVariable Long id) {
        return productoRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear producto")
    public Producto create(@RequestBody Producto producto) {
        Producto saved = productoRepository.save(producto);
        auditoriaService.log(getUser(), "PRODUCTO_CREADO", 
            "Producto creado: " + saved.getModelo(), "Producto", saved.getId().toString());
        return saved;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar producto")
    public ResponseEntity<Producto> update(@PathVariable Long id, @RequestBody Producto producto) {
        if (!productoRepository.existsById(id))
            return ResponseEntity.notFound().build();
        producto.setId(id);
        Producto saved = productoRepository.save(producto);
        auditoriaService.log(getUser(), "PRODUCTO_ACTUALIZADO", 
            "Producto actualizado: " + saved.getModelo(), "Producto", id.toString());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar producto")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return productoRepository.findById(id).map(p -> {
            productoRepository.delete(p);
            auditoriaService.log(getUser(), "PRODUCTO_ELIMINADO", 
                "Producto eliminado: " + p.getModelo(), "Producto", id.toString());
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    private String getUser() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
