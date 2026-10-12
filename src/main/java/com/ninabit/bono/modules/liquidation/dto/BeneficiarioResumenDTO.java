package com.ninabit.bono.modules.liquidation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BeneficiarioResumenDTO {
    private Long beneficiarioId;
    private String beneficiarioNombre;
    private String tipoBeneficiario; // VENDEDOR, TIENDA
    private String ciudad;
    private String tiendaNombre;
    private int cantidadVentas;
    private BigDecimal montoBruto;
    private BigDecimal deduccionIncidencias;
    private BigDecimal montoNeto;
    private String fotoQr;
}
