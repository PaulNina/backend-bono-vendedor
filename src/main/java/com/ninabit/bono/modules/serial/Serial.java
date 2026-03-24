package com.ninabit.bono.modules.serial;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "serial")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Serial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_serie", nullable = false, unique = true)
    private String numeroSerie;

    @Column(name = "producto_id")
    private Long productoId;

    @Column(name = "registro_comprador_id")
    private Long registroCompradorId;

    @Column(name = "registro_vendedor_id")
    private Long registroVendedorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @org.hibernate.annotations.NotFound(action = org.hibernate.annotations.NotFoundAction.IGNORE)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private com.ninabit.bono.modules.product.Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registro_vendedor_id", insertable = false, updatable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @org.hibernate.annotations.NotFound(action = org.hibernate.annotations.NotFoundAction.IGNORE)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private com.ninabit.bono.modules.sale.Venta registroVendedor;

    @Column(name = "fecha_registro_comprador")
    private LocalDateTime fechaRegistroComprador;

    @Column(name = "fecha_registro_vendedor")
    private LocalDateTime fechaRegistroVendedor;

    @Column(nullable = false)
    @Builder.Default
    private boolean bloqueado = false;

    @Column(name = "motivo_bloqueo")
    private String motivoBloqueo;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(length = 50)
    private String container;

    @Column(length = 20)
    private String seal;

    @Column(name = "hoja_registro", length = 20)
    private String hojaRegistro;

    @Column(length = 30)
    private String invoice;

    @Column(name = "date_invoice")
    private LocalDateTime dateInvoice;

    // ---- Campos legacy mantenidos para compatibilidad con VentaController ----
    @Transient
    private String serial; // se infiere de numeroSerie

    @Column(nullable = true)
    private String modelo;

    @Column(name = "producto_nombre")
    private String productoNombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    @Builder.Default
    private Estado estado = Estado.DISPONIBLE;

    public enum Estado {
        DISPONIBLE, USADO, BLOQUEADO
    }

    /** Helper: devuelve el numero de serie como "serial" para compatibilidad */
    public String getSerial() {
        return numeroSerie;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
