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

    @Transient
    private String storeName;

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

    private String ciudad;

    @Column(name = "foto_tag")
    private String fotoTag;

    @Column(name = "foto_poliza")
    private String fotoPoliza;

    @Column(name = "foto_nota")
    private String fotoNota;

    @Column(name = "motivo_rechazo")
    private String motivoRechazo;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.util.List<VentaCampana> detallesCampanas = new java.util.ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public enum Estado {
        PENDIENTE, APROBADA, RECHAZADA, CERRADA
    }
}
