package com.ninabit.bono.modules.whatsapp.dto;

public record ActualizarAtencionConversacionRequest(
        String atendidoPor,       // "BOT" | "HUMANO"
        Boolean botSilenciado,
        String operador
) {}
