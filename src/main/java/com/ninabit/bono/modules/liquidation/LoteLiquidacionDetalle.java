package com.ninabit.bono.modules.liquidation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "lotes_liquidacion_detalle")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoteLiquidacionDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private LoteLiquidacion lote;

    @Column(name = "venta_campana_id")
    private Long ventaCampanaId;

    @Column(name = "venta_id", nullable = false)
    private Long ventaId;

    @Column(name = "serial", length = 100)
    private String serial;

    @Column(name = "product_model", length = 100)
    private String productModel;

    @Column(name = "vendedor_id")
    private Long vendedorId;

    @Column(name = "vendedor_nombre")
    private String vendedorNombre;

    @Column(name = "vendedor_ci", length = 50)
    private String vendedorCi;

    @Column(name = "tienda_id")
    private Long tiendaId;

    @Column(name = "tienda_nombre")
    private String tiendaNombre;

    @Column(name = "ciudad", length = 100)
    private String ciudad;

    @Column(name = "tipo_beneficiario", length = 30)
    private String tipoBeneficiario; // VENDEDOR or TIENDA

    @Column(name = "monto_bruto", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal montoBruto = BigDecimal.ZERO;

    @Column(name = "deduccion_incidencias", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal deduccionIncidencias = BigDecimal.ZERO;

    @Column(name = "monto_neto", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal montoNeto = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private EstadoDetalle estado = EstadoDetalle.PENDIENTE;

    @Column(name = "comprobante_individual_url")
    private String comprobanteIndividualUrl;

    @Column(name = "incidencia_regularizacion_id")
    private Long incidenciaRegularizacionId;

    /** Motivo si el detalle está en estado OBSERVADO (§5 Informe Complementario) */
    @Column(name = "motivo_observacion", columnDefinition = "TEXT")
    private String motivoObservacion;

    public enum EstadoDetalle {
        PENDIENTE, OBSERVADO, PAGADO, EXCLUIDO
    }
}
