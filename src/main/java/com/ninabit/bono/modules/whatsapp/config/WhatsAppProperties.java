package com.ninabit.bono.modules.whatsapp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "whatsapp")
@Data
public class WhatsAppProperties {
    private String verifyToken = "SKYWORTH_WA_VERIFY_2026";
    private String accessToken = "";
    private String phoneNumberId = "";
    private String apiVersion = "v25.0";
    private String apiBaseUrl = "https://graph.facebook.com";
    private String webhookPublicUrl = "https://bono.skyworth.bo/api/webhook/whatsapp";

    public String getMessagesUrl() {
        return apiBaseUrl + "/" + apiVersion + "/" + phoneNumberId + "/messages";
    }
}
