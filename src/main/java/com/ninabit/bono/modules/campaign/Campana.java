package com.ninabit.bono.modules.campaign;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "campanas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Campana {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    private String subtitulo;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "registro_habilitado")
    @Builder.Default
    private boolean registroHabilitado = true;

    @Column(name = "validacion_ia")
    @Builder.Default
    private boolean validacionIa = false;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "campana_tienda_excluida",
        joinColumns = @JoinColumn(name = "campana_id"),
        inverseJoinColumns = @JoinColumn(name = "tienda_id")
    )
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Builder.Default
    private java.util.Set<com.ninabit.bono.modules.tienda.Tienda> tiendasExcluidas = new java.util.HashSet<>();

    /** Ciclo de vida completo de la campaña (§1 Informe Complementario) */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_campana", length = 20)
    @Builder.Default
    private EstadoCampana estadoCampana = EstadoCampana.BORRADOR;

    public enum EstadoCampana {
        /** En configuración, no visible aún para vendedores */
        BORRADOR,
        /** Publicada y en operación */
        ACTIVA,
        /** Suspendida temporalmente; puede reanudarse */
        PAUSADA,
        /** Finalizada; no acepta nuevas ventas */
        CERRADA,
        /** Archivada permanentemente; solo consulta histórica */
        ARCHIVADA
    }
}
