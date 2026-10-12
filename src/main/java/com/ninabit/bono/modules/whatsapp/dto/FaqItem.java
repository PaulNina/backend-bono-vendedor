package com.ninabit.bono.modules.whatsapp.dto;

import java.util.List;

public record FaqItem(String pregunta, String respuesta, List<String> keywords) {}
