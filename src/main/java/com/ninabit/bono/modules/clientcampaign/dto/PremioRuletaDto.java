package com.ninabit.bono.modules.clientcampaign.dto;

import com.ninabit.bono.modules.clientcampaign.PremioRuleta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PremioRuletaDto {
    private Long id;
    private Long campanaClienteId;
    private String nombre;
    private String descripcion;
    private String imagenUrl;
    private String colorHex;
    private Integer probabilidadPeso;
    private boolean activo;
    private boolean esSinPremio;
    private Integer ordenRuleta;
    private LocalDateTime createdAt;

    public static PremioRuletaDto fromEntity(PremioRuleta p) {
        if (p == null) return null;
        return PremioRuletaDto.builder()
                .id(p.getId())
                .campanaClienteId(p.getCampanaCliente() != null ? p.getCampanaCliente().getId() : null)
                .nombre(p.getNombre())
                .descripcion(p.getDescripcion())
                .imagenUrl(p.getImagenUrl())
                .colorHex(p.getColorHex())
                .probabilidadPeso(p.getProbabilidadPeso())
                .activo(p.isActivo())
                .esSinPremio(p.isEsSinPremio())
                .ordenRuleta(p.getOrdenRuleta())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
