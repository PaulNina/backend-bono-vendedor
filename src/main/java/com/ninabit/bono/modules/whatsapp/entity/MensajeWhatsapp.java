package com.ninabit.bono.modules.whatsapp.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "mensajes_whatsapp", indexes = {
        @Index(name = "idx_wa_from_number", columnList = "from_number"),
        @Index(name = "idx_wa_created_at", columnList = "created_at")
})
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MensajeWhatsapp {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wa_message_id", unique = true, length = 128)
    private String waMessageId;

    @Column(name = "from_number", length = 30)
    private String fromNumber;

    @Column(name = "to_number", length = 50)
    private String toNumber;

    /** ID numérico de teléfono en Meta Cloud API por el cual se procesó el mensaje */
    @Column(name = "phone_number_id", length = 50)
    private String phoneNumberId;

    @Column(name = "nombre_contacto", length = 100)
    private String nombreContacto;

    @Enumerated(EnumType.STRING) @Column(length = 20)
    private TipoMensaje tipo;

    @Enumerated(EnumType.STRING) @Column(length = 10, nullable = false)
    private DireccionMensaje direccion;

    @Column(columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "media_url")
    private String mediaUrl;

    @Column(name = "media_id", length = 100)
    private String mediaId;

    @Enumerated(EnumType.STRING) @Column(length = 15)
    private EstadoMensaje estado;

    @Column(name = "es_respuesta_chatbot")
    private boolean esRespuestaChatbot;

    @Column(name = "campana_id")
    private Long campanaId;

    @Column(name = "registro_cliente_id")
    private Long registroClienteId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum TipoMensaje { TEXTO, IMAGEN, AUDIO, VIDEO, DOCUMENTO, STICKER, UBICACION, REACCION, TEMPLATE, INTERACTIVO, DESCONOCIDO }
    public enum DireccionMensaje { ENTRADA, SALIDA }
    public enum EstadoMensaje { RECIBIDO, ENVIADO, ENTREGADO, LEIDO, FALLIDO }
}
