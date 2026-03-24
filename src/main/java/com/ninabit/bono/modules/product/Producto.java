package com.ninabit.bono.modules.product;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import com.ninabit.bono.modules.product.type.TipoProducto;

@Entity
@Table(name = "producto")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_producto_id")
    private TipoProducto tipoProducto;

    @Column(nullable = false)
    private String nombre;

    @Column(length = 100)
    private String modelo;

    @Column(name = "tamano_pulgadas")
    private Integer tamanoPulgadas;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    // ---- Campos legacy mantenidos para compatibilidad con VentaController ----
    @Column(name = "modelo_codigo")
    private String modeloCodigo;

    @Column(nullable = true)
    private Integer pulgadas;

    @PrePersist
    public void prePersist() {
        if (fechaCreacion == null)
            fechaCreacion = LocalDateTime.now();
        // Sincronizar campo legacy con el nuevo
        if (modeloCodigo == null && modelo != null)
            modeloCodigo = modelo;
        if (pulgadas == null && tamanoPulgadas != null)
            pulgadas = tamanoPulgadas;
    }

    @PreUpdate
    public void preUpdate() {
        if (modeloCodigo == null && modelo != null)
            modeloCodigo = modelo;
        if (pulgadas == null && tamanoPulgadas != null)
            pulgadas = tamanoPulgadas;
    }
}
