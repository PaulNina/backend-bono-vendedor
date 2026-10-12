package com.ninabit.bono.modules.clientcampaign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "campanas_cliente")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CampanaCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    private String subtitulo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "registro_habilitado", nullable = false)
    @Builder.Default
    private boolean registroHabilitado = true;

    @Column(name = "ruleta_habilitada", nullable = false)
    @Builder.Default
    private boolean ruletaHabilitada = true;

    @Column(name = "web_habilitada", nullable = false)
    @Builder.Default
    private boolean webHabilitada = true;

    @Column(name = "chatbot_habilitado", nullable = false)
    @Builder.Default
    private boolean chatbotHabilitado = true;

    @Column(name = "premio_final_monto", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal premioFinalMonto = new BigDecimal("100000.00");

    @Column(name = "premio_final_descripcion")
    @Builder.Default
    private String premioFinalDescripcion = "Gran Sorteo Final de Bs. 100.000";

    @Column(name = "terminos_condiciones", columnDefinition = "TEXT")
    private String terminosCondiciones;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
