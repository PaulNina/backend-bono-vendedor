package com.ninabit.bono.modules.user;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    @Column(name = "nombre_completo")
    private String nombreCompleto;

    @Column(name = "vendedor_id")
    private Long vendedorId;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    /** City assigned to reviewer/supervisor-type users */
    private String ciudad;

    /** Store ID assigned for store managers or store-level supervisors */
    @Column(name = "tienda_id")
    private Long tiendaId;

    @Column(name = "reset_password_token", length = 100)
    private String resetPasswordToken;

    @Column(name = "reset_password_token_expiry")
    private java.time.LocalDateTime resetPasswordTokenExpiry;

    public enum Rol {
        ADMIN_TOTAL,
        ADMIN,
        ADMIN_OPERATIVO,
        SUPERVISOR,
        REVISOR,
        REVIEWER,
        ENCARGADO_TIENDA,
        VISOR,
        VISOR_CENTRAL,
        VENDOR,
        TECNICO,
        OPERADOR_BANDEO
    }
}
