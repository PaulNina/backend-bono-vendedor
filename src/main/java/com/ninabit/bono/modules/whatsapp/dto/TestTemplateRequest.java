package com.ninabit.bono.modules.whatsapp.dto;

import java.util.List;

public record TestTemplateRequest(
        String to,
        String templateName,
        String headerImageUrl,
        List<String> params
) {}
