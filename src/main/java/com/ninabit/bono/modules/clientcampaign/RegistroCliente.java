package com.ninabit.bono.modules.clientcampaign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "registros_cliente")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class RegistroCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "campana_cliente_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private CampanaCliente campanaCliente;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal_registro", nullable = false)
    private CanalRegistro canalRegistro;

    @Column(name = "impulsador_id")
    private Long impulsadorId;

    @Column(name = "impulsador_nombre")
    private String impulsadorNombre;

    @Column(name = "nombre_cliente", nullable = false)
    private String nombreCliente;

    @Column(name = "apellido_cliente", nullable = false)
    private String apellidoCliente;

    @Column(name = "ci")
    private String ci;

    @Column(name = "celular", nullable = false)
    private String celular;

    private String email;

    @Column(nullable = false)
    private String ciudad;

    @Column(name = "punto_venta")
    private String puntoVenta;

    @Column(name = "producto_id")
    private Long productoId;

    @Column(name = "producto_modelo")
    private String productoModelo;

    @Column(name = "producto_pulgadas")
    private Integer productoPulgadas;

    @Column(nullable = false)
    private String serial;

    @Column(name = "fecha_compra", nullable = false)
    private LocalDate fechaCompra;

    // Los 5 respaldos obligatorios
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

    // Estado de validación por Supervisor
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EstadoRegistro estado = EstadoRegistro.PENDIENTE;

    @Column(name = "supervisor_id")
    private Long supervisorId;

    @Column(name = "supervisor_nombre")
    private String supervisorNombre;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    @Column(name = "motivo_observacion", columnDefinition = "TEXT")
    private String motivoObservacion;

    @Column(name = "historial_revisiones", columnDefinition = "TEXT")
    private String historialRevisiones;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum CanalRegistro {
        BANDEO, WEB, CHATBOT
    }

    public enum EstadoRegistro {
        PENDIENTE, EN_REVISION, OBSERVADO, RECHAZADO, APROBADO
    }
}
