package com.ninabit.bono.modules.clientcampaign.dto;

import com.ninabit.bono.modules.clientcampaign.RegistroCliente;
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
public class RegistroClienteDto {
    private Long id;
    private Long campanaClienteId;
    private String campanaClienteNombre;
    private String canalRegistro;
    private Long impulsadorId;
    private String impulsadorNombre;
    private String nombreCliente;
    private String apellidoCliente;
    private String ci;
    private String celular;
    private String email;
    private String ciudad;
    private String puntoVenta;
    private Long productoId;
    private String productoModelo;
    private Integer productoPulgadas;
    private String serial;
    private LocalDate fechaCompra;
    private String fotoTag;
    private String fotoPoliza;
    private String fotoNota;
    private String fotoCiAnverso;
    private String fotoCiReverso;
    private String estado;
    private Long supervisorId;
    private String supervisorNombre;
    private LocalDateTime fechaRevision;
    private String motivoObservacion;
    private String historialRevisiones;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RegistroClienteDto fromEntity(RegistroCliente r) {
        if (r == null) return null;
        return RegistroClienteDto.builder()
                .id(r.getId())
                .campanaClienteId(r.getCampanaCliente() != null ? r.getCampanaCliente().getId() : null)
                .campanaClienteNombre(r.getCampanaCliente() != null ? r.getCampanaCliente().getNombre() : null)
                .canalRegistro(r.getCanalRegistro() != null ? r.getCanalRegistro().name() : null)
                .impulsadorId(r.getImpulsadorId())
                .impulsadorNombre(r.getImpulsadorNombre())
                .nombreCliente(r.getNombreCliente())
                .apellidoCliente(r.getApellidoCliente())
                .ci(r.getCi())
                .celular(r.getCelular())
                .email(r.getEmail())
                .ciudad(r.getCiudad())
                .puntoVenta(r.getPuntoVenta())
                .productoId(r.getProductoId())
                .productoModelo(r.getProductoModelo())
                .productoPulgadas(r.getProductoPulgadas())
                .serial(r.getSerial())
                .fechaCompra(r.getFechaCompra())
                .fotoTag(r.getFotoTag())
                .fotoPoliza(r.getFotoPoliza())
                .fotoNota(r.getFotoNota())
                .fotoCiAnverso(r.getFotoCiAnverso())
                .fotoCiReverso(r.getFotoCiReverso())
                .estado(r.getEstado() != null ? r.getEstado().name() : null)
                .supervisorId(r.getSupervisorId())
                .supervisorNombre(r.getSupervisorNombre())
                .fechaRevision(r.getFechaRevision())
                .motivoObservacion(r.getMotivoObservacion())
                .historialRevisiones(r.getHistorialRevisiones())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
