package com.ninabit.bono.modules.clientcampaign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ninabit.bono.modules.product.Producto;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "campana_cliente_productos", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"campana_cliente_id", "producto_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CampanaClienteProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "campana_cliente_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private CampanaCliente campanaCliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Producto producto;

    @Column(nullable = false)
    @Builder.Default
    private Integer tickets = 1;

    public Integer getMultiplicadorTickets() {
        return tickets;
    }

    public void setMultiplicadorTickets(Integer multiplicadorTickets) {
        this.tickets = multiplicadorTickets;
    }

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}
