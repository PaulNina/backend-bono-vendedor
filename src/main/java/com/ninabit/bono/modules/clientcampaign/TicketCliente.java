package com.ninabit.bono.modules.clientcampaign;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tickets_cliente")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class TicketCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_ticket", nullable = false, unique = true, length = 50)
    private String codigoTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registro_cliente_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private RegistroCliente registroCliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "campana_cliente_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private CampanaCliente campanaCliente;

    @JsonIgnore
    public RegistroCliente getRegistroCliente() {
        return this.registroCliente;
    }

    @JsonIgnore
    public CampanaCliente getCampanaCliente() {
        return this.campanaCliente;
    }

    @Column(name = "cliente_ci")
    private String clienteCi;

    @Column(name = "cliente_nombre", nullable = false)
    private String clienteNombre;

    @Column(name = "cliente_celular", nullable = false)
    private String clienteCelular;

    @Column(nullable = false)
    private String serial;

    @Column(name = "producto_modelo")
    private String productoModelo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EstadoTicket estado = EstadoTicket.NO_HABILITADO;

    /**
     * Si el cliente ya ganó en una tómbola quincenal previa de esta campaña,
     * este campo se marca en true para excluir todos sus tickets de futuros sorteos quincenales.
     * En el sorteo final de Bs. 100.000, TODOS los tickets HABILITADOS participan sin importar este flag.
     */
    @Column(name = "excluido_quincenal", nullable = false)
    @Builder.Default
    private boolean excluidoQuincenal = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public enum EstadoTicket {
        NO_HABILITADO, HABILITADO, PREMIADO, ANULADO
    }
}
