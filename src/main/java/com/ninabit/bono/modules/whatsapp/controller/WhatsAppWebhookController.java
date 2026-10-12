package com.ninabit.bono.modules.whatsapp.controller;

import com.ninabit.bono.modules.whatsapp.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookController {

    private final WhatsAppService whatsAppService;

    /** 
     * Meta verifica el webhook con GET (Handshake inicial)
     * URL: https://bono.skyworth.bo/api/webhook/whatsapp
     */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verificarWebhook(
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(value = "hub.challenge", required = false) String challenge) {

        // Si se abre directamente desde el navegador para verificar estado
        if (mode == null && verifyToken == null && challenge == null) {
            log.info("Comprobación GET de salud del webhook de WhatsApp.");
            return ResponseEntity.ok("OK - Webhook de WhatsApp Skyworth activo y listo para Meta Cloud API.");
        }

        String expectedToken = whatsAppService.getEffectiveVerifyToken();
        if (expectedToken != null) expectedToken = expectedToken.trim();
        else expectedToken = "";
        String receivedToken = verifyToken != null ? verifyToken.trim() : "";

        if ("subscribe".equals(mode) && !expectedToken.isEmpty() && expectedToken.equals(receivedToken)) {
            log.info("WhatsApp webhook verificado exitosamente por Meta. Challenge: {}", challenge);
            return ResponseEntity.ok(challenge != null ? challenge : "");
        }

        log.warn("WhatsApp webhook: rechazo de verificación. Mode: {}, Token recibido: '{}', Token esperado: '{}'",
                mode, receivedToken, expectedToken);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Token de verificación incorrecto");
    }

    /** 
     * Meta envía mensajes y actualizaciones de estado con POST
     */
    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, "*/*"}, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> recibirMensaje(@RequestBody String payload) {
        log.info("Webhook WA POST recibido (bytes: {})", payload != null ? payload.length() : 0);
        try {
            whatsAppService.procesarWebhookEntrante(payload);
        } catch (Exception e) {
            log.error("Error al procesar webhook de WhatsApp: {}", e.getMessage(), e);
        }
        // Meta requiere status 200 inmediato para confirmar recepción
        return ResponseEntity.ok("EVENT_RECEIVED");
    }
}
