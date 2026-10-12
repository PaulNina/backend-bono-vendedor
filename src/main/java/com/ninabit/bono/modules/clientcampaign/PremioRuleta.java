package com.ninabit.bono.modules.clientcampaign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "premios_ruleta")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PremioRuleta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "campana_cliente_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private CampanaCliente campanaCliente;

    @Column(nullable = false)
    private String nombre;

    private String descripcion;

    @Column(name = "imagen_url")
    private String imagenUrl;

    @Column(name = "color_hex", length = 20)
    @Builder.Default
    private String colorHex = "#1D4ED8";

    @Column(name = "probabilidad_peso", nullable = false)
    @Builder.Default
    private Integer probabilidadPeso = 10;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "orden_ruleta")
    @Builder.Default
    private Integer ordenRuleta = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
