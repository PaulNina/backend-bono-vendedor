package com.ninabit.bono.modules.payment;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CommissionReportDTO {
    private Long vendedorId;
    private String vendedorNombre;
    private String ciudad;
    private String tienda;
    private String fotoQr;
    private String tallaPolera;
    private Long cantidadVentas;
    private Long montoTotal;
    private String estado;

    public CommissionReportDTO(Long vendedorId, String vendedorNombre, String ciudad,
            String tienda, String fotoQr, String tallaPolera,
            Long cantidadVentas, Long montoTotal, String estado) {
        this.vendedorId = vendedorId;
        this.vendedorNombre = vendedorNombre;
        this.ciudad = ciudad;
        this.tienda = tienda;
        this.fotoQr = fotoQr;
        this.tallaPolera = tallaPolera;
        this.cantidadVentas = cantidadVentas;
        this.montoTotal = montoTotal;
        this.estado = estado;
    }
}
