package com.ninabit.bono.modules.report;

import com.ninabit.bono.modules.email.EmailService;
import com.ninabit.bono.modules.vendor.Vendedor;
import com.ninabit.bono.modules.vendor.VendedorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VendorNotificationService {

    private final VendedorRepository vendedorRepository;
    private final EmailService emailService;

    /**
     * Dedicated single-thread executor for bulk email dispatch.
     * - Single thread: no concurrent dispatch jobs compete for resources.
     * - Named thread: visible in JVM thread dumps for easier diagnostics.
     */
    private final ExecutorService emailExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vendor-bulk-email");
        t.setDaemon(true); // Does NOT block JVM shutdown
        return t;
    });

    /**
     * Guard flag: prevents the admin from triggering two simultaneous bulk sends.
     * If true, a dispatch is already in progress and new requests are rejected.
     */
    private final AtomicBoolean dispatching = new AtomicBoolean(false);

    public boolean isDispatching() {
        return dispatching.get();
    }

    public void sendNotificationToAllActiveVendors(String asunto, String title, String text,
                                                   MultipartFile imagePart, List<String> targetEmails) {

        // ── Guard: reject if a dispatch is already running ──────────────────────
        if (!dispatching.compareAndSet(false, true)) {
            throw new RuntimeException("Ya hay un envío en progreso. Espera a que termine antes de iniciar otro.");
        }

        // ── Read image bytes synchronously (in the request thread) ───────────────
        // MultipartFile input stream is only valid during the HTTP request; reading
        // it here avoids "stream already closed" errors inside the async thread.
        byte[] imageBytes;
        String contentType;
        try {
            imageBytes = (imagePart != null && !imagePart.isEmpty()) ? imagePart.getBytes() : null;
            contentType = (imagePart != null) ? imagePart.getContentType() : null;
        } catch (Exception e) {
            dispatching.set(false);
            throw new RuntimeException("Error al leer la imagen. Intenta de nuevo.", e);
        }

        // ── Load vendor list synchronously (in the request thread) ───────────────
        // Keeps the DB connection usage short, bounded and transactional.
        // The async thread never touches the DB.
        List<Vendedor> allActive = vendedorRepository.findByActivoTrue();
        final List<Vendedor> targets;
        if (targetEmails != null && !targetEmails.isEmpty()) {
            Set<String> targetSet = new HashSet<>(targetEmails);
            targets = allActive.stream()
                    .filter(v -> targetSet.contains(v.getEmail()))
                    .collect(Collectors.toList());
        } else {
            targets = allActive;
        }

        log.info("Bulk email queued. Subject: '{}', targets: {}", asunto, targets.size());

        final byte[] finalImageBytes = imageBytes;
        final String finalContentType = contentType;
        final String cid = "imagen_ranking";
        final String htmlBody = buildHtmlBody(title, text, finalImageBytes != null);

        // ── Submit to dedicated single-thread executor ────────────────────────────
        emailExecutor.submit(() -> {
            int successCount = 0;
            int errorCount = 0;
            try {
                for (Vendedor vendor : targets) {
                    try {
                        emailService.sendHtmlEmailWithInlineImage(
                                vendor.getEmail(),
                                asunto,
                                htmlBody,
                                cid,
                                finalImageBytes,
                                finalContentType
                        );
                        successCount++;
                    } catch (Exception e) {
                        errorCount++;
                        log.error("Failed to send to {}: {}", vendor.getEmail(), e.getMessage());
                    }

                    // Small delay to avoid SMTP rate-limiting
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Bulk email thread interrupted at vendor {}. Stopping.", vendor.getEmail());
                        break;
                    }
                }
            } finally {
                // Always reset the guard, even if an unexpected error occurs
                dispatching.set(false);
                log.info("Bulk email dispatch finished. Success: {}, Errors: {}", successCount, errorCount);
            }
        });
    }

    private String buildHtmlBody(String title, String text, boolean hasImage) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html><head><meta charset=\"UTF-8\">");
        sb.append("<style>");
        sb.append("body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; }");
        sb.append("h2 { color: #f59e0b; text-align: center; }");
        sb.append(".content { background: #fdfdfd; border: 1px solid #eee; border-radius: 8px; padding: 20px; }");
        sb.append(".text-content { margin-bottom: 20px; white-space: pre-wrap; }");
        sb.append(".image-container { text-align: center; margin-top: 20px; }");
        sb.append(".image-container img { max-width: 100%; border-radius: 8px; }");
        sb.append("</style>");
        sb.append("</head><body>");

        sb.append("<div class=\"content\">");
        sb.append("<h2>").append(escapeHtml(title)).append("</h2>");

        if (text != null && !text.isBlank()) {
            sb.append("<div class=\"text-content\">").append(escapeHtml(text)).append("</div>");
        }

        if (hasImage) {
            sb.append("<div class=\"image-container\">");
            sb.append("<img src=\"cid:imagen_ranking\" alt=\"Ranking\" />");
            sb.append("</div>");
        }

        sb.append("</div>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /** Gracefully shut down the email executor when Spring context closes. */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down vendor email executor...");
        emailExecutor.shutdown();
    }
}
