package com.ninabit.bono.modules.liquidation.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class PreviewLoteRequest {
    private Long campanaId;
    private String ciudad;
    private String tipoBeneficiario; // VENDEDOR, TIENDA, MIXTO
    private LocalDate periodoInicio;
    private LocalDate periodoFin;
}
