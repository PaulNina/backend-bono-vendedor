package com.ninabit.bono.modules.payment;

import com.ninabit.bono.modules.vendor.Vendedor;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "pagos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendedor_id", nullable = false)
    private Vendedor vendedor;

    @Column(name = "monto_total", nullable = false)
    private Integer montoTotal;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "foto_comprobante")
    private String fotoComprobante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Estado estado = Estado.PENDIENTE;

    public enum Estado {
        PENDIENTE, PAGADO
    }
}
