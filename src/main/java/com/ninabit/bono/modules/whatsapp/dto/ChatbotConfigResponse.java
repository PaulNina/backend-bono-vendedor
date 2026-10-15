package com.ninabit.bono.modules.whatsapp.dto;

import com.ninabit.bono.modules.whatsapp.entity.ChatbotConfig.ProveedorIA;
import java.time.LocalDateTime;
import java.util.List;

public record ChatbotConfigResponse(
        boolean activo,
        ProveedorIA proveedorIa,
        String apiKeyEnmascarado,
        String modelo,
        String baseUrl,
        String promptSistema,
        Double temperatura,
        Integer maxTokens,
        Double umbralSimilitudFaq,
        List<FaqItem> faqs,
        LocalDateTime updatedAt,
        String webhookPublicUrl,
        String verifyToken,
        String waAccessTokenEnmascarado,
        String waPhoneNumberId,
        String waNumero1,
        String waPhoneNumberId2,
        String waNumero2,
        String waVerifyToken,
        String waHeaderImageUrl,
        com.ninabit.bono.modules.whatsapp.entity.ChatbotConfig.ModoRegistroWa modoRegistroWa,
        Integer cantidadFotosRequeridas,
        String mensajeRedireccionWeb
) {}

