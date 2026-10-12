package com.ninabit.bono.modules.whatsapp.controller;

import com.ninabit.bono.modules.whatsapp.dto.ConversacionResumen;
import com.ninabit.bono.modules.whatsapp.dto.EnviarMensajeRequest;
import com.ninabit.bono.modules.whatsapp.entity.MensajeWhatsapp;
import com.ninabit.bono.modules.whatsapp.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/whatsapp")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
public class WhatsAppAdminController {

    private final WhatsAppService whatsAppService;

    @GetMapping("/conversaciones")
    public ResponseEntity<List<ConversacionResumen>> getConversaciones() {
        return ResponseEntity.ok(whatsAppService.obtenerConversaciones());
    }

    @GetMapping("/conversaciones/{numero}")
    public ResponseEntity<List<MensajeWhatsapp>> getHistorial(@PathVariable String numero) {
        return ResponseEntity.ok(whatsAppService.obtenerHistorial(numero));
    }

    @GetMapping("/mensajes/recientes")
    public ResponseEntity<Map<String, Object>> getMensajesRecientes(@RequestParam(required = false) Long ultimoId) {
        return ResponseEntity.ok(whatsAppService.obtenerMensajesRecientes(ultimoId));
    }

    @PostMapping("/enviar")
    public ResponseEntity<Map<String, String>> enviarMensaje(@RequestBody EnviarMensajeRequest req) {
        whatsAppService.enviarMensajeTexto(req.to(), req.texto(), req.fromPhoneNumberId(), false);
        return ResponseEntity.ok(Map.of("status", "enviado", "to", req.to()));
    }

    @PostMapping("/templates/probar")
    public ResponseEntity<com.ninabit.bono.modules.whatsapp.dto.TemplateSendResult> probarTemplate(
            @RequestBody com.ninabit.bono.modules.whatsapp.dto.TestTemplateRequest req) {
        String img = (req.headerImageUrl() != null && !req.headerImageUrl().isBlank())
                ? req.headerImageUrl()
                : "https://bono.skyworth.bo/logo_skyworth.jpg";
        var resultado = whatsAppService.enviarTemplateConResultado(
                req.to(),
                req.templateName(),
                img,
                req.params()
        );
        return ResponseEntity.ok(resultado);
    }

    @PostMapping("/templates/masivo")
    public ResponseEntity<com.ninabit.bono.modules.whatsapp.dto.MasivoTemplateResponse> enviarMasivo(
            @RequestBody com.ninabit.bono.modules.whatsapp.dto.MasivoTemplateRequest req) {
        var resultado = whatsAppService.enviarTemplatesMasivo(req);
        return ResponseEntity.ok(resultado);
    }

    @PostMapping("/conversaciones/{numero}/leer")
    public ResponseEntity<Map<String, String>> marcarComoLeido(@PathVariable String numero) {
        whatsAppService.marcarComoLeidos(numero);
        return ResponseEntity.ok(Map.of("status", "ok", "numero", numero));
    }

    @PutMapping("/conversaciones/{numero}/estado")
    public ResponseEntity<com.ninabit.bono.modules.whatsapp.entity.WhatsAppConversacion> actualizarEstado(
            @PathVariable String numero,
            @RequestBody com.ninabit.bono.modules.whatsapp.dto.ActualizarEstadoConversacionRequest req) {
        try {
            var nuevoEstado = com.ninabit.bono.modules.whatsapp.entity.WhatsAppConversacion.EstadoConversacion.valueOf(req.estado().toUpperCase());
            return ResponseEntity.ok(whatsAppService.actualizarEstadoConversacion(numero, nuevoEstado));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/conversaciones/{numero}/atencion")
    public ResponseEntity<com.ninabit.bono.modules.whatsapp.entity.WhatsAppConversacion> actualizarAtencion(
            @PathVariable String numero,
            @RequestBody com.ninabit.bono.modules.whatsapp.dto.ActualizarAtencionConversacionRequest req) {
        return ResponseEntity.ok(whatsAppService.actualizarAtencionConversacion(numero, req));
    }

    @PutMapping("/conversaciones/{numero}/nombre")
    public ResponseEntity<com.ninabit.bono.modules.whatsapp.entity.WhatsAppConversacion> actualizarNombre(
            @PathVariable String numero,
            @RequestBody Map<String, String> body) {
        String nuevoNombre = body.get("nombre");
        return ResponseEntity.ok(whatsAppService.actualizarNombreContacto(numero, nuevoNombre));
    }

}
