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

    /** Puntos acreditados al vendedor por venta aprobada de este modelo */
    @Column(nullable = false)
    @Builder.Default
    private Integer puntos = 0;

    /** Bono en Bs acreditado al vendedor por venta aprobada */
    @Column(name = "bono_bs", nullable = false)
    @Builder.Default
    private Integer bonoBs = 0;

    /**
     * Bono en Bs acreditado a la TIENDA por venta aprobada (§4.1 Informe Complementario).
     * Independiente del bono del vendedor; puede ser cero.
     */
    @Column(name = "bono_bs_tienda", nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private Integer bonoBsTienda = 0;

    /**
     * Cantidad de tickets digitales de vendedor generados automáticamente al
     * aprobar la venta (§7 Informe Complementario). Cero si la campaña no usa tickets.
     */
    @Column(name = "tickets_digitales", nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private Integer ticketsDigitales = 0;

    /**
     * Indica si la campaña activa la generación de cupones físicos para este
     * modelo/campaña (§7 Informe Complementario).
     */
    @Column(name = "cupones_fisicos_activos", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean cuponesActivados = false;

    /** Puntos acreditados a la TIENDA por venta aprobada */
    @Column(name = "puntos_tienda", nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private Integer puntosTienda = 0;

    /**
     * Cantidad de tickets digitales de TIENDA generados automáticamente al
     * aprobar la venta. Cero si la campaña no usa tickets para tiendas.
     */
    @Column(name = "tickets_digitales_tienda", nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private Integer ticketsDigitalesTienda = 0;

    /**
     * Indica si la campaña activa la generación de cupones físicos para la TIENDA.
     */
    @Column(name = "cupones_fisicos_tienda", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean cuponesFisicosTienda = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}
