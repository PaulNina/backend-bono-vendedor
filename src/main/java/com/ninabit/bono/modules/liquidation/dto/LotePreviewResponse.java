package com.ninabit.bono.modules.liquidation.dto;

import com.ninabit.bono.modules.liquidation.LoteLiquidacionDetalle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LotePreviewResponse {
    private int totalVentas;
    private int totalBeneficiarios;
    private BigDecimal totalMontoBrutoBs;
    private BigDecimal totalDeduccionesBs;
    private BigDecimal totalMontoNetoBs;
    @Builder.Default
    private List<BeneficiarioResumenDTO> beneficiarios = new ArrayList<>();
    @Builder.Default
    private List<LoteLiquidacionDetalle> detalles = new ArrayList<>();
}
