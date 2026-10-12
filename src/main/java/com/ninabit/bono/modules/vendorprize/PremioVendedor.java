package com.ninabit.bono.modules.vendorprize;

import com.ninabit.bono.modules.campaign.Campana;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "premios_vendedor")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PremioVendedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "campana_id", nullable = true)
    private Campana campana;

    @Column(nullable = false)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "puntos_requeridos", nullable = false)
    private Integer puntosRequeridos;

    @Column(nullable = false)
    @Builder.Default
    private Integer stock = 0;

    private String foto;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    /**
     * Condiciones y restricciones para canjear este premio.
     * Texto libre: ej. "Válido solo para vendedores con más de 3 meses de antigüedad."
     * (§6.1 Informe Complementario)
     */
    @Column(columnDefinition = "TEXT")
    private String condiciones;

    /**
     * JSON con lista de ciudades elegibles para el canje de este premio.
     * Null o vacío = aplica a todas las ciudades.
     * Ej: ["La Paz","Cochabamba"]
     * (§6.1 Informe Complementario — "condiciones y ámbito")
     */
    @Column(name = "alcance_ciudades", columnDefinition = "TEXT")
    private String alcanceCiudades;

    /**
     * Política de tratamiento del saldo restante tras el canje (§6.1 Informe Complementario):
     * - DESCUENTO_EXACTO: se descuentan exactamente los puntos del costo del premio.
     * - REINICIO_SALDO: el saldo de puntos de la campaña se reinicia a cero tras el canje.
     * La interfaz debe mostrar el efecto antes de confirmar.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "politica_canje", length = 30)
    @Builder.Default
    private PoliticaCanje politicaCanje = PoliticaCanje.DESCUENTO_EXACTO;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public enum PoliticaCanje {
        /** Solo se descuenta el costo exacto del premio */
        DESCUENTO_EXACTO,
        /** El saldo total de la campaña se reinicia a cero al canjear */
        REINICIO_SALDO
    }
}
