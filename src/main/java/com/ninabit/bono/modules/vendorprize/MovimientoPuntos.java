package com.ninabit.bono.modules.vendorprize;

import com.ninabit.bono.modules.vendor.Vendedor;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "movimientos_puntos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientoPuntos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Vendedor vendedor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimiento tipo;

    @Column(nullable = false)
    private Integer puntos; // Positivo para abonos/reversiones, negativo para reservas

    @Column(name = "saldo_resultante", nullable = false)
    private Integer saldoResultante;

    @Column(name = "referencia_id")
    private Long referenciaId;

    private String descripcion;

    @Column(nullable = false)
    private LocalDateTime fecha;

    private String usuario;

    /**
     * ID de la campaña a la que pertenecen estos puntos (§6 Informe Complementario).
     * "Los puntos de campañas distintas no se mezclarán."
     */
    @Column(name = "campana_id")
    private Long campanaId;

    /**
     * Nombre de la campaña en snapshot para preservar histórico ante renombrados.
     */
    @Column(name = "campana_nombre", length = 200)
    private String campanaNombre;

    @PrePersist
    public void prePersist() {
        if (this.fecha == null) {
            this.fecha = LocalDateTime.now();
        }
    }

    public enum TipoMovimiento {
        ABONO_VENTA, RESERVA_CANJE, REVERSION_CANJE, AJUSTE_MANUAL
    }
}
