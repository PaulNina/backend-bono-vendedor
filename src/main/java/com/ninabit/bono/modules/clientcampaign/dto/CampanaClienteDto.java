package com.ninabit.bono.modules.clientcampaign.dto;

import com.ninabit.bono.modules.clientcampaign.CampanaCliente;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampanaClienteDto {
    private Long id;
    private String nombre;
    private String subtitulo;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private boolean activo;
    private boolean registroHabilitado;
    private boolean ruletaHabilitada;
    private boolean webHabilitada;
    private boolean chatbotHabilitado;
    private BigDecimal premioFinalMonto;
    private String premioFinalDescripcion;
    private String terminosCondiciones;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CampanaClienteDto fromEntity(CampanaCliente c) {
        if (c == null) return null;
        return CampanaClienteDto.builder()
                .id(c.getId())
                .nombre(c.getNombre())
                .subtitulo(c.getSubtitulo())
                .fechaInicio(c.getFechaInicio())
                .fechaFin(c.getFechaFin())
                .activo(c.isActivo())
                .registroHabilitado(c.isRegistroHabilitado())
                .ruletaHabilitada(c.isRuletaHabilitada())
                .webHabilitada(c.isWebHabilitada())
                .chatbotHabilitado(c.isChatbotHabilitado())
                .premioFinalMonto(c.getPremioFinalMonto())
                .premioFinalDescripcion(c.getPremioFinalDescripcion())
                .terminosCondiciones(c.getTerminosCondiciones())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
