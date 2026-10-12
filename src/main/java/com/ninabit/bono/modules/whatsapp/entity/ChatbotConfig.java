package com.ninabit.bono.modules.whatsapp.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "chatbot_config")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ChatbotConfig {

    @Id
    private Long id;

    @Column(name = "activo")
    private boolean activo;

    @Enumerated(EnumType.STRING) @Column(name = "proveedor_ia", length = 20)
    private ProveedorIA proveedorIa;

    @Column(name = "api_key", columnDefinition = "TEXT")
    private String apiKey;

    @Column(name = "modelo", length = 60)
    private String modelo;

    @Column(name = "base_url", length = 255)
    private String baseUrl;

    @Column(name = "prompt_sistema", columnDefinition = "TEXT")
    private String promptSistema;

    @Column(name = "temperatura")
    private Double temperatura;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    /** JSON array: [{pregunta, respuesta, keywords:[]}] */
    @Column(name = "faqs_json", columnDefinition = "TEXT")
    private String faqsJson;

    @Column(name = "umbral_similitud_faq")
    private Double umbralSimilitudFaq;

    // ── Credenciales WhatsApp Cloud API (configurables sin redesplegar) ──
    @Column(name = "wa_access_token", columnDefinition = "TEXT")
    private String waAccessToken;

    @Column(name = "wa_phone_number_id", length = 50)
    private String waPhoneNumberId;

    @Column(name = "wa_numero_1", length = 50)
    private String waNumero1;

    @Column(name = "wa_phone_number_id_2", length = 50)
    private String waPhoneNumberId2;

    @Column(name = "wa_numero_2", length = 50)
    private String waNumero2;

    @Column(name = "wa_verify_token", length = 100)
    private String waVerifyToken;

    // ── Modo de Registro de Clientes por WhatsApp ──
    @Enumerated(EnumType.STRING)
    @Column(name = "modo_registro_wa", length = 30)
    private ModoRegistroWa modoRegistroWa;

    @Column(name = "cantidad_fotos_requeridas")
    private Integer cantidadFotosRequeridas;

    @Column(name = "mensaje_redireccion_web", columnDefinition = "TEXT")
    private String mensajeRedireccionWeb;

    @UpdateTimestamp @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum ProveedorIA { OPENAI, GEMINI, DEEPSEEK }
    public enum ModoRegistroWa { PASO_A_PASO, COMPACTO, SOLO_WEB }
}

