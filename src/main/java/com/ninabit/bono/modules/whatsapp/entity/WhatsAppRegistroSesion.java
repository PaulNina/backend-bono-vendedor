package com.ninabit.bono.modules.whatsapp.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_registro_sesiones", indexes = {
        @Index(name = "idx_wa_sesion_telefono", columnList = "telefono")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppRegistroSesion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telefono", nullable = false, unique = true, length = 40)
    private String telefono;

    @Column(name = "from_phone_number_id", length = 50)
    private String fromPhoneNumberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 30, nullable = false)
    private EstadoSesion estado;

    @Column(name = "campana_id")
    private Long campanaId;

    @Column(name = "serial", length = 100)
    private String serial;

    @Column(name = "producto_id")
    private Long productoId;

    @Column(name = "producto_modelo", length = 100)
    private String productoModelo;

    @Column(name = "producto_pulgadas")
    private Integer productoPulgadas;

    @Column(name = "tickets_proyectados")
    private Integer ticketsProyectados;

    @Column(name = "nombre_cliente", length = 100)
    private String nombreCliente;

    @Column(name = "apellido_cliente", length = 100)
    private String apellidoCliente;

    @Column(name = "ci", length = 50)
    private String ci;

    @Column(name = "celular", length = 40)
    private String celular;

    @Column(name = "ciudad", length = 100)
    private String ciudad;

    @Column(name = "punto_venta", length = 150)
    private String puntoVenta;

    @Column(name = "foto_tag")
    private String fotoTag;

    @Column(name = "foto_poliza")
    private String fotoPoliza;

    @Column(name = "foto_nota")
    private String fotoNota;

    @Column(name = "foto_ci_anverso")
    private String fotoCiAnverso;

    @Column(name = "foto_ci_reverso")
    private String fotoCiReverso;

    @Column(name = "fotos_recibidas")
    private int fotosRecibidas;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum EstadoSesion {
        INICIO,
        ESPERANDO_SERIAL,
        ESPERANDO_NOMBRE,
        ESPERANDO_CI,
        ESPERANDO_CIUDAD,
        ESPERANDO_TIENDA,
        ESPERANDO_DATOS,
        ESPERANDO_FOTOS,
        CONSULTA_TICKETS,
        COMPLETADO
    }
}
