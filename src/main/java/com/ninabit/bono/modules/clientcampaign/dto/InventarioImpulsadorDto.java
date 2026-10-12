package com.ninabit.bono.modules.clientcampaign.dto;

import com.ninabit.bono.modules.clientcampaign.InventarioImpulsador;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventarioImpulsadorDto {
    private Long id;
    private Long campanaClienteId;
    private PremioRuletaDto premioRuleta;
    private Long usuarioId;
    private Long vendedorId;
    private String vendedorNombre;
    private String nombreDestinatario;
    private String tipoDestinatario;
    private Long tiendaId;
    private String tiendaNombre;
    private Integer cantidadAsignada;
    private Integer cantidadEntregada;
    private Integer cantidadDevuelta;
    private Integer saldo;
    private String estadoConciliacion;
    private String observaciones;
    private LocalDateTime fechaConciliacion;
    private LocalDateTime updatedAt;

    public static InventarioImpulsadorDto fromEntity(InventarioImpulsador inv) {
        if (inv == null) return null;
        return InventarioImpulsadorDto.builder()
                .id(inv.getId())
                .campanaClienteId(inv.getCampanaCliente() != null ? inv.getCampanaCliente().getId() : null)
                .premioRuleta(inv.getPremioRuleta() != null ? PremioRuletaDto.fromEntity(inv.getPremioRuleta()) : null)
                .usuarioId(inv.getUsuarioId())
                .vendedorId(inv.getVendedorId())
                .vendedorNombre(inv.getVendedorNombre())
                .nombreDestinatario(inv.getNombreDestinatario())
                .tipoDestinatario(inv.getTipoDestinatario())
                .tiendaId(inv.getTiendaId())
                .tiendaNombre(inv.getTiendaNombre())
                .cantidadAsignada(inv.getCantidadAsignada())
                .cantidadEntregada(inv.getCantidadEntregada())
                .cantidadDevuelta(inv.getCantidadDevuelta())
                .saldo(inv.getSaldo())
                .estadoConciliacion(inv.getEstadoConciliacion())
                .observaciones(inv.getObservaciones())
                .fechaConciliacion(inv.getFechaConciliacion())
                .updatedAt(inv.getUpdatedAt())
                .build();
    }
}
