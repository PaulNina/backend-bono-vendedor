package com.ninabit.bono.modules.clientcampaign;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "entregas_ruleta")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class EntregaRuleta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ToString.Exclude
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "registro_cliente_id", nullable = false, unique = true)
    private RegistroCliente registroCliente;

    @JsonIgnore
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "campana_cliente_id", nullable = false)
    private CampanaCliente campanaCliente;

    @JsonIgnore
    public RegistroCliente getRegistroCliente() {
        return this.registroCliente;
    }

    @JsonIgnore
    public CampanaCliente getCampanaCliente() {
        return this.campanaCliente;
    }

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "premio_ruleta_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private PremioRuleta premioRuleta;

    @Column(name = "impulsador_id", nullable = false)
    private Long impulsadorId;

    @Column(name = "impulsador_nombre")
    private String impulsadorNombre;

    @Column(name = "fecha_giro", nullable = false)
    private LocalDateTime fechaGiro;

    @Column(name = "foto_entrega")
    private String fotoEntrega;

    @Column(nullable = false)
    @Builder.Default
    private boolean revertido = false;

    @Column(name = "motivo_reversion")
    private String motivoReversion;

    @Column(name = "revertido_por")
    private String revertidoPor;

    @Column(name = "fecha_reversion")
    private LocalDateTime fechaReversion;

    @PrePersist
    public void prePersist() {
        if (this.fechaGiro == null) {
            this.fechaGiro = LocalDateTime.now();
        }
    }
}
