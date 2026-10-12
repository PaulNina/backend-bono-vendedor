package com.ninabit.bono.modules.sale;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ventas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vendedor_id", nullable = false)
    private Long vendedorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id", insertable = false, updatable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private com.ninabit.bono.modules.vendor.Vendedor vendedor;

    @Column(name = "producto_id")
    private Long productoId;

    @Column(name = "vendor_name")
    private String vendorName;

    @Column(name = "tienda_id")
    private Long tiendaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tienda_id", insertable = false, updatable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private com.ninabit.bono.modules.tienda.Tienda tienda;

    @Column(name = "store_name")
    private String storeName;

    @Column(name = "store_code", length = 50)
    private String storeCode;

    @Column(name = "store_city", length = 100)
    private String storeCity;

    @Column(name = "store_department", length = 100)
    private String storeDepartment;

    @Column(name = "store_channel", length = 50)
    private String storeChannel;

    @Transient
    private String vendorPhone;

    @Transient
    private String vendorEmail;

    @Transient
    private String vendorCi;

    @Column(name = "product_model")
    private String productModel;

    @Column(name = "product_size")
    private String productSize;

    @Column(name = "product_type")
    private String productType;

    @Column(nullable = false)
    private String serial;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Estado estado = Estado.PENDIENTE;

    @Column(nullable = false)
    @Builder.Default
    private Integer puntos = 0;

    @Column(name = "bono_bs", nullable = false)
    @Builder.Default
    private Integer bonoBs = 0;

    @Column(name = "bono_bs_tienda")
    @Builder.Default
    private Integer bonoBsTienda = 0;

    @Column(name = "puntos_tienda")
    @Builder.Default
    private Integer puntosTienda = 0;

    private String ciudad;

    @Column(name = "foto_tag")
    private String fotoTag;

    @Column(name = "foto_poliza")
    private String fotoPoliza;

    @Column(name = "foto_nota")
    private String fotoNota;

    @Column(name = "motivo_rechazo")
    private String motivoRechazo;

    @Column(name = "historial_rechazos", columnDefinition = "TEXT")
    private String historialRechazos;

    @Column(name = "motivo_observacion", columnDefinition = "TEXT")
    private String motivoObservacion;

    @Column(name = "archivos_observados", length = 150)
    private String archivosObservados;

    @Column(name = "evidencias_anteriores", columnDefinition = "TEXT")
    private String evidenciasAnteriores;

    @Column(name = "fecha_observacion")
    private LocalDateTime fechaObservacion;

    @Column(name = "fecha_subsanacion")
    private LocalDateTime fechaSubsanacion;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.util.List<VentaCampana> detallesCampanas = new java.util.ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.bonoBsTienda == null) {
            this.bonoBsTienda = 0;
        }
        if (this.puntosTienda == null) {
            this.puntosTienda = 0;
        }
    }

    public Integer getBonoBsTienda() {
        return bonoBsTienda != null ? bonoBsTienda : 0;
    }

    public Integer getPuntosTienda() {
        return puntosTienda != null ? puntosTienda : 0;
    }

    public enum Estado {
        REGISTRADA, PENDIENTE, OBSERVADA, APROBADA, RECHAZADA, ANULADA, CERRADA
    }
}
