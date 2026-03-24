package com.ninabit.bono.modules.sale;

import com.ninabit.bono.modules.campaign.Campana;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "venta_campana")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentaCampana {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Venta venta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "campana_id", nullable = false)
    private Campana campana;

    @Column(name = "puntos_ganados", nullable = false)
    private Integer puntosGanados;

    @Column(name = "bono_ganado", nullable = false)
    private Integer bonoGanado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pago_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private com.ninabit.bono.modules.payment.Pago pago;
}
