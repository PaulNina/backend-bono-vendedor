package com.ninabit.bono.modules.tienda;

import com.ninabit.bono.modules.city.Ciudad;
import com.ninabit.bono.modules.user.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "solicitudes_tienda")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitudTienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_solicitud", nullable = false)
    @Builder.Default
    private TipoSolicitud tipoSolicitud = TipoSolicitud.NUEVA_TIENDA;

    /** Tienda existente que se reclama (si tipoSolicitud == RECLAMAR_TIENDA) */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_existente_id", nullable = true)
    private Tienda tiendaExistente;

    // --- Datos de la tienda propuesta ---
    @Column(name = "nombre_tienda")
    private String nombreTienda;

    @Column(name = "codigo_tienda")
    private String codigoTienda;

    @Column(name = "canal")
    private String canal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ciudad_id", nullable = true)
    private Ciudad ciudad;

    @Column(name = "departamento")
    private String departamento;

    @Column(name = "direccion")
    private String direccion;

    @Column(name = "telefono_tienda")
    private String telefonoTienda;

    @Column(name = "nit")
    private String nit;

    // --- Datos del solicitante / dueño / encargado ---
    @Column(name = "nombre_solicitante", nullable = false)
    private String nombreSolicitante;

    @Column(name = "ci_solicitante")
    private String ciSolicitante;

    @Column(name = "telefono_solicitante", nullable = false)
    private String telefonoSolicitante;

    @Column(name = "email_solicitante", nullable = false)
    private String emailSolicitante;

    /** Password codificado o temporal para crear la cuenta de usuario al ser aprobada */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "cargo_solicitante")
    private String cargoSolicitante; // "PROPIETARIO", "ENCARGADO", "ADMINISTRADOR"

    // --- Datos de cobro y pagos (opcionales) ---
    @Column(name = "banco")
    private String banco;

    @Column(name = "nro_cuenta")
    private String nroCuenta;

    @Column(name = "titular_cobro")
    private String titularCobro;

    @Column(name = "telefono_titular")
    private String telefonoTitular;

    @Column(name = "qr_pago_url")
    private String qrPagoUrl;

    // --- Verificación / Respaldo ---
    @Column(name = "foto_verificacion_url")
    private String fotoVerificacionUrl;

    @Column(name = "notas_adicionales", columnDefinition = "TEXT")
    private String notasAdicionales;

    // --- Estado de la solicitud ---
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    @Builder.Default
    private EstadoSolicitud estado = EstadoSolicitud.PENDIENTE;

    @Column(name = "motivo_rechazo", columnDefinition = "TEXT")
    private String motivoRechazo;

    @Column(name = "revisado_por")
    private String revisadoPor;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    /** Tienda creada o vinculada tras la aprobación */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tienda_resultado_id", nullable = true)
    private Tienda tiendaResultado;

    /** Cuenta de usuario creada o vinculada tras la aprobación */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_creado_id", nullable = true)
    private Usuario usuarioCreado;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (estado == null) {
            estado = EstadoSolicitud.PENDIENTE;
        }
        if (tipoSolicitud == null) {
            tipoSolicitud = TipoSolicitud.NUEVA_TIENDA;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum TipoSolicitud {
        NUEVA_TIENDA,
        RECLAMAR_TIENDA
    }

    public enum EstadoSolicitud {
        PENDIENTE,
        APROBADA,
        RECHAZADA
    }
}
