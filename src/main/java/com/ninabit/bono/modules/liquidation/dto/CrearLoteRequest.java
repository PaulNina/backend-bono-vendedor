package com.ninabit.bono.modules.liquidation.dto;

import com.ninabit.bono.modules.liquidation.LoteLiquidacion;
import lombok.Data;
import java.time.LocalDate;

@Data
public class CrearLoteRequest {
    private Long campanaId;
    private String ciudad;
    private String tipoBeneficiario; // VENDEDOR, TIENDA, MIXTO
    private LocalDate periodoInicio;
    private LocalDate periodoFin;
    private String observaciones;
    private LoteLiquidacion.EstadoLote estado; // BORRADOR or EN_REVISION
}
