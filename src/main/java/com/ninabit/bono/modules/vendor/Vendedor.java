package com.ninabit.bono.modules.vendor;

import com.ninabit.bono.modules.tienda.Tienda;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "vendedores")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vendedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_completo", nullable = false)
    private String nombreCompleto;

    @Column(nullable = false, unique = true)
    private String email;

    private String telefono;

    private String ci;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_id")
    private Tienda tienda;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "pending_approval")
    @Builder.Default
    private Boolean pendingApproval = false;

    @Column(name = "talla_polera")
    private String tallaPolera;

    @Column(name = "foto_qr")
    private String fotoQr;
}
