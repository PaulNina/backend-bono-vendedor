package com.ninabit.bono.modules.whatsapp.service;

@FunctionalInterface
public interface WhatsAppSender {
    void enviar(String to, String texto, String fromPhoneNumberId, boolean esChatbot);

    default void enviar(String to, String texto, boolean esChatbot) {
        enviar(to, texto, null, esChatbot);
    }
}

