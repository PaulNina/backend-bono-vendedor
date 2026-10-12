package com.ninabit.bono.modules.clientcampaign;

import com.ninabit.bono.modules.audit.AuditoriaService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupervisorClienteService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir = "uploads";

    private final RegistroClienteRepository registroClienteRepository;
    private final TicketClienteRepository ticketClienteRepository;
    private final EntregaRuletaRepository entregaRuletaRepository;
    private final AuditoriaService auditoriaService;
    private final com.ninabit.bono.modules.whatsapp.service.WhatsAppService whatsAppService;
    private final com.ninabit.bono.modules.email.EmailService emailService;

    @Transactional(readOnly = true)
    public Page<RegistroCliente> listarRegistros(
            Long campanaId,
            RegistroCliente.EstadoRegistro estado,
            String ciudad,
            String search,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());

        Specification<RegistroCliente> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (campanaId != null) {
                predicates.add(cb.equal(root.get("campanaCliente").get("id"), campanaId));
            }

            if (estado != null) {
                predicates.add(cb.equal(root.get("estado"), estado));
            }

            if (ciudad != null && !ciudad.isBlank() && !ciudad.equalsIgnoreCase("all")) {
                predicates.add(cb.equal(cb.lower(root.get("ciudad")), ciudad.toLowerCase().trim()));
            }

            if (fechaInicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fechaCompra"), fechaInicio));
            }

            if (fechaFin != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("fechaCompra"), fechaFin));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase().trim() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("nombreCliente")), pattern),
                        cb.like(cb.lower(root.get("apellidoCliente")), pattern),
                        cb.like(cb.lower(root.get("ci")), pattern),
                        cb.like(cb.lower(root.get("celular")), pattern),
                        cb.like(cb.lower(root.get("serial")), pattern),
                        cb.like(cb.lower(root.get("puntoVenta")), pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return registroClienteRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> obtenerKpis(Long campanaId) {
        List<Object[]> counts = registroClienteRepository.countByEstadoGroupBy(campanaId);
        Map<String, Long> kpis = new HashMap<>();
        kpis.put("PENDIENTE", 0L);
        kpis.put("EN_REVISION", 0L);
        kpis.put("OBSERVADO", 0L);
        kpis.put("RECHAZADO", 0L);
        kpis.put("APROBADO", 0L);
        kpis.put("TOTAL", 0L);

        long total = 0;
        for (Object[] row : counts) {
            RegistroCliente.EstadoRegistro est = (RegistroCliente.EstadoRegistro) row[0];
            Long cnt = (Long) row[1];
            kpis.put(est.name(), cnt);
            total += cnt;
        }
        kpis.put("TOTAL", total);
        return kpis;
    }

    @Transactional
    public RegistroCliente aprobarRegistro(Long registroId, Long supervisorId, String supervisorNombre) {
        RegistroCliente reg = registroClienteRepository.findById(registroId)
                .orElseThrow(() -> new IllegalArgumentException("Registro no encontrado con ID: " + registroId));

        RegistroCliente.EstadoRegistro estadoPrevio = reg.getEstado();
        reg.setEstado(RegistroCliente.EstadoRegistro.APROBADO);
        reg.setSupervisorId(supervisorId);
        reg.setSupervisorNombre(supervisorNombre);
        reg.setFechaRevision(LocalDateTime.now());
        reg.setMotivoObservacion(null);

        String historial = reg.getHistorialRevisiones() != null ? reg.getHistorialRevisiones() : "";
        historial += String.format("[%s] %s APROBÓ el registro (Estado previo: %s)\n", LocalDateTime.now(), supervisorNombre, estadoPrevio);
        reg.setHistorialRevisiones(historial);

        RegistroCliente saved = registroClienteRepository.save(reg);

        // Habilitar todos los tickets vinculados a este registro para participar en sorteos
        int ticketsHabilitados = ticketClienteRepository.updateEstadoByRegistroId(
                registroId, TicketCliente.EstadoTicket.HABILITADO
        );

        // Obtener códigos de tickets reales asignados al cliente
        List<TicketCliente> tickets = ticketClienteRepository.findByRegistroClienteId(registroId);
        String numerosTicketStr = tickets != null ? tickets.stream()
                .map(TicketCliente::getCodigoTicket)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(", ")) : "";

        auditoriaService.log(
                supervisorNombre,
                "APROBACION_SUPERVISOR_CLIENTE",
                "Aprobado registro ID: " + registroId + " del cliente " + reg.getNombreCliente() + " CI: " + reg.getCi() + " (" + ticketsHabilitados + " tickets habilitados: " + numerosTicketStr + ")",
                "RegistroCliente",
                registroId.toString()
        );

        // Campo 4 de la plantilla de WhatsApp: cantidad y códigos de ticket asignados (solo en template de aprobación)
        String campo4Tickets = String.valueOf(ticketsHabilitados);
        if (!numerosTicketStr.isBlank()) {
            campo4Tickets = ticketsHabilitados == 1
                    ? "1 (Ticket: " + numerosTicketStr + ")"
                    : ticketsHabilitados + " (Tickets: " + numerosTicketStr + ")";
        }

        notificarClienteWAInteligente(saved, "skyworth_registro_aprobado",
                List.of(
                        reg.getNombreCliente(),
                        reg.getProductoModelo() != null ? reg.getProductoModelo() : "Televisor Skyworth",
                        reg.getSerial(),
                        campo4Tickets
                ),
                String.format(
                        "🎉 ¡Felicidades %s! Tu compra de TV Skyworth (Serial: %s) ha sido APROBADA exitosamente.\n\n" +
                        "🎟️ Tickets habilitados: %s\n" +
                        "Consulta tus números de ticket en: https://bono.skyworth.bo/mis-tickets",
                        reg.getNombreCliente(), reg.getSerial(), campo4Tickets
                )
        );

        // Segundo correo con lista de tickets asignados
        String detalleTicketsEmail = numerosTicketStr.isBlank()
                ? String.format("%d ticket(s)", ticketsHabilitados)
                : String.format("%d ticket(s) habilitado(s): %s", ticketsHabilitados, numerosTicketStr);

        notificarClienteEmail(saved, "¡Compra Aprobada y Tickets Habilitados! - Skyworth", String.format(
                "Estimado(a) %s,\n\n" +
                "Nos complace informarte que tu registro de compra de TV Skyworth ha sido APROBADO exitosamente.\n\n" +
                "- Modelo: %s\n" +
                "- Serie / TAG: %s\n" +
                "- Tickets Asignados para Sorteos: %s\n\n" +
                "Puedes consultar tus números de ticket ingresando con tu C.I. o teléfono en:\n" +
                "https://bono.skyworth.bo/mis-tickets\n\n" +
                "¡Mucha suerte en los sorteos oficiales de Skyworth!\n\n" +
                "Equipo Skyworth Bolivia",
                reg.getNombreCliente(),
                reg.getProductoModelo() != null ? reg.getProductoModelo() : "Televisor Skyworth",
                reg.getSerial(),
                detalleTicketsEmail
        ));

        return saved;
    }

    @Transactional
    public RegistroCliente observarRegistro(Long registroId, Long supervisorId, String supervisorNombre, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("El motivo de la observación es obligatorio.");
        }

        RegistroCliente reg = registroClienteRepository.findById(registroId)
                .orElseThrow(() -> new IllegalArgumentException("Registro no encontrado con ID: " + registroId));

        RegistroCliente.EstadoRegistro estadoPrevio = reg.getEstado();
        reg.setEstado(RegistroCliente.EstadoRegistro.OBSERVADO);
        reg.setSupervisorId(supervisorId);
        reg.setSupervisorNombre(supervisorNombre);
        reg.setFechaRevision(LocalDateTime.now());
        reg.setMotivoObservacion(motivo.trim());

        String historial = reg.getHistorialRevisiones() != null ? reg.getHistorialRevisiones() : "";
        historial += String.format("[%s] %s OBSERVÓ el registro: %s (Estado previo: %s)\n", LocalDateTime.now(), supervisorNombre, motivo.trim(), estadoPrevio);
        reg.setHistorialRevisiones(historial);

        RegistroCliente saved = registroClienteRepository.save(reg);

        // Tickets quedan en NO_HABILITADO
        ticketClienteRepository.updateEstadoByRegistroId(registroId, TicketCliente.EstadoTicket.NO_HABILITADO);

        auditoriaService.log(
                supervisorNombre,
                "OBSERVACION_SUPERVISOR_CLIENTE",
                "Observado registro ID: " + registroId + " Motivo: " + motivo.trim(),
                "RegistroCliente",
                registroId.toString()
        );

        notificarClienteWAInteligente(saved, "skyworth_registro_observado_2",
                List.of(
                        reg.getNombreCliente(),
                        reg.getProductoModelo() != null ? reg.getProductoModelo() : "Televisor Skyworth",
                        reg.getSerial(),
                        motivo.trim()
                ),
                String.format(
                        "⚠️ Hola %s, tu registro de compra de TV Skyworth (Serial: %s) tiene una observación:\n\n" +
                        "📌 Motivo: \"%s\"\n\n" +
                        "👉 Para corregir, ingresa con tu C.I. (%s):\n" +
                        "https://bono.skyworth.bo/mis-tickets",
                        reg.getNombreCliente(), reg.getSerial(), motivo.trim(), reg.getCi()
                )
        );

        notificarClienteEmail(saved, "Registro de Compra Observado - Skyworth", String.format(
                "Estimado(a) %s,\n\n" +
                "Tu registro de compra de TV Skyworth (Serial: %s) tiene una observación por parte del equipo de supervisión:\n\n" +
                "Motivo de observación:\n" +
                "%s\n\n" +
                "Para corregir y volver a subir tus fotografías o respaldos, ingresa con tu C.I. (%s) en:\n" +
                "https://bono.skyworth.bo/mis-tickets\n\n" +
                "Una vez actualizados los respaldos, tus tickets serán habilitados para participar en los sorteos.\n\n" +
                "Atentamente,\n" +
                "Equipo de Supervisión Skyworth Bolivia",
                reg.getNombreCliente(),
                reg.getSerial(),
                motivo.trim(),
                reg.getCi()
        ));

        return saved;
    }

    @Transactional
    public RegistroCliente rechazarRegistro(Long registroId, Long supervisorId, String supervisorNombre, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("El motivo del rechazo es obligatorio.");
        }

        RegistroCliente reg = registroClienteRepository.findById(registroId)
                .orElseThrow(() -> new IllegalArgumentException("Registro no encontrado con ID: " + registroId));

        RegistroCliente.EstadoRegistro estadoPrevio = reg.getEstado();
        reg.setEstado(RegistroCliente.EstadoRegistro.RECHAZADO);
        reg.setSupervisorId(supervisorId);
        reg.setSupervisorNombre(supervisorNombre);
        reg.setFechaRevision(LocalDateTime.now());
        reg.setMotivoObservacion(motivo.trim());

        String historial = reg.getHistorialRevisiones() != null ? reg.getHistorialRevisiones() : "";
        historial += String.format("[%s] %s RECHAZÓ el registro: %s (Estado previo: %s)\n", LocalDateTime.now(), supervisorNombre, motivo.trim(), estadoPrevio);
        reg.setHistorialRevisiones(historial);

        RegistroCliente saved = registroClienteRepository.save(reg);

        // Anular los tickets
        ticketClienteRepository.updateEstadoByRegistroId(registroId, TicketCliente.EstadoTicket.ANULADO);

        auditoriaService.log(
                supervisorNombre,
                "RECHAZO_SUPERVISOR_CLIENTE",
                "Rechazado registro ID: " + registroId + " Motivo: " + motivo.trim(),
                "RegistroCliente",
                registroId.toString()
        );

        notificarClienteWAInteligente(saved, "skyworth_registro_rechazado",
                List.of(
                        reg.getNombreCliente(),
                        reg.getSerial(),
                        motivo.trim()
                ),
                String.format(
                        "❌ Hola %s, lamentamos informarte que tu registro de TV Skyworth (Serial: %s) ha sido RECHAZADO.\n\n" +
                        "📌 Motivo: \"%s\"\n\n" +
                        "Si consideras que es un error, por favor contacta al comercio autorizado donde compraste el televisor.",
                        reg.getNombreCliente(), reg.getSerial(), motivo.trim()
                )
        );

        notificarClienteEmail(saved, "Registro de Compra Rechazado - Skyworth", String.format(
                "Estimado(a) %s,\n\n" +
                "Te informamos que tu registro de compra de TV Skyworth (Serial: %s) ha sido RECHAZADO.\n\n" +
                "Motivo del rechazo:\n" +
                "%s\n\n" +
                "Si consideras que se trata de un error, por favor contacta a soporte o al comercio autorizado donde adquiriste tu televisor.\n\n" +
                "Atentamente,\n" +
                "Equipo Skyworth Bolivia",
                reg.getNombreCliente(),
                reg.getSerial(),
                motivo.trim()
        ));

        return saved;
    }

    /**
     * Lógica inteligente de notificación WhatsApp según canal de registro y ventana de 24h:
     * - BANDEO: siempre usa template (operador registró al cliente, sin sesión previa).
     * - CHATBOT: siempre texto libre (ya hay un canal de comunicación activo dentro de 24h).
     * - WEB: verifica si el cliente escribió al bot en las últimas 24h:
     *   · Si tiene sesión activa → texto libre.
     *   · Si no tiene o pasaron más de 24h → template aprobado de Meta.
     */
    private void notificarClienteWAInteligente(
            RegistroCliente reg,
            String templateName,
            List<String> templateParams,
            String textoLibre
    ) {
        if (reg == null || reg.getCelular() == null || reg.getCelular().isBlank() || whatsAppService == null) {
            return;
        }
        try {
            String celular = reg.getCelular().replaceAll("[^0-9]", "");
            if (celular.length() == 8) {
                celular = "591" + celular; // Prefijo internacional Bolivia
            }

            RegistroCliente.CanalRegistro canal = reg.getCanalRegistro();

            if (canal == RegistroCliente.CanalRegistro.CHATBOT) {
                // Ya hay sesión activa: enviar texto libre directamente
                whatsAppService.enviarMensajeTexto(celular, textoLibre);
                log.info("[WA-CHATBOT] Texto libre enviado a {} (Registro: {})", celular, reg.getId());

            } else if (canal == RegistroCliente.CanalRegistro.WEB) {
                // Verificar ventana de 24h
                boolean sesionActiva = whatsAppService.tieneVentana24hActiva(celular);
                if (sesionActiva) {
                    whatsAppService.enviarMensajeTexto(celular, textoLibre);
                    log.info("[WA-WEB-SESION] Texto libre enviado a {} (ventana 24h activa, Registro: {})", celular, reg.getId());
                } else {
                    whatsAppService.enviarTemplate(celular, templateName, templateParams, textoLibre);
                    log.info("[WA-WEB-TEMPLATE] Template '{}' enviado a {} (sin sesión activa, Registro: {})", templateName, celular, reg.getId());
                }

            } else {
                // BANDEO u otro canal: siempre template (el operador registró al cliente)
                whatsAppService.enviarTemplate(celular, templateName, templateParams, textoLibre);
                log.info("[WA-BANDEO-TEMPLATE] Template '{}' enviado a {} (Registro: {})", templateName, celular, reg.getId());
            }

        } catch (Exception e) {
            log.warn("No se pudo enviar notificación WhatsApp al cliente {}: {}", reg.getCelular(), e.getMessage());
        }
    }

    private void notificarClienteEmail(RegistroCliente reg, String subject, String text) {
        if (reg == null || reg.getEmail() == null || reg.getEmail().isBlank() || emailService == null) {
            return;
        }
        try {
            emailService.sendEmail(reg.getEmail().trim(), subject, text);
            log.info("Notificación por correo enviada al cliente {} (Registro: {})", reg.getEmail(), reg.getId());
        } catch (Exception e) {
            log.warn("No se pudo enviar correo al cliente {}: {}", reg.getEmail(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Optional<EntregaRuleta> obtenerEntregaRuleta(Long registroId) {
        return entregaRuletaRepository.findByRegistroClienteId(registroId);
    }

    @Transactional
    public RegistroCliente actualizarEvidencia(Long registroId, String tipoEvidencia, MultipartFile archivo, Long supervisorId, String supervisorNombre) throws IOException {
        RegistroCliente reg = registroClienteRepository.findById(registroId)
                .orElseThrow(() -> new IllegalArgumentException("Registro no encontrado con ID: " + registroId));

        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("El archivo de imagen no puede estar vacío.");
        }

        // Caso especial: Foto de entrega de ruleta (Bandeo)
        if ("fotoEntrega".equals(tipoEvidencia)) {
            EntregaRuleta entrega = entregaRuletaRepository.findByRegistroClienteId(registroId)
                    .orElseThrow(() -> new IllegalArgumentException("No existe registro de entrega de ruleta para el registro ID: " + registroId));

            String nuevaRuta = guardarArchivo(archivo, "entrega_ruleta");
            entrega.setFotoEntrega(nuevaRuta);
            entregaRuletaRepository.save(entrega);

            String historial = reg.getHistorialRevisiones() != null ? reg.getHistorialRevisiones() : "";
            historial += String.format("[%s] %s ACTUALIZÓ/SUBIÓ la foto de entrega de ruleta\n", LocalDateTime.now(), supervisorNombre);
            reg.setHistorialRevisiones(historial);
            RegistroCliente saved = registroClienteRepository.save(reg);

            auditoriaService.log(
                    supervisorNombre,
                    "ACTUALIZAR_EVIDENCIA_SUPERVISOR",
                    "Actualizada foto entrega de ruleta en registro ID: " + registroId + " nueva ruta: " + nuevaRuta,
                    "RegistroCliente",
                    registroId.toString()
            );
            return saved;
        }

        String prefix = switch (tipoEvidencia) {
            case "fotoNota" -> "nota";
            case "fotoTag" -> "tag";
            case "fotoPoliza" -> "poliza";
            case "fotoCiAnverso" -> "ci_anverso";
            case "fotoCiReverso" -> "ci_reverso";
            default -> throw new IllegalArgumentException("Tipo de evidencia no válido: " + tipoEvidencia);
        };

        String nuevaRuta = guardarArchivo(archivo, prefix);

        switch (tipoEvidencia) {
            case "fotoNota" -> reg.setFotoNota(nuevaRuta);
            case "fotoTag" -> reg.setFotoTag(nuevaRuta);
            case "fotoPoliza" -> reg.setFotoPoliza(nuevaRuta);
            case "fotoCiAnverso" -> reg.setFotoCiAnverso(nuevaRuta);
            case "fotoCiReverso" -> reg.setFotoCiReverso(nuevaRuta);
        }

        String historial = reg.getHistorialRevisiones() != null ? reg.getHistorialRevisiones() : "";
        historial += String.format("[%s] %s ACTUALIZÓ/SUBIÓ la evidencia '%s'\n", LocalDateTime.now(), supervisorNombre, tipoEvidencia);
        reg.setHistorialRevisiones(historial);

        RegistroCliente saved = registroClienteRepository.save(reg);

        auditoriaService.log(
                supervisorNombre,
                "ACTUALIZAR_EVIDENCIA_SUPERVISOR",
                "Actualizada evidencia " + tipoEvidencia + " en registro ID: " + registroId + " nueva ruta: " + nuevaRuta,
                "RegistroCliente",
                registroId.toString()
        );

        return saved;
    }

    @Transactional
    public RegistroCliente eliminarEvidencia(Long registroId, String tipoEvidencia, Long supervisorId, String supervisorNombre) {
        RegistroCliente reg = registroClienteRepository.findById(registroId)
                .orElseThrow(() -> new IllegalArgumentException("Registro no encontrado con ID: " + registroId));

        // Caso especial: Foto de entrega de ruleta (Bandeo)
        if ("fotoEntrega".equals(tipoEvidencia)) {
            EntregaRuleta entrega = entregaRuletaRepository.findByRegistroClienteId(registroId)
                    .orElseThrow(() -> new IllegalArgumentException("No existe registro de entrega de ruleta para el registro ID: " + registroId));

            entrega.setFotoEntrega(null);
            entregaRuletaRepository.save(entrega);

            String historial = reg.getHistorialRevisiones() != null ? reg.getHistorialRevisiones() : "";
            historial += String.format("[%s] %s QUITÓ la foto de entrega de ruleta\n", LocalDateTime.now(), supervisorNombre);
            reg.setHistorialRevisiones(historial);
            RegistroCliente saved = registroClienteRepository.save(reg);

            auditoriaService.log(
                    supervisorNombre,
                    "ELIMINAR_EVIDENCIA_SUPERVISOR",
                    "Quitada foto de entrega de ruleta en registro ID: " + registroId,
                    "RegistroCliente",
                    registroId.toString()
            );
            return saved;
        }

        switch (tipoEvidencia) {
            case "fotoNota" -> reg.setFotoNota(null);
            case "fotoTag" -> reg.setFotoTag(null);
            case "fotoPoliza" -> reg.setFotoPoliza(null);
            case "fotoCiAnverso" -> reg.setFotoCiAnverso(null);
            case "fotoCiReverso" -> reg.setFotoCiReverso(null);
            default -> throw new IllegalArgumentException("Tipo de evidencia no válido: " + tipoEvidencia);
        }

        String historial = reg.getHistorialRevisiones() != null ? reg.getHistorialRevisiones() : "";
        historial += String.format("[%s] %s QUITÓ la evidencia '%s'\n", LocalDateTime.now(), supervisorNombre, tipoEvidencia);
        reg.setHistorialRevisiones(historial);

        RegistroCliente saved = registroClienteRepository.save(reg);

        auditoriaService.log(
                supervisorNombre,
                "ELIMINAR_EVIDENCIA_SUPERVISOR",
                "Quitada evidencia " + tipoEvidencia + " en registro ID: " + registroId,
                "RegistroCliente",
                registroId.toString()
        );

        return saved;
    }

    private String guardarArchivo(MultipartFile file, String prefix) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String subDir = "cliente_final/" + LocalDate.now().getYear() + "/" + LocalDate.now().getMonthValue();
        Path dirPath = Paths.get(uploadDir, subDir);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "doc.jpg";
        String ext = "";
        int dotIdx = originalFilename.lastIndexOf('.');
        if (dotIdx >= 0) {
            ext = originalFilename.substring(dotIdx);
        }
        if (ext.isEmpty()) ext = ".jpg";

        String filename = prefix + "_" + UUID.randomUUID().toString().substring(0, 10) + ext;
        Path targetPath = dirPath.resolve(filename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        return "uploads/" + subDir + "/" + filename;
    }
}
