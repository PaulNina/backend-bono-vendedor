package com.ninabit.bono.modules.serial;

import lombok.Data;

@Data
public class SerialUpdateDto {
    private String numeroSerie;
    private String modelo;
    private Long productoId;
    private String productoNombre;
    private String container;
    private String seal;
    private String hojaRegistro;
    private String invoice;
    private String dateInvoice; // yyyy-MM-dd or yyyy-MM-ddTHH:mm:ss
    private String estado;
    private Boolean bloqueado;
    private String motivoBloqueo;
}
