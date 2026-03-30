package com.ninabit.bono.modules.vendor;

import com.ninabit.bono.modules.city.Ciudad;
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

    @Column(nullable = false)
    private String telefono;

    @Column(nullable = false)
    private String ci;

    /**
     * Relación directa con Ciudad (reemplaza tienda_id → tiendas → ciudad_id).
     * La columna ciudad_id debe existir en la tabla vendedores (ver script SQL de migración).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ciudad_id", nullable = false)
    private Ciudad ciudad;

    /**
     * Nombre de tienda como texto libre (ya no FK a tiendas).
     * La columna tienda debe existir en la tabla vendedores (ver script SQL de migración).
     */
    @Column(name = "tienda", nullable = false)
    private String tienda;

    /**
     * Campo legacy — tienda_id sigue en la BD pero JPA no lo gestiona.
     * Se puede eliminar la columna en una limpieza futura.
     */
    @Column(name = "tienda_id", insertable = false, updatable = false)
    private Long tiendaId;

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
