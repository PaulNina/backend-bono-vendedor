package com.ninabit.bono.modules.clientcampaign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventario_impulsador")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class InventarioImpulsador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "campana_cliente_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private CampanaCliente campanaCliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "premio_ruleta_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private PremioRuleta premioRuleta;

    /** ID de usuario si el destinatario es un Operador de Bandeo o Trabajador Skyworth */
    @Column(name = "usuario_id")
    private Long usuarioId;

    /** ID de vendedor tradicional si aplica */
    @Column(name = "vendedor_id")
    private Long vendedorId;

    @Column(name = "vendedor_nombre")
    private String vendedorNombre;

    /** Nombre de la persona o entidad a quien se le entregó el stock */
    @Column(name = "nombre_destinatario")
    private String nombreDestinatario;

    /** Tipo de destinatario: OPERADOR_BANDEO, TRABAJADOR_SKYWORTH, VENDEDOR, TIENDA */
    @Column(name = "tipo_destinatario")
    @Builder.Default
    private String tipoDestinatario = "OPERADOR_BANDEO";

    /** Tienda o punto de venta físico donde se realiza el bandeo */
    @Column(name = "tienda_id")
    private Long tiendaId;

    @Column(name = "tienda_nombre")
    private String tiendaNombre;

    @Column(name = "cantidad_asignada", nullable = false)
    @Builder.Default
    private Integer cantidadAsignada = 0;

    @Column(name = "cantidad_entregada", nullable = false)
    @Builder.Default
    private Integer cantidadEntregada = 0;

    /** Cantidad física devuelta al almacén/administrador al cerrar turno */
    @Column(name = "cantidad_devuelta", nullable = false)
    @Builder.Default
    private Integer cantidadDevuelta = 0;

    /** Saldo = Asignada - Entregada - Devuelta (si > 0 al cerrar, representa faltante) */
    @Column(nullable = false)
    @Builder.Default
    private Integer saldo = 0;

    /** Estado de conciliación: ABIERTO, CONCILIADO, CON_DIFERENCIA */
    @Column(name = "estado_conciliacion")
    @Builder.Default
    private String estadoConciliacion = "ABIERTO";

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "fecha_conciliacion")
    private LocalDateTime fechaConciliacion;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void calculateSaldo() {
        if (cantidadAsignada == null) cantidadAsignada = 0;
        if (cantidadEntregada == null) cantidadEntregada = 0;
        if (cantidadDevuelta == null) cantidadDevuelta = 0;
        this.saldo = this.cantidadAsignada - this.cantidadEntregada - this.cantidadDevuelta;
        if (this.saldo <= 0 && (this.cantidadEntregada > 0 || this.cantidadDevuelta > 0)) {
            this.estadoConciliacion = "CONCILIADO";
        } else if (this.estadoConciliacion == null) {
            this.estadoConciliacion = "ABIERTO";
        }
        this.updatedAt = LocalDateTime.now();
    }
}
