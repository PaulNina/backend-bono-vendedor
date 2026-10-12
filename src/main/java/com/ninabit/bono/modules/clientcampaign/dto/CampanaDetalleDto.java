package com.ninabit.bono.modules.clientcampaign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampanaDetalleDto {
    private CampanaClienteDto campana;
    private List<CampanaClienteProductoDto> productos;
    private List<PremioRuletaDto> premiosRuleta;
}
