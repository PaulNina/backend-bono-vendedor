package com.ninabit.bono.modules.whatsapp.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_conversaciones", indexes = {
        @Index(name = "idx_wa_conv_estado", columnList = "estado"),
        @Index(name = "idx_wa_conv_atendido", columnList = "atendido_por")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppConversacion {

    @Id
    @Column(name = "telefono", length = 40, nullable = false)
    private String telefono;

    @Column(name = "nombre_contacto", length = 100)
    private String nombreContacto;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 30, nullable = false)
    @Builder.Default
    private EstadoConversacion estado = EstadoConversacion.ABIERTA;

    @Enumerated(EnumType.STRING)
    @Column(name = "atendido_por", length = 20, nullable = false)
    @Builder.Default
    private TipoAtencion atendidoPor = TipoAtencion.BOT;

    @Column(name = "bot_silenciado", nullable = false)
    @Builder.Default
    private boolean botSilenciado = false;

    @Column(name = "operador_asignado", length = 100)
    private String operadorAsignado;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @Column(name = "ultimo_mensaje", columnDefinition = "TEXT")
    private String ultimoMensaje;

    @Column(name = "fecha_ultimo_mensaje")
    private LocalDateTime fechaUltimoMensaje;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum EstadoConversacion {
        ABIERTA,
        EN_REGISTRO,
        ESPERANDO_AGENTE,
        FINALIZADA
    }

    public enum TipoAtencion {
        BOT,
        HUMANO
    }
}
