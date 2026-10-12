package com.ninabit.bono.modules.clientcampaign.dto;

import com.ninabit.bono.modules.clientcampaign.SorteoGanador;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SorteoGanadorDto {
    private Long id;
    private Long sorteoPeriodoId;
    private String sorteoPeriodoNombre;
    private Long ticketClienteId;
    private String ticketCodigo;
    private Long ticketVendedorId;
    private String tipoParticipante;
    private String clienteCi;
    private String clienteNombre;
    private String clienteCelular;
    private String serial;
    private String productoModelo;
    private String premio;
    private Integer orden;
    private String evidenciaActaUrl;
    private LocalDateTime createdAt;

    private TicketSimpleDto ticketCliente;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketSimpleDto {
        private String codigoTicket;
    }

    public static SorteoGanadorDto fromEntity(SorteoGanador g) {
        if (g == null) return null;
        String cod = g.getTicketCliente() != null ? g.getTicketCliente().getCodigoTicket() : 
                (g.getTicketVendedor() != null ? g.getTicketVendedor().getNumeroTicket() : null);

        return SorteoGanadorDto.builder()
                .id(g.getId())
                .sorteoPeriodoId(g.getSorteoPeriodo() != null ? g.getSorteoPeriodo().getId() : null)
                .sorteoPeriodoNombre(g.getSorteoPeriodo() != null ? g.getSorteoPeriodo().getNombre() : null)
                .ticketClienteId(g.getTicketCliente() != null ? g.getTicketCliente().getId() : null)
                .ticketCodigo(cod)
                .ticketCliente(cod != null ? TicketSimpleDto.builder().codigoTicket(cod).build() : null)
                .ticketVendedorId(g.getTicketVendedor() != null ? g.getTicketVendedor().getId() : null)
                .tipoParticipante(g.getTipoParticipante())
                .clienteCi(g.getClienteCi())
                .clienteNombre(g.getClienteNombre())
                .clienteCelular(g.getClienteCelular())
                .serial(g.getSerial())
                .productoModelo(g.getProductoModelo())
                .premio(g.getPremio())
                .orden(g.getOrden())
                .evidenciaActaUrl(g.getEvidenciaActaUrl())
                .createdAt(g.getCreatedAt())
                .build();
    }
}
