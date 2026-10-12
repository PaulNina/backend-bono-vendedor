package com.ninabit.bono.modules.whatsapp.controller;

import com.ninabit.bono.modules.whatsapp.dto.*;
import com.ninabit.bono.modules.whatsapp.service.ChatbotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/whatsapp/chatbot")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ChatbotAdminController {

    private final ChatbotService chatbotService;

    @GetMapping("/config")
    public ResponseEntity<ChatbotConfigResponse> getConfig() {
        return ResponseEntity.ok(chatbotService.obtenerConfig());
    }

    @PutMapping("/config")
    public ResponseEntity<ChatbotConfigResponse> guardarConfig(@RequestBody ChatbotConfigRequest req) {
        return ResponseEntity.ok(chatbotService.guardarConfig(req));
    }

    @PostMapping("/probar")
    public ResponseEntity<Map<String, String>> probar(@RequestBody ProbarChatbotRequest req) {
        String respuesta = chatbotService.probar(req.pregunta());
        return ResponseEntity.ok(Map.of("respuesta", respuesta));
    }

    @GetMapping("/faqs")
    public ResponseEntity<List<FaqItem>> getFaqs() {
        return ResponseEntity.ok(chatbotService.obtenerFaqs());
    }
}
