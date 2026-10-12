package com.ninabit.bono.modules.whatsapp.dto;

import java.util.List;

public record MasivoTemplateRequest(
        String templateName,
        String headerImageUrl,
        List<DestinatarioTemplate> destinatarios
) {
    public record DestinatarioTemplate(
            String to,
            List<String> params
    ) {}
}
