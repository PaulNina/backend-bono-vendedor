package com.ninabit.bono.modules.campaign;

import com.ninabit.bono.modules.product.Producto;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "campana_producto")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampanaProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "campana_id", nullable = false)
    private Campana campana;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    @Builder.Default
    private Integer puntos = 0;

    @Column(name = "bono_bs", nullable = false)
    @Builder.Default
    private Integer bonoBs = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}
