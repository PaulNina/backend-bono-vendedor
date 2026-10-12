package com.ninabit.bono.modules.whatsapp.dto;

import java.util.List;

public record MasivoTemplateResponse(
        int total,
        int exitosos,
        int fallidos,
        List<TemplateSendResult> resultados
) {}
