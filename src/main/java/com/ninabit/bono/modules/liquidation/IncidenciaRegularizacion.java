package com.ninabit.bono.modules.liquidation;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "incidencias_regularizacion")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidenciaRegularizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TipoIncidencia tipo;

    @Column(name = "venta_id")
    private Long ventaId;

    @Column(name = "serial_afectado", length = 100)
    private String serialAfectado;

    @Column(name = "pago_id")
    private Long pagoId;

    @Column(name = "vendedor_id")
    private Long vendedorId;

    @Column(name = "vendedor_nombre")
    private String vendedorNombre;

    @Column(name = "tienda_id")
    private Long tiendaId;

    @Column(name = "tienda_nombre")
    private String tiendaNombre;

    @Column(name = "monto_a_compensar", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoACompensar;

    @Column(name = "saldo_pendiente", nullable = false, precision = 12, scale = 2)
    private BigDecimal saldoPendiente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private EstadoIncidencia estado = EstadoIncidencia.PENDIENTE;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @Column(name = "lote_compensador_id")
    private Long loteCompensadorId;

    @Column(name = "fecha_compensacion")
    private LocalDateTime fechaCompensacion;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @PrePersist
    public void prePersist() {
        if (this.fechaRegistro == null) {
            this.fechaRegistro = LocalDateTime.now();
        }
        if (this.saldoPendiente == null) {
            this.saldoPendiente = this.montoACompensar;
        }
    }

    public enum TipoIncidencia {
        VENTA_PAGADA_ANULADA, AJUSTE_MANUAL, DEVOLUCION_PRODUCTO
    }

    public enum EstadoIncidencia {
        PENDIENTE, COMPENSADA, CANCELADA
    }
}
