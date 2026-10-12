package com.ninabit.bono.modules.clientcampaign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sorteos_periodos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SorteoPeriodo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "campana_cliente_id", nullable = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private CampanaCliente campanaCliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "campana_id", nullable = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private com.ninabit.bono.modules.campaign.Campana campana;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_participante")
    @Builder.Default
    private TipoParticipante tipoParticipante = TipoParticipante.CLIENTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_sorteo", nullable = false)
    private TipoSorteo tipoSorteo;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "fecha_sorteo", nullable = false)
    private LocalDate fechaSorteo;

    @Column(name = "fecha_corte_registro", nullable = false)
    private LocalDate fechaCorteRegistro;

    @Column(name = "descripcion_premios", columnDefinition = "TEXT")
    private String descripcionPremios;

    @Column(nullable = false)
    @Builder.Default
    private boolean ejecutado = false;

    @Column(name = "fecha_ejecucion")
    private LocalDateTime fechaEjecucion;

    @Column(name = "usuario_ejecutor")
    private String usuarioEjecutor;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public enum TipoSorteo {
        QUINCENAL, FINAL
    }

    public enum TipoParticipante {
        CLIENTE, VENDEDOR, TIENDA
    }
}

