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
public class ConsultarTicketsResponseDto {
    private String ci;
    private int totalTickets;
    private long ticketsHabilitados;
    private List<RegistroClienteDto> registros;
    private List<TicketClienteDto> tickets;
}
