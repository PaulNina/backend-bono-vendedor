package com.ninabit.bono.modules.configuracion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
@Tag(name = "Configuración Pública")
public class PublicConfigController {

    private final ConfiguracionGlobalRepository configuracionGlobalRepository;

    @GetMapping("/public")
    @Operation(summary = "Configuración pública (sin autenticación)")
    public Map<String, String> getPublicConfig() {
        return configuracionGlobalRepository.findAll().stream()
                .filter(c -> c.getClave().startsWith("landing_") || 
                             c.getClave().equals("auto_aprobar_vendedores") || 
                             c.getClave().equals("venta_fecha_max_semanas"))
                .collect(Collectors.toMap(ConfiguracionGlobal::getClave, ConfiguracionGlobal::getValor));
    }
}
