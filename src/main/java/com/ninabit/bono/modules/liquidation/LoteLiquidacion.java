package com.ninabit.bono.modules.liquidation;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lotes_liquidacion")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoteLiquidacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_lote", unique = true, nullable = false, length = 50)
    private String codigoLote;

    @Column(name = "fecha_cierre", nullable = false)
    private LocalDate fechaCierre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private EstadoLote estado = EstadoLote.BORRADOR;

    @Column(name = "campana_id")
    private Long campanaId;

    @Column(name = "campana_nombre")
    private String campanaNombre;

    private String ciudad;

    @Column(name = "tipo_beneficiario", length = 30)
    @Builder.Default
    private String tipoBeneficiario = "VENDEDOR"; // VENDEDOR, TIENDA, MIXTO

    @Column(name = "periodo_inicio")
    private LocalDate periodoInicio;

    @Column(name = "periodo_fin")
    private LocalDate periodoFin;

    @Column(name = "total_monto_bruto_bs", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalMontoBrutoBs = BigDecimal.ZERO;

    @Column(name = "total_deducciones_bs", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalDeduccionesBs = BigDecimal.ZERO;

    @Column(name = "total_monto_neto_bs", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalMontoNetoBs = BigDecimal.ZERO;

    @Column(name = "total_ventas", nullable = false)
    @Builder.Default
    private Integer totalVentas = 0;

    @Column(name = "total_beneficiarios", nullable = false)
    @Builder.Default
    private Integer totalBeneficiarios = 0;

    @Column(name = "usuario_creador")
    private String usuarioCreador;

    @Column(name = "usuario_autorizador")
    private String usuarioAutorizador;

    @Column(name = "fecha_autorizacion")
    private LocalDateTime fechaAutorizacion;

    @Column(name = "usuario_pagador")
    private String usuarioPagador;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    @Column(name = "comprobante_pago_global_url")
    private String comprobantePagoGlobalUrl;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    /** Motivo de observación cuando el lote está en estado OBSERVADO (§5 Informe Complementario) */
    @Column(name = "motivo_observacion", columnDefinition = "TEXT")
    private String motivoObservacion;

    @Column(name = "usuario_observador")
    private String usuarioObservador;

    @Column(name = "fecha_observacion")
    private java.time.LocalDateTime fechaObservacion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "lote", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<LoteLiquidacionDetalle> detalles = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.fechaCierre == null) {
            this.fechaCierre = LocalDate.now();
        }
    }

    public enum EstadoLote {
        BORRADOR, EN_REVISION, AUTORIZADO, PAGADO, OBSERVADO, ANULADO
    }
}
