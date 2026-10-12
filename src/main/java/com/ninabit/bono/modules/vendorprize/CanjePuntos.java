package com.ninabit.bono.modules.vendorprize;

import com.ninabit.bono.modules.vendor.Vendedor;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "canjes_puntos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CanjePuntos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendedor_id", nullable = false)
    private Vendedor vendedor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "premio_id", nullable = false)
    private PremioVendedor premio;

    @Column(name = "puntos_consumidos", nullable = false)
    private Integer puntosConsumidos;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EstadoCanje estado = EstadoCanje.PENDIENTE;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    @Column(name = "usuario_resolucion")
    private String usuarioResolucion;

    @Column(name = "motivo_rechazo")
    private String motivoRechazo;

    @Column(name = "foto_comprobante")
    private String fotoComprobante;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @PrePersist
    public void prePersist() {
        if (this.fechaSolicitud == null) {
            this.fechaSolicitud = LocalDateTime.now();
        }
    }

    public enum EstadoCanje {
        PENDIENTE, APROBADO, RECHAZADO, ENTREGADO
    }
}
