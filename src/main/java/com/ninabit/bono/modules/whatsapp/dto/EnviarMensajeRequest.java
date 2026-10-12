package com.ninabit.bono.modules.whatsapp.dto;

public record EnviarMensajeRequest(String to, String texto, String fromPhoneNumberId) {
    public EnviarMensajeRequest(String to, String texto) {
        this(to, texto, null);
    }
}

