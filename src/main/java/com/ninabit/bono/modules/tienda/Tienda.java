package com.ninabit.bono.modules.tienda;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tiendas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", length = 50)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "canal", length = 50)
    private String canal;

    @Column(name = "departamento", length = 50)
    private String departamento;

    @Column(name = "nombre_propietario")
    private String nombrePropietario;

    @Column(name = "encargado_nombre")
    private String encargadoNombre;

    @Column(name = "encargado_telefono")
    private String encargadoTelefono;

    @Column(name = "encargado_email")
    private String encargadoEmail;

    @Column(name = "encargado_ci")
    private String encargadoCi;

    @Column(name = "qr_pago_url", length = 500)
    private String qrPagoUrl;

    @Column(name = "banco", length = 100)
    private String banco;

    @Column(name = "nro_cuenta", length = 100)
    private String nroCuenta;

    @Column(name = "titular_cobro")
    private String titularCobro;

    @Column(name = "telefono_titular")
    private String telefonoTitular;

    private String direccion;

    private String telefono;

    @ManyToOne
    @JoinColumn(name = "ciudad_id", nullable = true)
    private com.ninabit.bono.modules.city.Ciudad ciudad;

    @Column(name = "nit")
    private String nit;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = java.time.LocalDateTime.now();
        }
        if (codigo == null || codigo.isBlank()) {
            codigo = "TND-" + String.format("%05d", (int)(Math.random() * 90000 + 10000));
        }
    }
}
