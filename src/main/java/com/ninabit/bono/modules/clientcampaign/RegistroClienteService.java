package com.ninabit.bono.modules.clientcampaign;

import com.ninabit.bono.modules.audit.AuditoriaService;
import com.ninabit.bono.modules.clientcampaign.dto.*;
import com.ninabit.bono.modules.product.Producto;
import com.ninabit.bono.modules.product.ProductoRepository;
import com.ninabit.bono.modules.serial.Serial;
import com.ninabit.bono.modules.serial.SerialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistroClienteService {

    private final CampanaClienteRepository campanaClienteRepository;
    private final CampanaClienteProductoRepository campanaClienteProductoRepository;
    private final RegistroClienteRepository registroClienteRepository;
    private final TicketClienteRepository ticketClienteRepository;
    private final SerialRepository serialRepository;
    private final ProductoRepository productoRepository;
    private final AuditoriaService auditoriaService;
    private final com.ninabit.bono.modules.email.EmailService emailService;

    @org.springframework.context.annotation.Lazy
    private final com.ninabit.bono.modules.whatsapp.service.WhatsAppService whatsAppService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Transactional(readOnly = true)
    public Map<String, Object> validarSerialParaCliente(String serial, Long campanaId) {
        String cleanedSerial = serial != null ? serial.trim().toUpperCase() : "";
        Map<String, Object> response = new HashMap<>();

        if (cleanedSerial.isEmpty()) {
            response.put("valido", false);
            response.put("mensaje", "El serial no puede estar vacío.");
            return response;
        }

        // 1. Obtener campaña activa
        CampanaCliente campana;
        if (campanaId != null) {
            campana = campanaClienteRepository.findById(campanaId).orElse(null);
        } else {
            campana = campanaClienteRepository.findCurrentActiveCampaign(LocalDate.now()).orElse(null);
        }

        if (campana == null) {
            response.put("valido", false);
            response.put("mensaje", "No hay ninguna campaña de cliente final activa en este momento.");
            return response;
        }

        response.put("campanaId", campana.getId());
        response.put("campanaNombre", campana.getNombre());

        // 2. Verificar que el serial no esté registrado previamente en esta campaña
        boolean yaRegistrado = registroClienteRepository.existsByCampanaClienteIdAndSerial(campana.getId(), cleanedSerial);
        if (yaRegistrado) {
            response.put("valido", false);
            response.put("mensaje", "Este serial o TAG ya fue registrado en la campaña actual.");
            return response;
        }

        // 3. Verificar en el padrón de seriales oficiales de Skyworth
        Optional<Serial> serialOpt = serialRepository.findByNumeroSerie(cleanedSerial);
        if (serialOpt.isEmpty()) {
            // Permitimos advertencia o validación según catálogo de productos
            response.put("valido", false);
            response.put("mensaje", "El serial ingresado no figura en el padrón oficial de Skyworth.");
            return response;
        }

        Serial serialEntity = serialOpt.get();
        if (serialEntity.isBloqueado()) {
            response.put("valido", false);
            response.put("mensaje", "El serial se encuentra restringido/bloqueado: " +
                    (serialEntity.getMotivoBloqueo() != null ? serialEntity.getMotivoBloqueo() : "Equipo restringido"));
            return response;
        }

        // 4. Determinar modelo, pulgadas y tickets proyectados
        Producto producto = serialEntity.getProducto();
        String modelo = serialEntity.getModelo();
        Integer pulgadas = 0;
        Long productoId = serialEntity.getProductoId();

        if (producto != null) {
            modelo = producto.getModelo() != null ? producto.getModelo() : producto.getModeloCodigo();
            pulgadas = producto.getTamanoPulgadas() != null ? producto.getTamanoPulgadas()
                    : (producto.getPulgadas() != null ? producto.getPulgadas() : 0);
            productoId = producto.getId();
        }

        int ticketsProyectados = 0;
        if (productoId != null) {
            Optional<CampanaClienteProducto> cpOpt = campanaClienteProductoRepository
                    .findByCampanaClienteIdAndProductoIdAndActivoTrue(campana.getId(), productoId);
            if (cpOpt.isPresent() && cpOpt.get().getTickets() != null) {
                ticketsProyectados = cpOpt.get().getTickets();
            }
        }

        if (ticketsProyectados <= 0) {
            response.put("valido", false);
            response.put("noParticipa", true);
            response.put("serialExiste", true);
            response.put("serial", cleanedSerial);
            response.put("productoId", productoId);
            response.put("modelo", modelo != null ? modelo : "");
            response.put("pulgadas", pulgadas);
            response.put("ticketsProyectados", 0);
            response.put("mensaje", "El número de serie existe y es original de Skyworth" +
                    (modelo != null && !modelo.isBlank() ? " (" + modelo + ")" : "") +
                    ", pero este modelo no está participando en la campaña activa.");
            return response;
        }

        response.put("valido", true);
        response.put("noParticipa", false);
        response.put("serialExiste", true);
        response.put("serial", cleanedSerial);
        response.put("productoId", productoId);
        response.put("modelo", modelo != null ? modelo : "");
        response.put("pulgadas", pulgadas);
        response.put("ticketsProyectados", ticketsProyectados);
        response.put("mensaje", "Serial válido de Skyworth. Otorga " + ticketsProyectados + " ticket(s) para los sorteos.");
        return response;
    }

    @Transactional
    public RegistroCliente registrarCompraCliente(
            Long campanaId,
            RegistroCliente.CanalRegistro canal,
            Long impulsadorId,
            String impulsadorNombre,
            String nombreCliente,
            String apellidoCliente,
            String ci,
            String celular,
            String email,
            String ciudad,
            String puntoVenta,
            Long productoId,
            String productoModelo,
            Integer productoPulgadas,
            String serial,
            LocalDate fechaCompra,
            MultipartFile fotoTag,
            MultipartFile fotoPoliza,
            MultipartFile fotoNota,
            MultipartFile fotoCiAnverso,
            MultipartFile fotoCiReverso
    ) throws IOException {

        String cleanedSerial = serial != null ? serial.trim().toUpperCase() : "";

        // Validar campaña
        CampanaCliente campana = campanaClienteRepository.findById(campanaId)
                .orElseThrow(() -> new IllegalArgumentException("Campaña cliente no encontrada con ID: " + campanaId));

        if (!campana.isActivo() || !campana.isRegistroHabilitado()) {
            throw new IllegalStateException("El registro de compras no está habilitado para esta campaña.");
        }

        // Validar no duplicidad en la campaña
        if (registroClienteRepository.existsByCampanaClienteIdAndSerial(campana.getId(), cleanedSerial)) {
            throw new IllegalStateException("El serial " + cleanedSerial + " ya fue registrado previamente en esta campaña.");
        }

        // Subir archivos fotográficos
        String pathTag = guardarArchivo(fotoTag, "tag");
        String pathPoliza = guardarArchivo(fotoPoliza, "poliza");
        String pathNota = guardarArchivo(fotoNota, "nota");
        String pathCiAnverso = guardarArchivo(fotoCiAnverso, "ci_anverso");
        String pathCiReverso = guardarArchivo(fotoCiReverso, "ci_reverso");

        return registrarCompraClienteConRutas(
                campanaId, canal, impulsadorId, impulsadorNombre,
                nombreCliente, apellidoCliente, ci, celular, email,
                ciudad, puntoVenta, productoId, productoModelo, productoPulgadas,
                serial, fechaCompra, pathTag, pathPoliza, pathNota, pathCiAnverso, pathCiReverso
        );
    }

    @Transactional
    public RegistroCliente registrarCompraClienteConRutas(
            Long campanaId,
            RegistroCliente.CanalRegistro canal,
            Long impulsadorId,
            String impulsadorNombre,
            String nombreCliente,
            String apellidoCliente,
            String ci,
            String celular,
            String email,
            String ciudad,
            String puntoVenta,
            Long productoId,
            String productoModelo,
            Integer productoPulgadas,
            String serial,
            LocalDate fechaCompra,
            String pathTag,
            String pathPoliza,
            String pathNota,
            String pathCiAnverso,
            String pathCiReverso
    ) {
        String cleanedSerial = serial != null ? serial.trim().toUpperCase() : "";

        // Validar campaña
        CampanaCliente campana = campanaClienteRepository.findById(campanaId)
                .orElseThrow(() -> new IllegalArgumentException("Campaña cliente no encontrada con ID: " + campanaId));

        if (!campana.isActivo() || !campana.isRegistroHabilitado()) {
            throw new IllegalStateException("El registro de compras no está habilitado para esta campaña.");
        }

        // Validar no duplicidad en la campaña
        if (registroClienteRepository.existsByCampanaClienteIdAndSerial(campana.getId(), cleanedSerial)) {
            throw new IllegalStateException("El serial " + cleanedSerial + " ya fue registrado previamente en esta campaña.");
        }

        // Crear registro en estado PENDIENTE
        RegistroCliente registro = RegistroCliente.builder()
                .campanaCliente(campana)
                .canalRegistro(canal)
                .impulsadorId(impulsadorId)
                .impulsadorNombre(impulsadorNombre)
                .nombreCliente(nombreCliente != null ? nombreCliente.trim() : "")
                .apellidoCliente(apellidoCliente != null ? apellidoCliente.trim() : "")
                .ci(ci != null ? ci.trim() : "")
                .celular(celular != null ? celular.trim() : "")
                .email(email != null ? email.trim() : null)
                .ciudad(ciudad != null && !ciudad.isBlank() ? ciudad.trim() : "Santa Cruz")
                .puntoVenta(puntoVenta != null ? puntoVenta.trim() : "")
                .productoId(productoId)
                .productoModelo(productoModelo)
                .productoPulgadas(productoPulgadas)
                .serial(cleanedSerial)
                .fechaCompra(fechaCompra != null ? fechaCompra : LocalDate.now())
                .fotoTag(pathTag)
                .fotoPoliza(pathPoliza)
                .fotoNota(pathNota)
                .fotoCiAnverso(pathCiAnverso)
                .fotoCiReverso(pathCiReverso)
                .estado(RegistroCliente.EstadoRegistro.PENDIENTE)
                .build();


        // Resolver productoId a partir del serial si viene vacío
        if (productoId == null && !cleanedSerial.isBlank()) {
            Optional<com.ninabit.bono.modules.serial.Serial> sOpt = serialRepository.findByNumeroSerie(cleanedSerial);
            if (sOpt.isPresent()) {
                com.ninabit.bono.modules.serial.Serial s = sOpt.get();
                if (s.getProductoId() != null) {
                    productoId = s.getProductoId();
                    registro.setProductoId(productoId);
                }
                if (s.getModelo() != null && (registro.getProductoModelo() == null || registro.getProductoModelo().isBlank())) {
                    registro.setProductoModelo(s.getModelo());
                }
            }
        }

        registro = registroClienteRepository.save(registro);

        // Determinar cantidad de tickets (debe participar en la campaña)
        int cantTickets = 0;
        if (productoId != null) {
            Optional<CampanaClienteProducto> cpOpt = campanaClienteProductoRepository
                    .findByCampanaClienteIdAndProductoIdAndActivoTrue(campana.getId(), productoId);
            if (cpOpt.isPresent() && cpOpt.get().getTickets() != null && cpOpt.get().getTickets() > 0) {
                cantTickets = cpOpt.get().getTickets();
            }
        }

        if (cantTickets <= 0) {
            throw new IllegalStateException("El producto asociado al serial " + cleanedSerial + " no participa en la campaña actual.");
        }

        // Generar N tickets en estado NO_HABILITADO únicamente si el producto está en la campaña
        List<TicketCliente> tickets = new ArrayList<>();
        if (cantTickets > 0) {
            int year = LocalDate.now().getYear();
            for (int i = 1; i <= cantTickets; i++) {
                String codigoTicket = String.format("SKY-%d-%06d-%d", year, registro.getId(), i);
                TicketCliente ticket = TicketCliente.builder()
                        .codigoTicket(codigoTicket)
                        .registroCliente(registro)
                        .campanaCliente(campana)
                        .clienteCi(registro.getCi() != null ? registro.getCi().trim() : "")
                        .clienteNombre(registro.getNombreCliente() + " " + registro.getApellidoCliente())
                        .clienteCelular(registro.getCelular())
                        .serial(registro.getSerial())
                        .productoModelo(registro.getProductoModelo())
                        .estado(TicketCliente.EstadoTicket.NO_HABILITADO)
                        .excluidoQuincenal(false)
                        .build();
                tickets.add(ticket);
            }
            ticketClienteRepository.saveAll(tickets);
        }

        auditoriaService.log(
                impulsadorNombre != null ? impulsadorNombre : "PUBLIC_CLIENT",
                "REGISTRO_CLIENTE",
                "Registro de cliente " + registro.getNombreCliente() + " CI: " + registro.getCi() + " Serial: " + cleanedSerial + " (" + cantTickets + " tickets creados en estado NO_HABILITADO)",
                "RegistroCliente",
                registro.getId().toString()
        );

        // ── Notificación WhatsApp según canal de registro ──────────────────
        notificarRegistroWAInteligente(registro, cantTickets);

        // ── Notificación Email (si tiene email) ────────────────────────────
        if (registro.getEmail() != null && !registro.getEmail().isBlank()) {
            try {
                String subject = "Registro de Compra Recibido - Skyworth";
                String text = String.format(
                        "Estimado(a) %s,\n\n" +
                        "Hemos recibido exitosamente el registro de tu televisor Skyworth:\n\n" +
                        "- Modelo: %s\n" +
                        "- Serie / TAG: %s\n" +
                        "- Tienda / Punto de Venta: %s\n" +
                        "- Tickets Proyectados: %d ticket(s)\n" +
                        "- Estado Inicial: PENDIENTE DE REVISIÓN\n\n" +
                        "Nuestro equipo de supervisión verificará tus respaldos fotográficos. Una vez aprobado, tus tickets serán habilitados para los sorteos oficiales de Skyworth.\n\n" +
                        "Puedes hacer seguimiento con tu C.I. en:\n" +
                        "https://bono.skyworth.bo/mis-tickets\n\n" +
                        "¡Gracias por tu compra!\n" +
                        "Equipo Skyworth Bolivia",
                        registro.getNombreCliente(),
                        registro.getProductoModelo() != null ? registro.getProductoModelo() : "Televisor Skyworth",
                        cleanedSerial,
                        registro.getPuntoVenta() != null && !registro.getPuntoVenta().isBlank() ? registro.getPuntoVenta() : "Comercio Autorizado",
                        cantTickets
                );
                emailService.sendEmail(registro.getEmail().trim(), subject, text);
                log.info("Correo inicial de registro enviado al cliente {} (Registro: {})", registro.getEmail(), registro.getId());
            } catch (Exception e) {
                log.warn("No se pudo enviar correo inicial de registro al cliente {}: {}", registro.getEmail(), e.getMessage());
            }
        }

        return registro;
    }

    /**
     * Notificación WA de registro nuevo según canal:
     * - BANDEO: siempre template + email ya se maneja aparte.
     * - WEB: verifica ventana 24h → texto libre o template.
     * - CHATBOT: texto libre (sesión activa garantizada).
     */
    private void notificarRegistroWAInteligente(RegistroCliente registro, int cantTickets) {
        if (whatsAppService == null || registro.getCelular() == null || registro.getCelular().isBlank()) return;
        try {
            String celular = registro.getCelular().replaceAll("[^0-9]", "");
            if (celular.length() == 8) celular = "591" + celular;

            List<String> templateParams = List.of(
                    registro.getNombreCliente(),
                    registro.getProductoModelo() != null ? registro.getProductoModelo() : "Televisor Skyworth",
                    registro.getSerial(),
                    String.valueOf(cantTickets)
            );
            String textoLibre = String.format(
                    "\u2705 Tu registro fue recibido correctamente.\n\n" +
                    "\ud83d\udcfa Equipo: %s\n" +
                    "\ud83d\udd22 Serie/TAG: %s\n" +
                    "\ud83c\udfdf\ufe0f Tickets generados: %d\n\n" +
                    "Nuestro equipo revisará tus respaldos en 24-48h.\n" +
                    "Seguimiento: https://bono.skyworth.bo/mis-tickets",
                    registro.getProductoModelo() != null ? registro.getProductoModelo() : "Televisor Skyworth",
                    registro.getSerial(), cantTickets
            );

            RegistroCliente.CanalRegistro canal = registro.getCanalRegistro();
            if (canal == RegistroCliente.CanalRegistro.CHATBOT) {
                // Sesión ya abierta: solo confirmar con texto libre
                whatsAppService.enviarMensajeTexto(celular, textoLibre);
                log.info("[WA-CHATBOT] Confirmación de registro enviada a {}", celular);
            } else if (canal == RegistroCliente.CanalRegistro.WEB) {
                if (whatsAppService.tieneVentana24hActiva(celular)) {
                    whatsAppService.enviarMensajeTexto(celular, textoLibre);
                    log.info("[WA-WEB-SESION] Confirmación texto libre enviada a {}", celular);
                } else {
                    whatsAppService.enviarTemplate(celular, "skyworth_registro_recibido", templateParams, textoLibre);
                    log.info("[WA-WEB-TEMPLATE] Template 'skyworth_registro_recibido' enviado a {}", celular);
                }
            } else {
                // BANDEO: template siempre
                whatsAppService.enviarTemplate(celular, "skyworth_registro_recibido", templateParams, textoLibre);
                log.info("[WA-BANDEO-TEMPLATE] Template 'skyworth_registro_recibido' enviado a {}", celular);
            }
        } catch (Exception e) {
            log.warn("Error enviando notificación WA de registro a {}: {}", registro.getCelular(), e.getMessage());
        }
    }

    @Transactional
    public ConsultarTicketsResponseDto consultarTicketsPorCi(String ci) {
        String cleanCi = ci != null ? ci.trim() : "";
        List<RegistroCliente> registros = cleanCi.isBlank()
                ? Collections.emptyList()
                : registroClienteRepository.findByCiFlexible(cleanCi);

        // Auto-reparación inteligente: Si algún registro aprobado o pendiente no tiene tickets, generarlos
        for (RegistroCliente r : registros) {
            List<TicketCliente> existing = ticketClienteRepository.findByRegistroClienteId(r.getId());
            if (existing.isEmpty() && r.getEstado() != RegistroCliente.EstadoRegistro.RECHAZADO) {
                int cantTickets = 0;
                Long pId = r.getProductoId();
                if (pId == null && r.getSerial() != null && !r.getSerial().isBlank()) {
                    serialRepository.findByNumeroSerie(r.getSerial().trim().toUpperCase()).ifPresent(s -> {
                        if (s.getProductoId() != null) r.setProductoId(s.getProductoId());
                    });
                    pId = r.getProductoId();
                }
                if (pId != null && r.getCampanaCliente() != null) {
                    Optional<CampanaClienteProducto> cpOpt = campanaClienteProductoRepository
                            .findByCampanaClienteIdAndProductoIdAndActivoTrue(r.getCampanaCliente().getId(), pId);
                    if (cpOpt.isPresent() && cpOpt.get().getTickets() != null && cpOpt.get().getTickets() > 0) {
                        cantTickets = cpOpt.get().getTickets();
                    }
                }
                // Si el producto no está en la campaña, no genera tickets (cantTickets = 0)
                if (cantTickets > 0) {
                    TicketCliente.EstadoTicket est = (r.getEstado() == RegistroCliente.EstadoRegistro.APROBADO)
                            ? TicketCliente.EstadoTicket.HABILITADO
                            : TicketCliente.EstadoTicket.NO_HABILITADO;
                    int year = r.getFechaCompra() != null ? r.getFechaCompra().getYear() : LocalDate.now().getYear();
                    List<TicketCliente> nuevos = new ArrayList<>();
                    for (int i = 1; i <= cantTickets; i++) {
                        nuevos.add(TicketCliente.builder()
                                .codigoTicket(String.format("SKY-%d-%06d-%d", year, r.getId(), i))
                                .registroCliente(r)
                                .campanaCliente(r.getCampanaCliente())
                                .clienteCi(r.getCi() != null ? r.getCi().trim() : "")
                                .clienteNombre(r.getNombreCliente() + " " + r.getApellidoCliente())
                                .clienteCelular(r.getCelular())
                                .serial(r.getSerial())
                                .productoModelo(r.getProductoModelo())
                                .estado(est)
                                .excluidoQuincenal(false)
                                .build());
                    }
                    ticketClienteRepository.saveAll(nuevos);
                }
            }
        }

        List<Long> regIds = registros.stream().map(RegistroCliente::getId).toList();
        List<TicketCliente> tickets;
        if (!regIds.isEmpty()) {
            tickets = ticketClienteRepository.findByRegistroClienteIdIn(regIds);
        } else {
            tickets = cleanCi.isBlank() ? Collections.emptyList() : ticketClienteRepository.findByClienteCiFlexible(cleanCi);
        }

        List<TicketClienteDto> ticketsDto = tickets.stream()
                .map(TicketClienteDto::fromEntity)
                .toList();

        List<RegistroClienteDto> registrosDto = registros.stream()
                .map(RegistroClienteDto::fromEntity)
                .toList();

        long habilitados = tickets.stream()
                .filter(t -> t.getEstado() == TicketCliente.EstadoTicket.HABILITADO)
                .count();

        return ConsultarTicketsResponseDto.builder()
                .ci(cleanCi)
                .totalTickets(tickets.size())
                .ticketsHabilitados(habilitados)
                .registros(registrosDto)
                .tickets(ticketsDto)
                .build();
    }

    @Transactional
    public RegistroCliente subsanarObservacion(
            Long registroId,
            String ci,
            MultipartFile fotoTag,
            MultipartFile fotoPoliza,
            MultipartFile fotoNota,
            MultipartFile fotoCiAnverso,
            MultipartFile fotoCiReverso
    ) throws IOException {
        RegistroCliente reg = registroClienteRepository.findById(registroId)
                .orElseThrow(() -> new IllegalArgumentException("Registro no encontrado con ID: " + registroId));

        if (reg.getCi() != null && !reg.getCi().isBlank()) {
            if (ci == null || !reg.getCi().trim().equalsIgnoreCase(ci.trim())) {
                throw new IllegalArgumentException("La cédula de identidad no coincide con el registro.");
            }
        }

        if (fotoTag != null && !fotoTag.isEmpty()) reg.setFotoTag(guardarArchivo(fotoTag, "tag"));
        if (fotoPoliza != null && !fotoPoliza.isEmpty()) reg.setFotoPoliza(guardarArchivo(fotoPoliza, "poliza"));
        if (fotoNota != null && !fotoNota.isEmpty()) reg.setFotoNota(guardarArchivo(fotoNota, "nota"));
        if (fotoCiAnverso != null && !fotoCiAnverso.isEmpty()) reg.setFotoCiAnverso(guardarArchivo(fotoCiAnverso, "ci_anverso"));
        if (fotoCiReverso != null && !fotoCiReverso.isEmpty()) reg.setFotoCiReverso(guardarArchivo(fotoCiReverso, "ci_reverso"));

        // Al subsanar, vuelve a estado PENDIENTE para revisión del supervisor
        reg.setEstado(RegistroCliente.EstadoRegistro.PENDIENTE);
        reg.setMotivoObservacion("Documentos subsanados por el usuario. Pendiente de re-verificación.");
        RegistroCliente updated = registroClienteRepository.save(reg);

        String userAudit = ci != null && !ci.isBlank() ? ci : "CLIENTE_" + reg.getId();
        auditoriaService.log(userAudit, "SUBSANACION_CLIENTE", "Cliente subsanó respaldos observados del registro " + reg.getId(), "RegistroCliente", reg.getId().toString());
        return updated;
    }

    @Transactional
    public RegistroCliente subsanarObservacionImpulsador(
            Long registroId,
            Long impulsadorId,
            MultipartFile fotoTag,
            MultipartFile fotoPoliza,
            MultipartFile fotoNota,
            MultipartFile fotoCiAnverso,
            MultipartFile fotoCiReverso
    ) throws IOException {
        RegistroCliente reg = registroClienteRepository.findById(registroId)
                .orElseThrow(() -> new IllegalArgumentException("Registro no encontrado con ID: " + registroId));

        if (impulsadorId != null && reg.getImpulsadorId() != null && !reg.getImpulsadorId().equals(impulsadorId)) {
            throw new IllegalArgumentException("No tiene permisos para subsanar este registro.");
        }

        if (fotoTag != null && !fotoTag.isEmpty()) reg.setFotoTag(guardarArchivo(fotoTag, "tag"));
        if (fotoPoliza != null && !fotoPoliza.isEmpty()) reg.setFotoPoliza(guardarArchivo(fotoPoliza, "poliza"));
        if (fotoNota != null && !fotoNota.isEmpty()) reg.setFotoNota(guardarArchivo(fotoNota, "nota"));
        if (fotoCiAnverso != null && !fotoCiAnverso.isEmpty()) reg.setFotoCiAnverso(guardarArchivo(fotoCiAnverso, "ci_anverso"));
        if (fotoCiReverso != null && !fotoCiReverso.isEmpty()) reg.setFotoCiReverso(guardarArchivo(fotoCiReverso, "ci_reverso"));

        // Al subsanar, vuelve a estado PENDIENTE para revisión del supervisor
        reg.setEstado(RegistroCliente.EstadoRegistro.PENDIENTE);
        reg.setMotivoObservacion("Documentos subsanados por el operador de bandeo. Pendiente de re-verificación.");
        RegistroCliente updated = registroClienteRepository.save(reg);

        auditoriaService.log("BANDEO_" + impulsadorId, "SUBSANACION_IMPULSADOR", "Impulsador " + impulsadorId + " subsanó respaldos del registro " + reg.getId(), "RegistroCliente", reg.getId().toString());
        return updated;
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
