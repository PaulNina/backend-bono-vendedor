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

    /** City assigned to reviewer-type users */
    private String ciudad;

    @Column(name = "reset_password_token", length = 100)
    private String resetPasswordToken;

    @Column(name = "reset_password_token_expiry")
    private java.time.LocalDateTime resetPasswordTokenExpiry;

    public enum Rol {
        ADMIN, VENDOR, REVIEWER, SUPERVISOR
    }
}
