package com.ninabit.bono.modules.product.type;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/product-types")
@RequiredArgsConstructor
@Tag(name = "Tipos de Producto")
public class TipoProductoController {

    private final TipoProductoRepository tipoProductoRepository;

    @GetMapping
    @Operation(summary = "Listar todos los tipos de producto")
    public List<TipoProducto> getAll() {
        return tipoProductoRepository.findAll();
    }
}
