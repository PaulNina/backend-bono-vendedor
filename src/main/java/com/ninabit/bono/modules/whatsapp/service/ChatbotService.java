package com.ninabit.bono.modules.whatsapp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninabit.bono.modules.whatsapp.dto.ChatbotConfigRequest;
import com.ninabit.bono.modules.whatsapp.dto.ChatbotConfigResponse;
import com.ninabit.bono.modules.whatsapp.dto.FaqItem;
import com.ninabit.bono.modules.whatsapp.entity.ChatbotConfig;
import com.ninabit.bono.modules.whatsapp.entity.ChatbotConfig.ProveedorIA;
import com.ninabit.bono.modules.whatsapp.entity.MensajeWhatsapp;
import com.ninabit.bono.modules.whatsapp.repository.ChatbotConfigRepository;
import com.ninabit.bono.modules.whatsapp.repository.MensajeWhatsappRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotService {

    private final ChatbotConfigRepository configRepo;
    private final MensajeWhatsappRepository mensajeRepo;
    private final ObjectMapper objectMapper;
    private final com.ninabit.bono.modules.whatsapp.config.WhatsAppProperties props;

    // ── Obtener / guardar config ──────────────────────────────────

    public ChatbotConfigResponse obtenerConfig() {
        ChatbotConfig cfg = configRepo.findFirstByOrderByIdAsc().orElse(defaultConfig());
        return toResponse(cfg);
    }

    @Transactional
    public ChatbotConfigResponse guardarConfig(ChatbotConfigRequest req) {
        ChatbotConfig cfg = configRepo.findFirstByOrderByIdAsc().orElse(new ChatbotConfig());
        cfg.setId(1L);
        cfg.setActivo(req.activo());
        cfg.setProveedorIa(req.proveedorIa());
        cfg.setModelo(req.modelo());
        cfg.setBaseUrl(req.baseUrl());
        // Solo actualizar apiKey si viene una clave real nueva y no una enmascarada con asteriscos
        if (req.apiKey() != null && !req.apiKey().isBlank() && !req.apiKey().contains("*")) {
            cfg.setApiKey(req.apiKey().trim());
        }
        cfg.setPromptSistema(req.promptSistema());
        cfg.setTemperatura(req.temperatura() != null ? req.temperatura() : 0.3);
        cfg.setMaxTokens(req.maxTokens() != null ? req.maxTokens() : 500);
        cfg.setUmbralSimilitudFaq(req.umbralSimilitudFaq() != null ? req.umbralSimilitudFaq() : 0.7);
        if (req.faqs() != null) {
            try { cfg.setFaqsJson(objectMapper.writeValueAsString(req.faqs())); }
            catch (Exception e) { cfg.setFaqsJson("[]"); }
        }
        // Solo actualizar waAccessToken si viene una clave real nueva y no una máscara con asteriscos
        if (req.waAccessToken() != null && !req.waAccessToken().isBlank() && !req.waAccessToken().contains("*")) {
            cfg.setWaAccessToken(req.waAccessToken().trim());
        }
        if (req.waPhoneNumberId() != null) {
            cfg.setWaPhoneNumberId(req.waPhoneNumberId().trim());
        }
        if (req.waNumero1() != null) {
            cfg.setWaNumero1(req.waNumero1().trim());
        }
        if (req.waPhoneNumberId2() != null) {
            cfg.setWaPhoneNumberId2(req.waPhoneNumberId2().trim());
        }
        if (req.waNumero2() != null) {
            cfg.setWaNumero2(req.waNumero2().trim());
        }
        if (req.waVerifyToken() != null && !req.waVerifyToken().isBlank()) {
            cfg.setWaVerifyToken(req.waVerifyToken().trim());
        }
        if (req.waHeaderImageUrl() != null) {
            cfg.setWaHeaderImageUrl(req.waHeaderImageUrl().trim());
        }
        if (req.modoRegistroWa() != null) {
            cfg.setModoRegistroWa(req.modoRegistroWa());
        }
        if (req.cantidadFotosRequeridas() != null) {
            cfg.setCantidadFotosRequeridas(req.cantidadFotosRequeridas());
        }
        if (req.mensajeRedireccionWeb() != null) {
            cfg.setMensajeRedireccionWeb(req.mensajeRedireccionWeb().trim());
        }
        return toResponse(configRepo.save(cfg));
    }

    public List<FaqItem> obtenerFaqs() {
        return configRepo.findFirstByOrderByIdAsc()
                .map(c -> parseFaqs(c.getFaqsJson()))
                .orElse(List.of());
    }

    // ── Responder automáticamente (async) ────────────────────────

    @Async
    public void responderAsync(MensajeWhatsapp mensaje) {
        try {
            ChatbotConfig cfg = configRepo.findFirstByOrderByIdAsc().orElse(null);
            if (cfg == null || !cfg.isActivo()) return;
            if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
                log.warn("Chatbot activo pero sin API key configurado");
                return;
            }
            String respuesta = generarRespuesta(cfg, mensaje);
            if (respuesta != null && !respuesta.isBlank()) {
                // Responder usando el mismo número/phone_number_id por el que ingresó el mensaje
                String targetPhoneId = mensaje.getPhoneNumberId();
                enviarRespuestaWA(cfg, mensaje.getFromNumber(), respuesta, targetPhoneId);
            }
        } catch (Exception e) {
            log.error("Error en chatbot para mensaje {}: {}", mensaje.getId(), e.getMessage(), e);
        }
    }

    /** Probar respuesta sin enviar WhatsApp (para el panel de configuración) */
    public String probar(String pregunta) {
        ChatbotConfig cfg = configRepo.findFirstByOrderByIdAsc().orElse(null);
        if (cfg == null) return "[Chatbot no configurado]";
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) return "[API key no configurada]";
        if (cfg.getApiKey().contains("*")) {
            return "[Error: La API key en la base de datos está corrupta con asteriscos. Por favor ingresa tu API key completa nuevamente en la configuración y guarda cambios]";
        }

        MensajeWhatsapp simulado = MensajeWhatsapp.builder()
                .fromNumber("test_simulator")
                .contenido(pregunta)
                .build();
        try {
            String resp = generarRespuesta(cfg, simulado);
            return resp != null ? resp : "[Sin respuesta generada por el proveedor]";
        } catch (Exception e) {
            return "[Error del proveedor IA: " + e.getMessage() + "]";
        }
    }

    // ── Lógica de respuesta ───────────────────────────────────────

    private String generarRespuesta(ChatbotConfig cfg, MensajeWhatsapp mensaje) {
        String pregunta = mensaje.getContenido();
        if (pregunta == null || pregunta.isBlank()) return null;

        // 1. Buscar en FAQs primero
        String respuestaFaq = buscarEnFaqs(cfg, pregunta);
        if (respuestaFaq != null) {
            log.info("Chatbot: respondiendo con FAQ para '{}'", pregunta.substring(0, Math.min(30, pregunta.length())));
            return respuestaFaq;
        }

        // 2. Llamar a la IA
        List<MensajeWhatsapp> historial = mensajeRepo.findConversacion(mensaje.getFromNumber());
        // Filtrar el mensaje actual si ya fue guardado en el historial
        List<MensajeWhatsapp> historialPrevio = historial.stream()
                .filter(m -> m.getId() == null || !m.getId().equals(mensaje.getId()))
                .toList();

        return switch (cfg.getProveedorIa()) {
            case OPENAI -> llamarOpenAI(cfg, historialPrevio, pregunta);
            case GEMINI -> llamarGemini(cfg, historialPrevio, pregunta);
            case DEEPSEEK -> llamarDeepSeek(cfg, historialPrevio, pregunta);
        };
    }

    private String buscarEnFaqs(ChatbotConfig cfg, String pregunta) {
        List<FaqItem> faqs = parseFaqs(cfg.getFaqsJson());
        if (faqs.isEmpty()) return null;
        double umbral = cfg.getUmbralSimilitudFaq() != null ? cfg.getUmbralSimilitudFaq() : 0.7;

        String preguntaNorm = normalizar(pregunta);
        FaqItem mejorMatch = null;
        double mejorScore = 0;

        for (FaqItem faq : faqs) {
            // Verificar keywords primero
            List<String> keywords = faq.keywords() != null ? faq.keywords() : List.of();
            long keyHits = keywords.stream().filter(k -> preguntaNorm.contains(normalizar(k))).count();
            double keyScore = keywords.isEmpty() ? 0 : (double) keyHits / keywords.size();

            // Similitud con la pregunta de la FAQ
            double faqScore = jaccardSimilarity(preguntaNorm, normalizar(faq.pregunta()));

            double score = Math.max(keyScore, faqScore);
            if (score > mejorScore) {
                mejorScore = score;
                mejorMatch = faq;
            }
        }

        if (mejorMatch != null && mejorScore >= umbral) {
            return mejorMatch.respuesta();
        }
        return null;
    }

    private String normalizeEndpoint(String baseUrl, String defaultUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return defaultUrl;
        }
        String url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/chat/completions")) {
            return url;
        }
        return url + "/chat/completions";
    }

    private String llamarOpenAI(ChatbotConfig cfg, List<MensajeWhatsapp> historial, String pregunta) {
        String endpoint = normalizeEndpoint(cfg.getBaseUrl(), "https://api.openai.com/v1/chat/completions");
        String modelo = (cfg.getModelo() != null && !cfg.getModelo().isBlank())
                ? cfg.getModelo().trim()
                : "gpt-4o-mini";
        return llamarOpenAICompatible(endpoint, modelo, cfg, historial, pregunta, "OpenAI");
    }

    private String llamarDeepSeek(ChatbotConfig cfg, List<MensajeWhatsapp> historial, String pregunta) {
        String endpoint = normalizeEndpoint(cfg.getBaseUrl(), "https://api.deepseek.com/chat/completions");
        String modelo = (cfg.getModelo() != null && !cfg.getModelo().isBlank())
                ? cfg.getModelo().trim()
                : "deepseek-chat";
        return llamarOpenAICompatible(endpoint, modelo, cfg, historial, pregunta, "DeepSeek");
    }

    private String llamarOpenAICompatible(String endpoint, String modelo, ChatbotConfig cfg,
                                          List<MensajeWhatsapp> historial, String pregunta, String providerName) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (cfg.getPromptSistema() != null && !cfg.getPromptSistema().isBlank()) {
            messages.add(Map.of("role", "system", "content", cfg.getPromptSistema()));
        }
        // Últimos 6 mensajes del historial
        historial.stream().skip(Math.max(0, historial.size() - 6)).forEach(m -> {
            String role = m.getDireccion() == MensajeWhatsapp.DireccionMensaje.ENTRADA ? "user" : "assistant";
            String cont = m.getContenido() != null ? m.getContenido() : "";
            if (!cont.isBlank()) {
                messages.add(Map.of("role", role, "content", cont));
            }
        });
        messages.add(Map.of("role", "user", "content", pregunta));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelo);
        body.put("messages", messages);
        if (modelo == null || !modelo.toLowerCase().contains("reasoner")) {
            body.put("temperature", cfg.getTemperatura() != null ? cfg.getTemperatura() : 0.3);
        }
        body.put("max_tokens", cfg.getMaxTokens() != null ? cfg.getMaxTokens() : 500);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(cfg.getApiKey());

        try {
            RestTemplate rt = new RestTemplate();
            ResponseEntity<String> resp = rt.postForEntity(
                    endpoint,
                    new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode messageNode = root.path("choices").get(0).path("message");
            String ans = messageNode.path("content").asText();
            if (ans == null || ans.isBlank()) {
                ans = messageNode.path("reasoning_content").asText();
            }
            return ans;
        } catch (org.springframework.web.client.HttpStatusCodeException httpEx) {
            String errDetail = providerName + " error (" + httpEx.getStatusCode() + "): " + httpEx.getResponseBodyAsString();
            log.error(errDetail);
            throw new RuntimeException(errDetail);
        } catch (Exception e) {
            log.error("Error llamando {}: {}", providerName, e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    private String llamarGemini(ChatbotConfig cfg, List<MensajeWhatsapp> historial, String pregunta) {
        // En Gemini, las conversaciones deben alternar estrictamente: user -> model -> user
        List<Map<String, Object>> contents = new ArrayList<>();

        // Extraer los últimos 6 mensajes válidos con contenido no vacío
        List<MensajeWhatsapp> ultimos = historial.stream()
                .filter(m -> m.getContenido() != null && !m.getContenido().isBlank())
                .skip(Math.max(0, historial.size() - 6))
                .toList();

        String ultimoRol = null;
        StringBuilder bufferTexto = new StringBuilder();

        for (MensajeWhatsapp m : ultimos) {
            String rol = m.getDireccion() == MensajeWhatsapp.DireccionMensaje.ENTRADA ? "user" : "model";
            if (rol.equals(ultimoRol)) {
                // Mismo rol consecutivo: concatenar texto para mantener alternancia estricta
                bufferTexto.append("\n").append(m.getContenido());
            } else {
                if (ultimoRol != null) {
                    contents.add(Map.of("role", ultimoRol, "parts", List.of(Map.of("text", bufferTexto.toString()))));
                }
                ultimoRol = rol;
                bufferTexto = new StringBuilder(m.getContenido());
            }
        }
        if (ultimoRol != null && !bufferTexto.isEmpty()) {
            contents.add(Map.of("role", ultimoRol, "parts", List.of(Map.of("text", bufferTexto.toString()))));
        }

        // Gemini exige que la conversación empiece con rol 'user'. Si empieza con 'model', quitarlo.
        while (!contents.isEmpty() && "model".equals(contents.get(0).get("role"))) {
            contents.remove(0);
        }

        // Agregar la pregunta actual del usuario
        if (!contents.isEmpty() && "user".equals(contents.get(contents.size() - 1).get("role"))) {
            // El último ya era user, concatenamos
            Map<String, Object> ultimoTurno = contents.remove(contents.size() - 1);
            @SuppressWarnings("unchecked")
            List<Map<String, String>> parts = (List<Map<String, String>>) ultimoTurno.get("parts");
            String previo = parts != null && !parts.isEmpty() ? parts.get(0).get("text") : "";
            contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", previo + "\n" + pregunta))));
        } else {
            contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", pregunta))));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", contents);
        if (cfg.getPromptSistema() != null && !cfg.getPromptSistema().isBlank()) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", cfg.getPromptSistema()))));
        }
        Map<String, Object> genConfig = new LinkedHashMap<>();
        genConfig.put("temperature", cfg.getTemperatura() != null ? cfg.getTemperatura() : 0.3);
        genConfig.put("maxOutputTokens", cfg.getMaxTokens() != null ? cfg.getMaxTokens() : 500);
        body.put("generationConfig", genConfig);

        String modelo = cfg.getModelo() != null && !cfg.getModelo().isBlank() ? cfg.getModelo().trim() : "gemini-3.8-flash";
        String baseUri = (cfg.getBaseUrl() != null && !cfg.getBaseUrl().isBlank())
                ? cfg.getBaseUrl().trim().replaceAll("/+$", "")
                : "https://generativelanguage.googleapis.com";
        String url = baseUri + "/v1beta/models/" + modelo + ":generateContent?key=" + cfg.getApiKey();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", cfg.getApiKey());

        try {
            RestTemplate rt = new RestTemplate();
            ResponseEntity<String> resp = rt.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                return candidates.get(0).path("content").path("parts").get(0).path("text").asText();
            }
            log.warn("Gemini retornó sin candidatos: {}", resp.getBody());
            return null;
        } catch (org.springframework.web.client.HttpStatusCodeException httpEx) {
            String errDetail = "Gemini API error (" + httpEx.getStatusCode() + "): " + httpEx.getResponseBodyAsString();
            log.error(errDetail);
            throw new RuntimeException(errDetail);
        } catch (Exception e) {
            log.error("Error llamando Gemini: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    // ── Enviar respuesta WA (inyección late para evitar dependencia circular) ─
    private WhatsAppSender whatsAppSender;

    public void setWhatsAppSender(WhatsAppSender sender) {
        this.whatsAppSender = sender;
    }

    private void enviarRespuestaWA(ChatbotConfig cfg, String to, String texto, String fromPhoneNumberId) {
        if (whatsAppSender != null) {
            whatsAppSender.enviar(to, texto, fromPhoneNumberId, true);
        } else {
            log.warn("WhatsAppSender no configurado, respuesta chatbot no enviada");
        }
    }

    // ── Utilidades ────────────────────────────────────────────────
    private List<FaqItem> parseFaqs(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Error parseando FAQs JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private String normalizar(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[áàä]", "a").replaceAll("[éèë]", "e")
                .replaceAll("[íìï]", "i").replaceAll("[óòö]", "o")
                .replaceAll("[úùü]", "u").replaceAll("[ñ]", "n")
                .replaceAll("[^a-z0-9\\s]", " ").trim();
    }

    private double jaccardSimilarity(String a, String b) {
        Set<String> setA = new HashSet<>(Arrays.asList(a.split("\\s+")));
        Set<String> setB = new HashSet<>(Arrays.asList(b.split("\\s+")));
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    private ChatbotConfig defaultConfig() {
        ChatbotConfig c = new ChatbotConfig();
        c.setId(1L);
        c.setActivo(false);
        c.setProveedorIa(ProveedorIA.OPENAI);
        c.setModelo("gpt-4o-mini");
        c.setTemperatura(0.3);
        c.setMaxTokens(500);
        c.setUmbralSimilitudFaq(0.7);
        c.setFaqsJson("[]");
        c.setModoRegistroWa(ChatbotConfig.ModoRegistroWa.PASO_A_PASO);
        c.setCantidadFotosRequeridas(2);
        c.setWaHeaderImageUrl(WhatsAppService.DEFAULT_HEADER_IMAGE);
        c.setMensajeRedireccionWeb("⚡ *Tip:* Para registrar tu equipo y participar en los sorteos por *Bs 100.000*, ingresa a nuestra web oficial:\n👉 https://bono.skyworth.bo/registro-cliente");
        return c;
    }

    private ChatbotConfigResponse toResponse(ChatbotConfig cfg) {
        String keyMasked = cfg.getApiKey() != null && cfg.getApiKey().length() > 8
                ? cfg.getApiKey().substring(0, 4) + "****" + cfg.getApiKey().substring(cfg.getApiKey().length() - 4)
                : (cfg.getApiKey() != null && !cfg.getApiKey().isBlank() ? "****" : "");

        String rawWaToken = (cfg.getWaAccessToken() != null && !cfg.getWaAccessToken().isBlank())
                ? cfg.getWaAccessToken()
                : props.getAccessToken();
        String waTokenMasked = rawWaToken != null && rawWaToken.length() > 10
                ? rawWaToken.substring(0, 6) + "****" + rawWaToken.substring(rawWaToken.length() - 6)
                : (rawWaToken != null && !rawWaToken.isBlank() ? "****" : "");

        String effectivePhone1 = (cfg.getWaPhoneNumberId() != null && !cfg.getWaPhoneNumberId().isBlank())
                ? cfg.getWaPhoneNumberId()
                : props.getPhoneNumberId();

        String effectiveVerify = (cfg.getWaVerifyToken() != null && !cfg.getWaVerifyToken().isBlank())
                ? cfg.getWaVerifyToken()
                : props.getVerifyToken();

        String effectiveHeaderImage = (cfg.getWaHeaderImageUrl() != null && !cfg.getWaHeaderImageUrl().isBlank())
                ? cfg.getWaHeaderImageUrl()
                : WhatsAppService.DEFAULT_HEADER_IMAGE;

        return new ChatbotConfigResponse(
                cfg.isActivo(), cfg.getProveedorIa(), keyMasked, cfg.getModelo(),
                cfg.getBaseUrl(),
                cfg.getPromptSistema(), cfg.getTemperatura(), cfg.getMaxTokens(),
                cfg.getUmbralSimilitudFaq(), parseFaqs(cfg.getFaqsJson()), cfg.getUpdatedAt(),
                props.getWebhookPublicUrl(),
                effectiveVerify,
                waTokenMasked,
                effectivePhone1,
                cfg.getWaNumero1(),
                cfg.getWaPhoneNumberId2(),
                cfg.getWaNumero2(),
                effectiveVerify,
                effectiveHeaderImage,
                cfg.getModoRegistroWa() != null ? cfg.getModoRegistroWa() : ChatbotConfig.ModoRegistroWa.PASO_A_PASO,
                cfg.getCantidadFotosRequeridas() != null ? cfg.getCantidadFotosRequeridas() : 2,
                cfg.getMensajeRedireccionWeb() != null ? cfg.getMensajeRedireccionWeb() : ""
        );
    }
}

