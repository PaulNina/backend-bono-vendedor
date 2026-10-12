package com.ninabit.bono.modules.whatsapp.dto;

import java.time.LocalDateTime;

public record ConversacionResumen(
        String numero,
        String nombreContacto,
        String ultimoMensaje,
        LocalDateTime fechaUltimo,
        long noLeidos,
        boolean ultimaEsChatbot,
        String estado,            // ABIERTA, EN_REGISTRO, ESPERANDO_AGENTE, FINALIZADA
        String atendidoPor,       // BOT, HUMANO
        boolean botSilenciado,
        String operadorAsignado
) {}
