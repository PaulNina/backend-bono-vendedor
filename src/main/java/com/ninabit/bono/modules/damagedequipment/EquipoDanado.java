package com.ninabit.bono.modules.damagedequipment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ninabit.bono.modules.product.Producto;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "equipo_danado")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipoDanado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "registro_cliente_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnoreProperties({"equipos", "hibernateLazyInitializer"})
    private RegistroClienteDanado registroCliente;

    @Column(name = "serial_caja", length = 100)
    private String serialCaja;

    @Column(name = "serial_tv", length = 100)
    private String serialTv;

    @Column(name = "seriales_coinciden", nullable = false)
    @Builder.Default
    private boolean serialesCoinciden = true;

    @Column(name = "serial_ingreso_manual", nullable = false)
    @Builder.Default
    private boolean serialIngresoManual = false;

    @Column(name = "serial_validado", length = 100, nullable = false)
    private String serialValidado;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Producto producto;

    @Column(length = 100)
    private String modelo;

    @Column(name = "pulgadas")
    private Integer pulgadas;

    @Column(name = "tipo_falla", nullable = false, length = 150)
    private String tipoFalla;

    @Column(columnDefinition = "TEXT")
    private String comentarios;

    @Column(name = "foto_tag_caja")
    private String fotoTagCaja;

    @Column(name = "foto_tag_tv")
    private String fotoTagTv;

    @Column(name = "foto_falla_1")
    private String fotoFalla1;

    @Column(name = "foto_falla_2")
    private String fotoFalla2;

    @Column(name = "foto_falla_3")
    private String fotoFalla3;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PostLoad
    public void postLoad() {
        if (!this.serialesCoinciden && this.serialCaja != null && this.serialTv != null) {
            String normCaja = this.serialCaja.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
            String normTv = this.serialTv.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
            if (!normCaja.isEmpty() && !normTv.isEmpty() && normCaja.equals(normTv)) {
                this.serialesCoinciden = true;
            }
        }
    }
}
