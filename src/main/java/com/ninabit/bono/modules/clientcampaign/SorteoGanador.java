package com.ninabit.bono.modules.clientcampaign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sorteos_ganadores")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SorteoGanador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sorteo_periodo_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private SorteoPeriodo sorteoPeriodo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ticket_cliente_id", nullable = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private TicketCliente ticketCliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ticket_vendedor_id", nullable = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private com.ninabit.bono.modules.vendorprize.TicketVendedor ticketVendedor;

    @Column(name = "tipo_participante")
    @Builder.Default
    private String tipoParticipante = "CLIENTE";


    @Column(name = "cliente_ci", nullable = false)
    private String clienteCi;

    @Column(name = "cliente_nombre", nullable = false)
    private String clienteNombre;

    @Column(name = "cliente_celular")
    private String clienteCelular;

    @Column(nullable = false)
    private String serial;

    @Column(name = "producto_modelo")
    private String productoModelo;

    @Column(nullable = false)
    private String premio;

    @Column(nullable = false)
    @Builder.Default
    private Integer orden = 1;

    @Column(name = "evidencia_acta_url")
    private String evidenciaActaUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
