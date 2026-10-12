package com.ninabit.bono.modules.clientcampaign.dto;

import com.ninabit.bono.modules.clientcampaign.EntregaRuleta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntregaRuletaDto {
    private Long id;
    private Long registroClienteId;
    private Long campanaClienteId;
    private PremioRuletaDto premioRuleta;
    private Long impulsadorId;
    private String impulsadorNombre;
    private LocalDateTime fechaGiro;
    private String fotoEntrega;
    private boolean revertido;
    private String motivoReversion;
    private String revertidoPor;
    private LocalDateTime fechaReversion;

    public static EntregaRuletaDto fromEntity(EntregaRuleta e) {
        if (e == null) return null;
        return EntregaRuletaDto.builder()
                .id(e.getId())
                .registroClienteId(e.getRegistroCliente() != null ? e.getRegistroCliente().getId() : null)
                .campanaClienteId(e.getCampanaCliente() != null ? e.getCampanaCliente().getId() : null)
                .premioRuleta(e.getPremioRuleta() != null ? PremioRuletaDto.fromEntity(e.getPremioRuleta()) : null)
                .impulsadorId(e.getImpulsadorId())
                .impulsadorNombre(e.getImpulsadorNombre())
                .fechaGiro(e.getFechaGiro())
                .fotoEntrega(e.getFotoEntrega())
                .revertido(e.isRevertido())
                .motivoReversion(e.getMotivoReversion())
                .revertidoPor(e.getRevertidoPor())
                .fechaReversion(e.getFechaReversion())
                .build();
    }
}
