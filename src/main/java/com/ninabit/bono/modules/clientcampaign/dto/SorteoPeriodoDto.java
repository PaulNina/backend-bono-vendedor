package com.ninabit.bono.modules.clientcampaign.dto;

import com.ninabit.bono.modules.clientcampaign.SorteoPeriodo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SorteoPeriodoDto {
    private Long id;
    private Long campanaClienteId;
    private String campanaClienteNombre;
    private Long campanaId;
    private String campanaNombre;
    private String tipoParticipante;
    private String tipoSorteo;
    private String nombre;
    private LocalDate fechaSorteo;
    private LocalDate fechaCorteRegistro;
    private String descripcionPremios;
    private boolean ejecutado;
    private LocalDateTime fechaEjecucion;
    private String usuarioEjecutor;
    private LocalDateTime createdAt;

    public static SorteoPeriodoDto fromEntity(SorteoPeriodo p) {
        if (p == null) return null;
        return SorteoPeriodoDto.builder()
                .id(p.getId())
                .campanaClienteId(p.getCampanaCliente() != null ? p.getCampanaCliente().getId() : null)
                .campanaClienteNombre(p.getCampanaCliente() != null ? p.getCampanaCliente().getNombre() : null)
                .campanaId(p.getCampana() != null ? p.getCampana().getId() : null)
                .campanaNombre(p.getCampana() != null ? p.getCampana().getNombre() : null)
                .tipoParticipante(p.getTipoParticipante() != null ? p.getTipoParticipante().name() : null)
                .tipoSorteo(p.getTipoSorteo() != null ? p.getTipoSorteo().name() : null)
                .nombre(p.getNombre())
                .fechaSorteo(p.getFechaSorteo())
                .fechaCorteRegistro(p.getFechaCorteRegistro())
                .descripcionPremios(p.getDescripcionPremios())
                .ejecutado(p.isEjecutado())
                .fechaEjecucion(p.getFechaEjecucion())
                .usuarioEjecutor(p.getUsuarioEjecutor())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
