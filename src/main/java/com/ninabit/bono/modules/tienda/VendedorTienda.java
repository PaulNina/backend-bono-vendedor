package com.ninabit.bono.modules.tienda;

import com.ninabit.bono.modules.campaign.Campana;
import com.ninabit.bono.modules.vendor.Vendedor;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "vendedor_tienda")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendedorTienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendedor_id", nullable = false)
    private Vendedor vendedor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id", nullable = false)
    private Tienda tienda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campana_id", nullable = true)
    private Campana campana;

    @Column(name = "activo", nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "fecha_asignacion")
    private LocalDateTime fechaAsignacion;

    @PrePersist
    public void prePersist() {
        if (fechaAsignacion == null) {
            fechaAsignacion = LocalDateTime.now();
        }
    }
}
