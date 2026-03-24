package com.ninabit.bono.modules.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final EmailConfigService emailConfigService;
    private final com.ninabit.bono.modules.configuracion.ConfiguracionGlobalRepository configRepository;

    private String getMailFrom() {
        return configRepository.findById("smtp.username")
                .map(c -> c.getValor())
                .orElse("noreply@skyworth.bo"); // Generic domain fallback
    }

    public void sendEmail(String to, String subject, String text) {
        try {
            JavaMailSender mailSender = emailConfigService.createMailSender();
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(getMailFrom());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("Correo enviado exitosamente a {}", to);
        } catch (Exception e) {
            log.error("Error al enviar correo a {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("No se pudo enviar el correo electrónico. Verifica la configuración SMTP.");
        }
    }

    public void sendEmailWithAttachment(String to, String subject, String text, String attachmentName,
            byte[] attachment) {
        try {
            JavaMailSender mailSender = emailConfigService.createMailSender();
            jakarta.mail.internet.MimeMessage message = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper = new org.springframework.mail.javamail.MimeMessageHelper(
                    message, true);

            helper.setFrom(getMailFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text);
            helper.addAttachment(attachmentName, new org.springframework.core.io.ByteArrayResource(attachment));

            mailSender.send(message);
            log.info("Correo con adjunto enviado exitosamente a {}", to);
        } catch (Exception e) {
            log.error("Error al enviar correo con adjunto a {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("No se pudo enviar el correo con adjunto. Verifica la configuración SMTP.");
        }
    }
}
