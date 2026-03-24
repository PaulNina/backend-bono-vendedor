package com.ninabit.bono.modules.email;

import com.ninabit.bono.modules.configuracion.ConfiguracionGlobal;
import com.ninabit.bono.modules.configuracion.ConfiguracionGlobalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailConfigService {

    private final ConfiguracionGlobalRepository configuracionGlobalRepository;

    public JavaMailSender createMailSender() {
        Map<String, String> configs = configuracionGlobalRepository.findAll().stream()
                .collect(Collectors.toMap(ConfiguracionGlobal::getClave, ConfiguracionGlobal::getValor));

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        String host = configs.get("smtp.host");
        if (host == null || host.isEmpty()) {
            log.warn("SMTP host is not configured in configuracion_global. Email sending may fail.");
            host = "localhost"; // Safe fallback to avoid NPE, though it will likely fail to connect
        }
        mailSender.setHost(host);
        
        try {
            mailSender.setPort(Integer.parseInt(configs.getOrDefault("smtp.port", "587")));
        } catch (NumberFormatException e) {
            log.warn("Invalid SMTP port configured: {}. Using default 587.", configs.get("smtp.port"));
            mailSender.setPort(587);
        }
        
        mailSender.setUsername(configs.getOrDefault("smtp.username", ""));
        mailSender.setPassword(configs.getOrDefault("smtp.password", ""));

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");

        // Determinar si usar STARTTLS o SSL según el puerto para mayor flexibilidad
        int port = mailSender.getPort();
        if (port == 465) {
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
        }

        return mailSender;
    }
}
