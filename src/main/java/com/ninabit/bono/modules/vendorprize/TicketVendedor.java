package com.ninabit.bono.modules.vendorprize;

import com.ninabit.bono.modules.campaign.Campana;
import com.ninabit.bono.modules.sale.Venta;
import com.ninabit.bono.modules.vendor.Vendedor;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tickets_vendedor")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketVendedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "campana_id", nullable = false)
    private Campana campana;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendedor_id", nullable = false)
    private Vendedor vendedor;

    @Column(name = "numero_ticket", nullable = false, unique = true, length = 50)
    private String numeroTicket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EstadoTicket estado = EstadoTicket.HABILITADO;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public enum EstadoTicket {
        HABILITADO, PREMIADO, ANULADO
    }
}
