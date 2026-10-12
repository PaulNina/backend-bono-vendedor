package com.ninabit.bono.modules.clientcampaign.dto;

import com.ninabit.bono.modules.clientcampaign.TicketCliente;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketClienteDto {
    private Long id;
    private String codigoTicket;
    private Long campanaClienteId;
    private String campanaClienteNombre;
    private String clienteCi;
    private String clienteNombre;
    private String clienteCelular;
    private String serial;
    private String productoModelo;
    private String estado;
    private boolean excluidoQuincenal;
    private LocalDateTime createdAt;
    private CampanaSimpleDto campanaCliente;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampanaSimpleDto {
        private Long id;
        private String nombre;
    }

    public static TicketClienteDto fromEntity(TicketCliente t) {
        if (t == null) return null;
        CampanaSimpleDto campanaSimple = t.getCampanaCliente() != null ?
                CampanaSimpleDto.builder()
                        .id(t.getCampanaCliente().getId())
                        .nombre(t.getCampanaCliente().getNombre())
                        .build() : null;

        return TicketClienteDto.builder()
                .id(t.getId())
                .codigoTicket(t.getCodigoTicket())
                .campanaClienteId(t.getCampanaCliente() != null ? t.getCampanaCliente().getId() : null)
                .campanaClienteNombre(t.getCampanaCliente() != null ? t.getCampanaCliente().getNombre() : null)
                .campanaCliente(campanaSimple)
                .clienteCi(t.getClienteCi())
                .clienteNombre(t.getClienteNombre())
                .clienteCelular(t.getClienteCelular())
                .serial(t.getSerial())
                .productoModelo(t.getProductoModelo())
                .estado(t.getEstado() != null ? t.getEstado().name() : null)
                .excluidoQuincenal(t.isExcluidoQuincenal())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
