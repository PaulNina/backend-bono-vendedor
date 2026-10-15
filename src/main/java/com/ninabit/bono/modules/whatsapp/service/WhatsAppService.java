package com.ninabit.bono.modules.whatsapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninabit.bono.modules.whatsapp.config.WhatsAppProperties;
import com.ninabit.bono.modules.whatsapp.dto.ConversacionResumen;
import com.ninabit.bono.modules.whatsapp.entity.ChatbotConfig;
import com.ninabit.bono.modules.whatsapp.entity.MensajeWhatsapp;
import com.ninabit.bono.modules.whatsapp.entity.MensajeWhatsapp.DireccionMensaje;
import com.ninabit.bono.modules.whatsapp.entity.MensajeWhatsapp.EstadoMensaje;
import com.ninabit.bono.modules.whatsapp.entity.MensajeWhatsapp.TipoMensaje;
import com.ninabit.bono.modules.whatsapp.entity.WhatsAppConversacion;
import com.ninabit.bono.modules.whatsapp.repository.ChatbotConfigRepository;
import com.ninabit.bono.modules.whatsapp.repository.MensajeWhatsappRepository;
import com.ninabit.bono.modules.whatsapp.repository.WhatsAppConversacionRepository;
import com.ninabit.bono.modules.whatsapp.dto.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    private final MensajeWhatsappRepository mensajeRepo;
    private final WhatsAppConversacionRepository conversacionRepo;
    private final ChatbotConfigRepository configRepo;
    private final WhatsAppProperties props;
    private final ChatbotService chatbotService;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    @Lazy
    private WhatsAppFlowService whatsAppFlowService;

    @org.springframework.beans.factory.annotation.Autowired
    @Lazy
    private com.ninabit.bono.modules.clientcampaign.RegistroClienteRepository registroClienteRepo;

    @PostConstruct
    public void init() {
        chatbotService.setWhatsAppSender((to, texto, fromPhoneId, esChatbot) ->
                enviarMensajeTexto(to, texto, fromPhoneId, esChatbot));
    }

    // ── Métodos de resolución dinámica de credenciales (BD primero, properties como fallback) ──

    public String getEffectiveAccessToken() {
        return configRepo.findFirstByOrderByIdAsc()
                .map(ChatbotConfig::getWaAccessToken)
                .filter(s -> s != null && !s.isBlank())
                .orElse(props.getAccessToken() != null ? props.getAccessToken() : "");
    }

    public String getEffectivePhoneNumberId() {
        return configRepo.findFirstByOrderByIdAsc()
                .map(ChatbotConfig::getWaPhoneNumberId)
                .filter(s -> s != null && !s.isBlank())
                .orElse(props.getPhoneNumberId() != null ? props.getPhoneNumberId() : "");
    }

    public String getEffectivePhoneNumberId2() {
        return configRepo.findFirstByOrderByIdAsc()
                .map(ChatbotConfig::getWaPhoneNumberId2)
                .filter(s -> s != null && !s.isBlank())
                .orElse("");
    }

    public String getEffectiveVerifyToken() {
        return configRepo.findFirstByOrderByIdAsc()
                .map(ChatbotConfig::getWaVerifyToken)
                .filter(s -> s != null && !s.isBlank())
                .orElse(props.getVerifyToken() != null ? props.getVerifyToken() : "");
    }

    public String getEffectiveHeaderImageUrl() {
        return configRepo.findFirstByOrderByIdAsc()
                .map(ChatbotConfig::getWaHeaderImageUrl)
                .filter(s -> s != null && !s.isBlank())
                .orElse(DEFAULT_HEADER_IMAGE);
    }

    @Transactional
    public String guardarHeaderImageUrl(String url) {
        ChatbotConfig cfg = configRepo.findFirstByOrderByIdAsc().orElseGet(() -> {
            ChatbotConfig c = new ChatbotConfig();
            c.setId(1L);
            return c;
        });
        String finalUrl = (url != null && !url.isBlank()) ? url.trim() : DEFAULT_HEADER_IMAGE;
        cfg.setWaHeaderImageUrl(finalUrl);
        configRepo.save(cfg);
        log.info("URL de imagen de cabecera de WhatsApp actualizada en BD: {}", finalUrl);
        return finalUrl;
    }

    // ── Enviar mensaje de texto ──────────────────────────────────
    public void enviarMensajeTexto(String to, String texto) {
        enviarMensajeTexto(to, texto, null, false);
    }

    public void enviarMensajeTexto(String to, String texto, boolean esChatbot) {
        enviarMensajeTexto(to, texto, null, esChatbot);
    }

    public void enviarMensajeTexto(String to, String texto, String fromPhoneNumberId, boolean esChatbot) {
        String token = getEffectiveAccessToken();
        String defaultPhoneId = getEffectivePhoneNumberId();
        String activePhoneId = (fromPhoneNumberId != null && !fromPhoneNumberId.isBlank())
                ? fromPhoneNumberId.trim()
                : defaultPhoneId;

        if (token.isBlank() || activePhoneId.isBlank()) {
            log.error("No se puede enviar mensaje WA a {}: 'whatsapp.access-token' o Phone Number ID están vacíos en BD / properties.", to);
        }

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "text",
                "text", Map.of("body", texto)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        MensajeWhatsapp msg = MensajeWhatsapp.builder()
                .waMessageId(UUID.randomUUID().toString())
                .fromNumber("Skyworth")
                .phoneNumberId(activePhoneId)
                .toNumber(to)
                .tipo(TipoMensaje.TEXTO)
                .direccion(DireccionMensaje.SALIDA)
                .contenido(texto)
                .estado(EstadoMensaje.FALLIDO)
                .esRespuestaChatbot(esChatbot)
                .build();

        String messagesUrl = props.getApiBaseUrl() + "/" + props.getApiVersion() + "/" + activePhoneId + "/messages";

        try {
            RestTemplate rt = new RestTemplate();
            log.info("Enviando WA a URL: {} (Línea PhoneId: {})", messagesUrl, activePhoneId);
            ResponseEntity<Map> response = rt.postForEntity(
                    messagesUrl, new HttpEntity<>(body, headers), Map.class);

            String waId = extractSentMessageId(response.getBody());
            if (waId != null) {
                msg.setWaMessageId(waId);
            }
            msg.setEstado(EstadoMensaje.ENVIADO);
            mensajeRepo.save(msg);
            log.info("WA mensaje enviado exitosamente a {} por línea {} (chatbot={})", to, activePhoneId, esChatbot);
        } catch (Exception e) {
            if (e instanceof org.springframework.web.client.HttpStatusCodeException httpEx) {
                log.error("Meta Graph API rechazo el envio a {} (HTTP {}): {}", to, httpEx.getStatusCode(), httpEx.getResponseBodyAsString());
            } else {
                log.error("Error enviando WA mensaje a {}: {}", to, e.getMessage());
            }
            try {
                // Se guarda en base de datos como FALLIDO para que figure en el chat y no desaparezca
                mensajeRepo.save(msg);
            } catch (Exception exSave) {
                log.error("No se pudo guardar mensaje con estado FALLIDO: {}", exSave.getMessage());
            }
        }
    }

    // ── Enviar mensaje interactivo con botones ────────────────────
    public void enviarMensajeBotones(String to, String texto, List<Map<String, String>> botones, String fromPhoneNumberId) {
        String token = getEffectiveAccessToken();
        String defaultPhoneId = getEffectivePhoneNumberId();
        String activePhoneId = (fromPhoneNumberId != null && !fromPhoneNumberId.isBlank())
                ? fromPhoneNumberId.trim()
                : defaultPhoneId;

        if (token.isBlank() || activePhoneId.isBlank()) {
            log.error("No se puede enviar botones WA a {}: Access Token o Phone Number ID están vacíos.", to);
            return;
        }

        List<Map<String, Object>> buttonsPayload = new ArrayList<>();
        for (Map<String, String> btn : botones) {
            String title = btn.get("title");
            if (title != null && title.length() > 20) {
                title = title.substring(0, 20);
            }
            buttonsPayload.add(Map.of(
                    "type", "reply",
                    "reply", Map.of(
                            "id", btn.get("id"),
                            "title", title != null ? title : "Opción"
                    )
            ));
        }

        Map<String, Object> interactive = Map.of(
                "type", "button",
                "body", Map.of("text", texto),
                "action", Map.of("buttons", buttonsPayload)
        );

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", to,
                "type", "interactive",
                "interactive", interactive
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        String messagesUrl = props.getApiBaseUrl() + "/" + props.getApiVersion() + "/" + activePhoneId + "/messages";

        try {
            RestTemplate rt = new RestTemplate();
            log.info("Enviando botones interactivos WA a URL: {} (to: {})", messagesUrl, to);
            rt.postForEntity(messagesUrl, new HttpEntity<>(body, headers), Map.class);

            MensajeWhatsapp msg = MensajeWhatsapp.builder()
                    .waMessageId(UUID.randomUUID().toString())
                    .fromNumber("Skyworth")
                    .phoneNumberId(activePhoneId)
                    .toNumber(to)
                    .tipo(TipoMensaje.INTERACTIVO)
                    .direccion(DireccionMensaje.SALIDA)
                    .contenido(texto)
                    .estado(EstadoMensaje.ENVIADO)
                    .esRespuestaChatbot(true)
                    .build();
            mensajeRepo.save(msg);
        } catch (Exception e) {
            log.warn("Fallo al enviar botones interactivos a {}: {}. Enviando texto simple como respaldo.", to, e.getMessage());
            StringBuilder sb = new StringBuilder(texto).append("\n\n*Opciones:*");
            for (int i = 0; i < botones.size(); i++) {
                sb.append("\n").append(i + 1).append("️⃣ ").append(botones.get(i).get("title"));
            }
            enviarMensajeTexto(to, sb.toString(), fromPhoneNumberId, true);
        }
    }

    // ── Descargar fotografía de WhatsApp y guardar en disco ──────
    public String descargarYGuardarFoto(String mediaId, String prefix) {
        if (mediaId == null || mediaId.isBlank()) return null;
        try {
            String token = getEffectiveAccessToken();
            if (token.isBlank()) {
                log.error("No se puede descargar media WA: Access Token vacío.");
                return null;
            }

            String mediaMetaUrl = props.getApiBaseUrl() + "/" + props.getApiVersion() + "/" + mediaId;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            RestTemplate rt = new RestTemplate();
            ResponseEntity<Map> metaResp = rt.exchange(mediaMetaUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (metaResp.getBody() == null || !metaResp.getBody().containsKey("url")) {
                log.error("No se pudo obtener URL de descarga para mediaId {}", mediaId);
                return null;
            }
            String downloadUrl = (String) metaResp.getBody().get("url");

            HttpHeaders downloadHeaders = new HttpHeaders();
            downloadHeaders.setBearerAuth(token);
            downloadHeaders.set("User-Agent", "curl/7.64.1");
            ResponseEntity<byte[]> fileResp = rt.exchange(downloadUrl, HttpMethod.GET, new HttpEntity<>(downloadHeaders), byte[].class);
            byte[] fileBytes = fileResp.getBody();

            if (fileBytes == null || fileBytes.length == 0) return null;

            java.nio.file.Path dir = java.nio.file.Paths.get("uploads", "cliente_respaldos");
            if (!java.nio.file.Files.exists(dir)) {
                java.nio.file.Files.createDirectories(dir);
            }
            String filename = prefix + "_" + UUID.randomUUID().toString() + ".jpg";
            java.nio.file.Path target = dir.resolve(filename);
            java.nio.file.Files.write(target, fileBytes);
            log.info("Foto de WhatsApp guardada exitosamente en disco: {}", filename);
            return filename;
        } catch (Exception e) {
            log.error("Error descargando/guardando foto WA id {}: {}", mediaId, e.getMessage(), e);
            return null;
        }
    }
    // ── Verificación de ventana de sesión activa de 24h ──────────────
    /**
     * Retorna true si el cliente escribió al bot en las últimas 24 horas.
     * En ese caso, WhatsApp permite enviar mensajes de texto libre sin necesidad de template.
     *
     * @param celularInternacional número en formato internacional (ej: 59171234567)
     */
    public boolean tieneVentana24hActiva(String celularInternacional) {
        if (celularInternacional == null || celularInternacional.isBlank()) return false;
        try {
            LocalDateTime hace24h = LocalDateTime.now().minusHours(24);
            return mensajeRepo.existsMensajeEntradaDespuesDe(celularInternacional, hace24h);
        } catch (Exception e) {
            log.warn("Error verificando ventana 24h para {}: {}", celularInternacional, e.getMessage());
            return false;
        }
    }

    // ── Enviar template aprobado de Meta ─────────────────────────────
    /**
     * Envía un WhatsApp template (categoría Utility) aprobado por Meta.
     * Útil cuando el cliente NO escribió en las últimas 24h y no hay sesión activa.
     *
     * @param to            Número destino en formato internacional (ej: 59171234567)
     * @param templateName  Nombre del template registrado en Meta (ej: skyworth_registro_aprobado)
     * @param params        Parámetros del body en orden: {{1}}, {{2}}, {{3}}...
     * @param fallbackTexto Texto de respaldo a enviar como texto libre si el template falla
     */
    public static final String DEFAULT_HEADER_IMAGE = "https://bono.skyworth.bo/logo_skyworth.jpg";

    /**
     * Envía un template de WhatsApp con imagen de cabecera y texto libre de fallback si falla.
     * Todos los templates de Skyworth en Meta están configurados con cabecera de imagen.
     *
     * @param to            Número destino (ej: "59171234567" o "71234567")
     * @param templateName  Nombre del template registrado en Meta (ej: skyworth_registro_aprobado)
     * @param params        Parámetros del body en orden: {{1}}, {{2}}, {{3}}...
     * @param fallbackTexto Texto de respaldo a enviar como texto libre si el template falla
     */
    public void enviarTemplate(String to, String templateName, List<String> params, String fallbackTexto) {
        enviarTemplate(to, templateName, getEffectiveHeaderImageUrl(), params, fallbackTexto);
    }

    public void enviarTemplate(String to, String templateName, String headerImageUrl, List<String> params, String fallbackTexto) {
        TemplateSendResult res = enviarTemplateConResultado(to, templateName, headerImageUrl, params);
        if (!res.success()) {
            log.warn("Template '{}' no pudo enviarse a {}. Usando texto libre como fallback.", templateName, to);
            if (fallbackTexto != null && !fallbackTexto.isBlank()) {
                enviarMensajeTexto(to, fallbackTexto, null, true);
            }
        }
    }

    /**
     * Envía un template de WhatsApp y retorna el resultado detallado de la llamada a Meta.
     * Envía siempre la imagen de cabecera requerida por los templates aprobados.
     */
    public TemplateSendResult enviarTemplateConResultado(String to, String templateName, String headerImageUrl, List<String> params) {
        String token = getEffectiveAccessToken();
        String phoneId = getEffectivePhoneNumberId();

        if (token.isBlank() || phoneId.isBlank()) {
            log.error("No se puede enviar template WA a {}: token o phoneId vacíos.", to);
            return new TemplateSendResult(false, to, null, "Token o Phone Number ID de WhatsApp no configurados en BD / properties.");
        }

        String celular = to != null ? to.replaceAll("[^0-9]", "") : "";
        if (celular.length() == 8) {
            celular = "591" + celular;
        }

        MensajeWhatsapp msg = MensajeWhatsapp.builder()
                .waMessageId(UUID.randomUUID().toString())
                .fromNumber("Skyworth")
                .phoneNumberId(phoneId)
                .toNumber(celular)
                .tipo(MensajeWhatsapp.TipoMensaje.TEMPLATE)
                .direccion(MensajeWhatsapp.DireccionMensaje.SALIDA)
                .contenido("[Template: " + templateName + "] Params: " + (params != null ? String.join(", ", params) : ""))
                .estado(MensajeWhatsapp.EstadoMensaje.FALLIDO)
                .esRespuestaChatbot(false)
                .build();

        String currentTemplate = "skyworth_registro_observado".equals(templateName)
                ? "skyworth_registro_observado_2"
                : templateName;
        String currentHeaderImg = (headerImageUrl != null && !headerImageUrl.isBlank()) ? headerImageUrl.trim() : getEffectiveHeaderImageUrl();
        String currentLang = "es";
        String lastErrorMsg = null;

        for (int intento = 1; intento <= 4; intento++) {
            try {
                ResponseEntity<Map> resp = invocarMetaTemplateApi(phoneId, token, celular, currentTemplate, currentHeaderImg, params, currentLang);
                log.info("Template '{}' ({}) enviado exitosamente a {} (status: {})", currentTemplate, currentLang, celular, resp.getStatusCode());

                String waId = extractSentMessageId(resp.getBody());
                if (waId != null) {
                    msg.setWaMessageId(waId);
                }
                msg.setEstado(MensajeWhatsapp.EstadoMensaje.ENVIADO);
                msg.setContenido("[Template: " + currentTemplate + " (" + currentLang + ")] Params: " + (params != null ? String.join(", ", params) : ""));
                mensajeRepo.save(msg);
                return new TemplateSendResult(true, celular, waId, null);

            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (e instanceof org.springframework.web.client.HttpStatusCodeException httpEx) {
                    errorMsg = "HTTP " + httpEx.getStatusCode() + ": " + httpEx.getResponseBodyAsString();
                }
                lastErrorMsg = errorMsg;
                log.warn("Meta rechazó template '{}' ({}, header={}) a {}: {}", currentTemplate, currentLang, currentHeaderImg, celular, errorMsg);

                // Estrategia: Si el template no existe en 'es', intentar 'es_LA' (Español Latinoamérica)
                if ("es".equals(currentLang) && (errorMsg.contains("132001") || errorMsg.contains("translated language") || errorMsg.contains("does not exist"))) {
                    log.info("Reintentando template '{}' con idioma 'es_LA'...", currentTemplate);
                    currentLang = "es_LA";
                    continue;
                }

                // Estrategia 2: Si Meta rechaza por tipo de parámetro (132000), intentar con cantidad numérica en param 4
                if ((errorMsg.contains("132000") || errorMsg.contains("parameter") || errorMsg.contains("does not match")) && params != null && params.size() >= 4) {
                    String p4 = params.get(3);
                    if (p4 != null && !p4.matches("^\\d+$")) {
                        log.info("Reintentando template '{}' con conteo numérico en param 4...", currentTemplate);
                        List<String> sanitizedParams = new ArrayList<>(params);
                        int count = Math.max(1, p4.split(",").length);
                        sanitizedParams.set(3, String.valueOf(count));
                        params = sanitizedParams;
                        continue;
                    }
                }

                // Error no recuperable con reintentos
                break;
            }
        }

        try {
            mensajeRepo.save(msg);
        } catch (Exception ignored) {}
        return new TemplateSendResult(false, celular, null, lastErrorMsg);
    }

    private ResponseEntity<Map> invocarMetaTemplateApi(
            String phoneId, String token, String celular,
            String templateName, String headerImageUrl, List<String> params, String langCode
    ) {
        List<Map<String, Object>> components = new ArrayList<>();

        // Componente HEADER con imagen siempre presente para todos los templates de Skyworth
        String imgUrl = (headerImageUrl != null && !headerImageUrl.isBlank()) ? headerImageUrl.trim() : DEFAULT_HEADER_IMAGE;
        components.add(Map.of(
                "type", "header",
                "parameters", List.of(
                        Map.of(
                                "type", "image",
                                "image", Map.of("link", imgUrl)
                        )
                )
        ));

        // Componente BODY con variables de texto
        if (params != null && !params.isEmpty()) {
            List<Map<String, String>> parametros = params.stream()
                    .map(p -> Map.of("type", "text", "text", p != null ? p : ""))
                    .toList();
            components.add(Map.of("type", "body", "parameters", parametros));
        }

        Map<String, Object> templatePayload = new LinkedHashMap<>();
        templatePayload.put("name", templateName);
        templatePayload.put("language", Map.of("code", langCode != null ? langCode : "es"));
        templatePayload.put("components", components);

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", celular,
                "type", "template",
                "template", templatePayload
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        String url = props.getApiBaseUrl() + "/" + props.getApiVersion() + "/" + phoneId + "/messages";

        RestTemplate rt = new RestTemplate();
        return rt.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
    }

    /**
     * Envío en lote de templates WhatsApp.
     */
    public MasivoTemplateResponse enviarTemplatesMasivo(MasivoTemplateRequest req) {
        if (req == null || req.destinatarios() == null || req.destinatarios().isEmpty()) {
            return new MasivoTemplateResponse(0, 0, 0, List.of());
        }

        String img = (req.headerImageUrl() != null && !req.headerImageUrl().isBlank())
                ? req.headerImageUrl().trim()
                : getEffectiveHeaderImageUrl();

        int total = req.destinatarios().size();
        int exitosos = 0;
        int fallidos = 0;
        List<TemplateSendResult> resultados = new ArrayList<>();

        for (MasivoTemplateRequest.DestinatarioTemplate d : req.destinatarios()) {
            TemplateSendResult res = enviarTemplateConResultado(
                    d.to(),
                    req.templateName(),
                    img,
                    d.params()
            );
            resultados.add(res);
            if (res.success()) {
                exitosos++;
            } else {
                fallidos++;
            }
            // Pequeño retardo entre envíos para evitar throttling
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {}
        }

        return new MasivoTemplateResponse(total, exitosos, fallidos, resultados);
    }

    // ── Parsear payload entrante de Meta ─────────────────────────

    @Transactional
    public void procesarWebhookEntrante(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            log.warn("Payload de webhook WA vacío recibido.");
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            JsonNode entries = root.path("entry");
            if (!entries.isArray()) return;

            for (JsonNode entry : entries) {
                JsonNode changes = entry.path("changes");
                if (!changes.isArray()) continue;

                for (JsonNode change : changes) {
                    JsonNode value = change.path("value");
                    String field = change.path("field").asText();
                    if (!"messages".equals(field)) continue;

                    // 0. Extraer metadata de la línea telefónica receptora (multi-número)
                    JsonNode metadata = value.path("metadata");
                    String recipientDisplayPhone = metadata.path("display_phone_number").asText(null);
                    String recipientPhoneId = metadata.path("phone_number_id").asText(null);

                    // 1. Extraer nombres de perfil de contactos si vienen incluidos
                    Map<String, String> contactNames = new HashMap<>();
                    JsonNode contacts = value.path("contacts");
                    if (contacts.isArray()) {
                        for (JsonNode c : contacts) {
                            String waId = c.path("wa_id").asText();
                            String name = c.path("profile").path("name").asText(null);
                            if (waId != null && !waId.isBlank() && name != null && !name.isBlank()) {
                                contactNames.put(waId, name);
                            }
                        }
                    }

                    // 2. Procesar estados de entrega / lectura (sent, delivered, read, failed)
                    JsonNode statuses = value.path("statuses");
                    if (statuses.isArray()) {
                        for (JsonNode status : statuses) {
                            procesarStatus(status);
                        }
                    }

                    // 3. Procesar mensajes entrantes de clientes
                    JsonNode messages = value.path("messages");
                    if (messages.isArray()) {
                        for (JsonNode msg : messages) {
                            String from = msg.path("from").asText();
                            String contactName = contactNames.getOrDefault(from, null);
                            procesarMensajeEntrante(msg, contactName, recipientPhoneId, recipientDisplayPhone);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error al deserializar y procesar webhook WA: {}", e.getMessage(), e);
        }
    }

    private void procesarMensajeEntrante(JsonNode msg, String contactName, String recipientPhoneId, String recipientDisplayPhone) {
        String waId = msg.path("id").asText();
        if (waId != null && !waId.isBlank() && mensajeRepo.existsByWaMessageId(waId)) {
            log.debug("Mensaje WA duplicado ignorado: {}", waId);
            return;
        }

        String from = msg.path("from").asText();
        String typeStr = msg.path("type").asText("text");
        TipoMensaje tipo = parseTipo(typeStr);
        String contenido = extractContenido(msg, tipo);
        String mediaId = extractMediaId(msg, tipo);

        String activePhoneId = recipientPhoneId != null && !recipientPhoneId.isBlank()
                ? recipientPhoneId
                : getEffectivePhoneNumberId();

        String displayOrId = recipientDisplayPhone != null && !recipientDisplayPhone.isBlank()
                ? recipientDisplayPhone
                : activePhoneId;

        MensajeWhatsapp saved = mensajeRepo.save(MensajeWhatsapp.builder()
                .waMessageId(waId != null && !waId.isBlank() ? waId : UUID.randomUUID().toString())
                .fromNumber(from)
                .toNumber(displayOrId)
                .phoneNumberId(activePhoneId)
                .nombreContacto(contactName)
                .tipo(tipo)
                .direccion(DireccionMensaje.ENTRADA)
                .contenido(contenido)
                .mediaId(mediaId)
                .estado(EstadoMensaje.RECIBIDO)
                .esRespuestaChatbot(false)
                .build());

        log.info("Mensaje WA guardado exitosamente de {} hacia línea {} (Nombre: '{}', Tipo: {}): {}", 
                from, activePhoneId, contactName, tipo, contenido);

        // Actualizar o crear registro de WhatsAppConversacion
        WhatsAppConversacion conv = conversacionRepo.findById(from).orElse(null);
        if (conv == null) {
            String initialName = contactName;
            if (initialName == null || initialName.isBlank()) {
                initialName = mensajeRepo.findUltimoNombreContacto(from).orElse(null);
            }
            conv = WhatsAppConversacion.builder()
                    .telefono(from)
                    .nombreContacto(initialName)
                    .estado(WhatsAppConversacion.EstadoConversacion.ABIERTA)
                    .atendidoPor(WhatsAppConversacion.TipoAtencion.BOT)
                    .botSilenciado(false)
                    .ultimoMensaje(contenido)
                    .fechaUltimoMensaje(LocalDateTime.now())
                    .build();
        } else {
            if (contactName != null && !contactName.isBlank()) {
                conv.setNombreContacto(contactName);
            } else if (conv.getNombreContacto() == null || conv.getNombreContacto().isBlank()) {
                mensajeRepo.findUltimoNombreContacto(from).ifPresent(conv::setNombreContacto);
            }
            conv.setUltimoMensaje(contenido);
            conv.setFechaUltimoMensaje(LocalDateTime.now());
        }

        // Detección inteligente de solicitud de operador humano
        boolean pideHumano = esPeticionOperadorHumano(contenido);
        if (pideHumano) {
            conv.setEstado(WhatsAppConversacion.EstadoConversacion.ESPERANDO_AGENTE);
            conv.setAtendidoPor(WhatsAppConversacion.TipoAtencion.HUMANO);
            conv.setBotSilenciado(true);
            conversacionRepo.save(conv);

            // Notificar al usuario que un agente humano lo atenderá
            enviarMensajeTexto(from, "👋 *Transferencia a Operador Humano*\n\nHemos canalizado tu consulta a nuestro equipo de atención humana. En unos momentos uno de nuestros operadores te responderá directamente por aquí. Por favor aguarda un momento.", activePhoneId, true);
            return;
        }

        conversacionRepo.save(conv);

        // Si la conversación está en atención humana o el bot fue pausado/silenciado para este chat:
        if (conv.isBotSilenciado() || conv.getAtendidoPor() == WhatsAppConversacion.TipoAtencion.HUMANO) {
            log.info("Chat {} configurado para atención HUMANA o bot silenciado. No se ejecuta respuesta automática del bot.", from);
            return;
        }

        // Procesar en el flujo guiado de WhatsApp (Registro de compras, tickets, o chatbot IA)
        whatsAppFlowService.procesarMensajeAsync(saved);
    }

    private boolean esPeticionOperadorHumano(String contenido) {
        if (contenido == null || contenido.isBlank()) return false;
        String lower = contenido.toLowerCase(Locale.ROOT).trim();
        return lower.matches(".*\\b(humano|asesor|asesora|operador|operadora|agente|persona|hablar con alguien|atencion al cliente|soporte humano|atencion humana)\\b.*");
    }

    private void procesarStatus(JsonNode status) {
        String waId = status.path("id").asText();
        String statusStr = status.path("status").asText();
        if (waId == null || waId.isBlank()) return;

        mensajeRepo.findByWaMessageId(waId).ifPresent(m -> {
            EstadoMensaje nuevoEstado = switch (statusStr) {
                case "sent" -> EstadoMensaje.ENVIADO;
                case "delivered" -> EstadoMensaje.ENTREGADO;
                case "read" -> EstadoMensaje.LEIDO;
                case "failed" -> EstadoMensaje.FALLIDO;
                default -> m.getEstado();
            };
            m.setEstado(nuevoEstado);
            mensajeRepo.save(m);
            log.info("Estado de mensaje WA {} actualizado a {}", waId, nuevoEstado);
        });
    }

    // ── Conversaciones para el panel ─────────────────────────────
    public List<ConversacionResumen> obtenerConversaciones() {
        List<String> contactos = mensajeRepo.findContactNumbers();
        List<ConversacionResumen> result = new ArrayList<>();

        Map<String, WhatsAppConversacion> convMap = new HashMap<>();
        for (WhatsAppConversacion c : conversacionRepo.findAll()) {
            if (c.getTelefono() != null) {
                convMap.put(c.getTelefono(), c);
            }
        }

        for (String numero : contactos) {
            mensajeRepo.findLastMessageWithContact(numero).ifPresent(last -> {
                long noLeidos = mensajeRepo.countUnread(numero);
                String preview = last.getContenido() != null
                        ? (last.getContenido().length() > 60 ? last.getContenido().substring(0, 60) + "..." : last.getContenido())
                        : "[" + last.getTipo().name().toLowerCase() + "]";

                WhatsAppConversacion conv = convMap.get(numero);

                // Resolución exhaustiva y persistente del nombre del contacto
                String nombre = (conv != null && conv.getNombreContacto() != null && !conv.getNombreContacto().isBlank())
                        ? conv.getNombreContacto()
                        : null;

                if (nombre == null || nombre.isBlank()) {
                    nombre = mensajeRepo.findUltimoNombreContacto(numero).orElse(null);
                    if (nombre == null || nombre.isBlank()) {
                        String celCorto = numero.length() > 8 ? numero.substring(numero.length() - 8) : numero;
                        if (registroClienteRepo != null) {
                            nombre = registroClienteRepo.findNombrePorCelular(celCorto).orElse(null);
                        }
                    }

                    // Si se descubrió el nombre, guardarlo en BD para que no se vuelva a perder
                    if (nombre != null && !nombre.isBlank()) {
                        if (conv == null) {
                            conv = WhatsAppConversacion.builder()
                                    .telefono(numero)
                                    .nombreContacto(nombre)
                                    .estado(WhatsAppConversacion.EstadoConversacion.ABIERTA)
                                    .atendidoPor(WhatsAppConversacion.TipoAtencion.BOT)
                                    .botSilenciado(false)
                                    .build();
                        } else {
                            conv.setNombreContacto(nombre);
                        }
                        conv = conversacionRepo.save(conv);
                        convMap.put(numero, conv);
                    }
                }

                String estado = (conv != null && conv.getEstado() != null)
                        ? conv.getEstado().name()
                        : WhatsAppConversacion.EstadoConversacion.ABIERTA.name();
                String atendidoPor = (conv != null && conv.getAtendidoPor() != null)
                        ? conv.getAtendidoPor().name()
                        : WhatsAppConversacion.TipoAtencion.BOT.name();
                boolean botSilenciado = conv != null && conv.isBotSilenciado();
                String operador = conv != null ? conv.getOperadorAsignado() : null;

                result.add(new ConversacionResumen(
                        numero,
                        nombre != null && !nombre.isBlank() ? nombre : last.getNombreContacto(),
                        preview,
                        last.getCreatedAt(),
                        noLeidos,
                        last.isEsRespuestaChatbot(),
                        estado,
                        atendidoPor,
                        botSilenciado,
                        operador
                ));
            });
        }
        result.sort(Comparator.comparing(ConversacionResumen::fechaUltimo, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    @Transactional
    public WhatsAppConversacion actualizarNombreContacto(String numero, String nuevoNombre) {
        WhatsAppConversacion conv = conversacionRepo.findById(numero)
                .orElseGet(() -> WhatsAppConversacion.builder()
                        .telefono(numero)
                        .estado(WhatsAppConversacion.EstadoConversacion.ABIERTA)
                        .atendidoPor(WhatsAppConversacion.TipoAtencion.BOT)
                        .botSilenciado(false)
                        .build());
        String clean = (nuevoNombre != null && !nuevoNombre.isBlank()) ? nuevoNombre.trim() : null;
        conv.setNombreContacto(clean);
        WhatsAppConversacion saved = conversacionRepo.save(conv);
        if (clean != null) {
            mensajeRepo.actualizarNombreContacto(numero, clean);
        }
        return saved;
    }

    @Transactional
    public List<MensajeWhatsapp> obtenerHistorial(String numero) {
        // Al consultar el historial, marcar de inmediato los mensajes como leídos
        mensajeRepo.marcarMensajesComoLeidos(numero);
        return mensajeRepo.findConversacion(numero);
    }

    @Transactional
    public void marcarComoLeidos(String numero) {
        mensajeRepo.marcarMensajesComoLeidos(numero);
    }

    @Transactional
    public WhatsAppConversacion actualizarEstadoConversacion(String numero, WhatsAppConversacion.EstadoConversacion estado) {
        WhatsAppConversacion conv = conversacionRepo.findById(numero)
                .orElseGet(() -> WhatsAppConversacion.builder()
                        .telefono(numero)
                        .estado(WhatsAppConversacion.EstadoConversacion.ABIERTA)
                        .atendidoPor(WhatsAppConversacion.TipoAtencion.BOT)
                        .botSilenciado(false)
                        .build());
        conv.setEstado(estado);
        return conversacionRepo.save(conv);
    }

    @Transactional
    public WhatsAppConversacion actualizarAtencionConversacion(String numero, ActualizarAtencionConversacionRequest req) {
        WhatsAppConversacion conv = conversacionRepo.findById(numero)
                .orElseGet(() -> WhatsAppConversacion.builder()
                        .telefono(numero)
                        .estado(WhatsAppConversacion.EstadoConversacion.ABIERTA)
                        .atendidoPor(WhatsAppConversacion.TipoAtencion.BOT)
                        .botSilenciado(false)
                        .build());

        if (req.atendidoPor() != null && !req.atendidoPor().isBlank()) {
            try {
                conv.setAtendidoPor(WhatsAppConversacion.TipoAtencion.valueOf(req.atendidoPor().toUpperCase()));
            } catch (Exception ignored) {}
        }
        if (req.botSilenciado() != null) {
            conv.setBotSilenciado(req.botSilenciado());
        }
        if (req.operador() != null) {
            conv.setOperadorAsignado(req.operador());
        }
        return conversacionRepo.save(conv);
    }

    /**
     * Retorna los mensajes entrantes nuevos desde un ID específico para el polling de notificaciones en tiempo real,
     * junto con el ID máximo actual y el conteo de no leídos.
     */
    public Map<String, Object> obtenerMensajesRecientes(Long ultimoId) {
        Long maxId = mensajeRepo.findMaxId();
        long totalNoLeidos = mensajeRepo.countTotalUnread();

        if (ultimoId == null || ultimoId <= 0) {
            return Map.of(
                    "ultimoId", maxId != null ? maxId : 0L,
                    "totalNoLeidos", totalNoLeidos,
                    "nuevos", List.of()
            );
        }

        List<MensajeWhatsapp> nuevos = mensajeRepo.findNuevosEntrantes(ultimoId);
        return Map.of(
                "ultimoId", maxId != null ? maxId : ultimoId,
                "totalNoLeidos", totalNoLeidos,
                "nuevos", nuevos
        );
    }

    // ── Helpers ──────────────────────────────────────────────────
    private TipoMensaje parseTipo(String type) {
        if (type == null) return TipoMensaje.DESCONOCIDO;
        return switch (type.toLowerCase()) {
            case "text" -> TipoMensaje.TEXTO;
            case "image" -> TipoMensaje.IMAGEN;
            case "audio", "voice" -> TipoMensaje.AUDIO;
            case "video" -> TipoMensaje.VIDEO;
            case "document" -> TipoMensaje.DOCUMENTO;
            case "sticker" -> TipoMensaje.STICKER;
            case "location" -> TipoMensaje.UBICACION;
            case "reaction" -> TipoMensaje.REACCION;
            case "template" -> TipoMensaje.TEMPLATE;
            case "interactive" -> TipoMensaje.INTERACTIVO;
            default -> TipoMensaje.DESCONOCIDO;
        };
    }

    private String extractContenido(JsonNode msg, TipoMensaje tipo) {
        return switch (tipo) {
            case TEXTO -> msg.path("text").path("body").asText("");
            case IMAGEN -> {
                String caption = msg.path("image").path("caption").asText("");
                yield caption.isBlank() ? "[Imagen]" : "[Imagen] " + caption;
            }
            case AUDIO -> "[Nota de voz / Audio]";
            case VIDEO -> {
                String caption = msg.path("video").path("caption").asText("");
                yield caption.isBlank() ? "[Video]" : "[Video] " + caption;
            }
            case DOCUMENTO -> {
                String filename = msg.path("document").path("filename").asText("");
                yield filename.isBlank() ? "[Documento adjunto]" : "[Documento: " + filename + "]";
            }
            case STICKER -> "[Sticker]";
            case UBICACION -> {
                JsonNode loc = msg.path("location");
                double lat = loc.path("latitude").asDouble();
                double lon = loc.path("longitude").asDouble();
                String name = loc.path("name").asText("");
                yield "[Ubicación: " + (name.isBlank() ? "" : name + " ") + lat + ", " + lon + "]";
            }
            case REACCION -> {
                String emoji = msg.path("reaction").path("emoji").asText("");
                yield "[Reacción: " + emoji + "]";
            }
            case INTERACTIVO -> {
                JsonNode interactive = msg.path("interactive");
                String iType = interactive.path("type").asText();
                yield switch (iType) {
                    case "button_reply" -> {
                        String bId = interactive.path("button_reply").path("id").asText("");
                        String bTitle = interactive.path("button_reply").path("title").asText("");
                        yield !bId.isBlank() ? bId : (!bTitle.isBlank() ? bTitle : "[Botón pulsado]");
                    }
                    case "list_reply" -> {
                        String lId = interactive.path("list_reply").path("id").asText("");
                        String lTitle = interactive.path("list_reply").path("title").asText("");
                        yield !lId.isBlank() ? lId : (!lTitle.isBlank() ? lTitle : "[Opción de lista]");
                    }
                    default -> "[Interactivo]";
                };
            }

            default -> "[Mensaje]";
        };
    }

    private String extractMediaId(JsonNode msg, TipoMensaje tipo) {
        return switch (tipo) {
            case IMAGEN -> msg.path("image").path("id").asText(null);
            case AUDIO -> msg.path("audio").path("id").asText(null);
            case VIDEO -> msg.path("video").path("id").asText(null);
            case DOCUMENTO -> msg.path("document").path("id").asText(null);
            case STICKER -> msg.path("sticker").path("id").asText(null);
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private String extractSentMessageId(Map<?, ?> responseBody) {
        if (responseBody == null) return null;
        Object messages = responseBody.get("messages");
        if (messages instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> map) {
                Object idObj = map.get("id");
                return idObj != null ? idObj.toString() : null;
            }
        }
        return null;
    }
}

