package com.ninabit.bono.modules.whatsapp.dto;

public record TemplateSendResult(
        boolean success,
        String to,
        String waMessageId,
        String error
) {}
