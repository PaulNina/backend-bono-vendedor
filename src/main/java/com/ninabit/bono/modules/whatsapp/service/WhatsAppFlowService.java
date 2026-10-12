package com.ninabit.bono.modules.whatsapp.service;

import com.ninabit.bono.modules.clientcampaign.CampanaCliente;
import com.ninabit.bono.modules.clientcampaign.CampanaClienteRepository;
import com.ninabit.bono.modules.clientcampaign.RegistroCliente;
import com.ninabit.bono.modules.clientcampaign.RegistroClienteService;
import com.ninabit.bono.modules.clientcampaign.dto.ConsultarTicketsResponseDto;
import com.ninabit.bono.modules.clientcampaign.dto.RegistroClienteDto;
import com.ninabit.bono.modules.clientcampaign.dto.TicketClienteDto;
import com.ninabit.bono.modules.whatsapp.entity.ChatbotConfig;
import com.ninabit.bono.modules.whatsapp.entity.MensajeWhatsapp;
import com.ninabit.bono.modules.whatsapp.entity.MensajeWhatsapp.TipoMensaje;
import com.ninabit.bono.modules.whatsapp.entity.WhatsAppRegistroSesion;
import com.ninabit.bono.modules.whatsapp.entity.WhatsAppRegistroSesion.EstadoSesion;
import com.ninabit.bono.modules.whatsapp.repository.ChatbotConfigRepository;
import com.ninabit.bono.modules.whatsapp.repository.WhatsAppRegistroSesionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppFlowService {

    private final WhatsAppRegistroSesionRepository sesionRepo;
    private final CampanaClienteRepository campanaRepo;
    private final ChatbotConfigRepository configRepo;
    private final ChatbotService chatbotService;

    @org.springframework.beans.factory.annotation.Autowired
    @Lazy
    private RegistroClienteService registroClienteService;

    @org.springframework.beans.factory.annotation.Autowired
    @Lazy
    private WhatsAppService whatsAppService;

    /**
     * Procesa asíncronamente cualquier mensaje entrante de WhatsApp:
     * - Saludo inicial con botones interactivos
     * - Flujo de registro guiado en la menor cantidad de pasos
     * - Consulta de tickets por C.I.
     * - Si no aplica a los flujos anteriores, delega al Chatbot IA / FAQs
     */
    @Async
    @Transactional
    public void procesarMensajeAsync(MensajeWhatsapp mensaje) {
        String telefono = mensaje.getFromNumber();
        String contenido = mensaje.getContenido() != null ? mensaje.getContenido().trim() : "";
        String phoneId = mensaje.getPhoneNumberId();
        TipoMensaje tipo = mensaje.getTipo() != null ? mensaje.getTipo() : TipoMensaje.TEXTO;

        log.info("Procesando mensaje WA de {} [tipo={}, contenido='{}']", telefono, tipo, contenido);

        // 1. Obtener o consultar sesión activa del cliente
        WhatsAppRegistroSesion sesion = sesionRepo.findByTelefono(telefono).orElse(null);

        // 2. Si el usuario escribe cancelar, reiniciar o salir -> limpiar sesión y mostrar menú
        if (esComandoCancelar(contenido)) {
            if (sesion != null) {
                sesionRepo.delete(sesion);
            }
            whatsAppService.actualizarEstadoConversacion(telefono, com.ninabit.bono.modules.whatsapp.entity.WhatsAppConversacion.EstadoConversacion.ABIERTA);
            enviarMenuPrincipal(telefono, phoneId, "Operación cancelada. ¿En qué podemos ayudarte?");
            return;
        }

        // 3. Si pulsa botón o escribe comando directo de REGISTRAR EQUIPO
        if (esComandoRegistrarEquipo(contenido)) {
            iniciarFlujoRegistro(telefono, phoneId);
            return;
        }

        // 4. Si pulsa botón o escribe comando directo de SABER MIS TICKETS
        if (esComandoSaberTickets(contenido)) {
            iniciarFlujoConsultaTickets(telefono, phoneId);
            return;
        }

        // 5. Si tiene una sesión activa en progreso:
        if (sesion != null && sesion.getEstado() != null && sesion.getEstado() != EstadoSesion.INICIO && sesion.getEstado() != EstadoSesion.COMPLETADO) {
            boolean handled = manejarPasoActivo(sesion, mensaje, telefono, phoneId, tipo, contenido);
            if (handled) return;
        }

        // 6. Si es saludo genérico o inicio de conversación -> Enviar Menú con Botones
        if (esSaludo(contenido)) {
            enviarMenuPrincipal(telefono, phoneId, null);
            return;
        }

        // 7. Si no pertenece a ningún flujo anterior, delegar al Chatbot IA / FAQs
        if (tipo == TipoMensaje.TEXTO && !contenido.isBlank()) {
            log.info("Mensaje de {} no corresponde al flujo guiado. Delegando al Chatbot IA.", telefono);
            chatbotService.responderAsync(mensaje);
        }
    }

    // ── Enviar Menú Inicial con Botones Interactivos ─────────────────
    public void enviarMenuPrincipal(String to, String fromPhoneId, String saludoPersonalizado) {
        String texto;
        if (saludoPersonalizado != null && !saludoPersonalizado.isBlank()) {
            texto = saludoPersonalizado;
        } else {
            texto = "¡Hola! 👋 Bienvenido/a a *Skyworth Bolivia* 📺✨\n" +
                    "Participa en sorteos quincenales y en la gran tómbola por *Bs 100.000* en premios.\n\n" +
                    "💡 *¿Quieres registrar tu televisor?*\n" +
                    "Te recomendamos hacerlo desde nuestra *página web* — es más rápido, gratuito y sin consumir datos de WhatsApp:\n" +
                    "👉 https://bono.skyworth.bo/registro-cliente\n\n" +
                    "También puedes hacerlo aquí por WhatsApp si lo prefieres.";
        }

        texto += "\n\n*¿En qué podemos ayudarte?*";

        List<Map<String, String>> botones = List.of(
                Map.of("id", "BTN_REGISTRAR_EQUIPO", "title", "Registrar por WhatsApp"),
                Map.of("id", "BTN_SABER_TICKETS", "title", "Consultar mis tickets")
        );

        whatsAppService.enviarMensajeBotones(to, texto, botones, fromPhoneId);
    }

    // ── Iniciar Flujo de Registro ────────────────────────────────────
    private void iniciarFlujoRegistro(String telefono, String fromPhoneId) {
        Optional<CampanaCliente> campanaOpt = campanaRepo.findCurrentActiveCampaign(LocalDate.now());
        if (campanaOpt.isEmpty()) {
            whatsAppService.enviarMensajeTexto(telefono,
                    "ℹ️ En este momento no hay una campaña de sorteos abierta para clientes. ¡Mantente atento a nuestras redes oficiales para los próximos lanzamientos de Skyworth! 📺",
                    fromPhoneId, true);
            return;
        }

        ChatbotConfig.ModoRegistroWa modo = obtenerModoRegistro();
        if (modo == ChatbotConfig.ModoRegistroWa.SOLO_WEB) {
            String msgWeb = obtenerMensajeRedireccionWeb();
            if (msgWeb == null || msgWeb.isBlank()) {
                msgWeb = "📺 *Registro de Compra Skyworth*\n\n" +
                        "Para brindarte la mayor comodidad y rapidez cargando tus fotos, el registro de compras se realiza exclusivamente a través de nuestra web oficial:\n\n" +
                        "👉 https://bono.skyworth.bo/registro-cliente\n\n" +
                        "¡Es rápido, seguro y no consume datos de WhatsApp! Al registrarte entrarás inmediatamente al sorteo por *Bs 100.000* en premios 🏆";
            }
            whatsAppService.enviarMensajeTexto(telefono, msgWeb, fromPhoneId, true);
            return;
        }

        WhatsAppRegistroSesion sesion = sesionRepo.findByTelefono(telefono).orElse(new WhatsAppRegistroSesion());
        sesion.setTelefono(telefono);
        sesion.setFromPhoneNumberId(fromPhoneId);
        sesion.setCampanaId(campanaOpt.get().getId());
        sesion.setEstado(EstadoSesion.ESPERANDO_SERIAL);
        sesion.setSerial(null);
        sesion.setFotosRecibidas(0);
        sesion.setFotoTag(null);
        sesion.setFotoPoliza(null);
        sesion.setFotoNota(null);
        sesion.setFotoCiAnverso(null);
        sesion.setFotoCiReverso(null);
        sesionRepo.save(sesion);
        whatsAppService.actualizarEstadoConversacion(telefono, com.ninabit.bono.modules.whatsapp.entity.WhatsAppConversacion.EstadoConversacion.EN_REGISTRO);

        String msg = "📺 *Registro de Compra Skyworth por WhatsApp*\n" +
                "Participa por *Bs 100.000* en premios 🏆\n\n" +
                "⚡ *Tip:* Si tienes acceso a internet, el registro por la *página web* es más rápido y no consume datos de WhatsApp:\n" +
                "👉 https://bono.skyworth.bo/registro-cliente\n\n" +
                "━━━━━━━━━━━━━━━━━━━━\n" +
                "Si prefieres continuar aquí, escribe el *Número de Serie o TAG* de tu televisor Skyworth.\n" +
                "Lo encuentras en la etiqueta trasera del televisor o en la caja del producto.\n\n" +
                "_(Ejemplo: SKY-55G22-00123. Escribe 'cancelar' para salir)_";

        whatsAppService.enviarMensajeTexto(telefono, msg, fromPhoneId, true);
    }

    // ── Iniciar Flujo de Consulta de Tickets ─────────────────────────
    private void iniciarFlujoConsultaTickets(String telefono, String fromPhoneId) {
        WhatsAppRegistroSesion sesion = sesionRepo.findByTelefono(telefono).orElse(new WhatsAppRegistroSesion());
        sesion.setTelefono(telefono);
        sesion.setFromPhoneNumberId(fromPhoneId);
        sesion.setEstado(EstadoSesion.CONSULTA_TICKETS);
        sesionRepo.save(sesion);

        String msg = "🎫 *Consulta de Tickets y Sorteos Skyworth*\n\n" +
                "Por favor, escribe tu número de *Cédula de Identidad (C.I.)* para buscar tus compras y tickets registrados:\n\n" +
                "_(Ejemplo: 8492019)_";

        whatsAppService.enviarMensajeTexto(telefono, msg, fromPhoneId, true);
    }

    // ── Manejo de pasos de la sesión ────────────────────────────────
    private boolean manejarPasoActivo(WhatsAppRegistroSesion sesion, MensajeWhatsapp mensaje,
                                     String telefono, String fromPhoneId, TipoMensaje tipo, String contenido) {
        EstadoSesion estado = sesion.getEstado();

        switch (estado) {
            case CONSULTA_TICKETS -> {
                procesarConsultaTickets(sesion, telefono, fromPhoneId, contenido);
                return true;
            }
            case ESPERANDO_SERIAL -> {
                procesarPasoSerial(sesion, telefono, fromPhoneId, contenido);
                return true;
            }
            case ESPERANDO_NOMBRE -> {
                procesarPasoNombre(sesion, telefono, fromPhoneId, contenido);
                return true;
            }
            case ESPERANDO_CI -> {
                procesarPasoCi(sesion, telefono, fromPhoneId, contenido);
                return true;
            }
            case ESPERANDO_CIUDAD -> {
                procesarPasoCiudad(sesion, telefono, fromPhoneId, contenido);
                return true;
            }
            case ESPERANDO_TIENDA -> {
                procesarPasoTienda(sesion, telefono, fromPhoneId, contenido);
                return true;
            }
            case ESPERANDO_DATOS -> {
                procesarPasoDatos(sesion, telefono, fromPhoneId, contenido);
                return true;
            }
            case ESPERANDO_FOTOS -> {
                procesarPasoFotos(sesion, mensaje, telefono, fromPhoneId, tipo, contenido);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // ── Paso: Consulta de tickets por CI ─────────────────────────────
    private void procesarConsultaTickets(WhatsAppRegistroSesion sesion, String telefono, String fromPhoneId, String ciInput) {
        String cleanCi = ciInput != null ? ciInput.replaceAll("[^0-9A-Za-z-]", "").trim() : "";
        if (cleanCi.length() < 4) {
            whatsAppService.enviarMensajeTexto(telefono, "⚠️ Por favor ingresa un número de C.I. válido (ejemplo: 8492019):", fromPhoneId, true);
            return;
        }

        try {
            ConsultarTicketsResponseDto res = registroClienteService.consultarTicketsPorCi(cleanCi);
            if (res.getTotalTickets() == 0 && (res.getRegistros() == null || res.getRegistros().isEmpty())) {
                String noEncontrado = "🔍 No encontramos compras ni tickets activos registrados con la C.I. *" + cleanCi + "*.\n\n" +
                        "Si compraste un televisor Skyworth, ¡regístralo ahora y participa en los sorteos por *Bs 100.000*!";
                
                sesion.setEstado(EstadoSesion.INICIO);
                sesionRepo.save(sesion);

                List<Map<String, String>> botones = List.of(
                        Map.of("id", "BTN_REGISTRAR_EQUIPO", "title", "Registrar equipo"),
                        Map.of("id", "BTN_SABER_TICKETS", "title", "Consultar otra C.I.")
                );
                whatsAppService.enviarMensajeBotones(telefono, noEncontrado, botones, fromPhoneId);
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("🎉 *Tus Tickets y Compras Skyworth*\n\n");
            sb.append("👤 *C.I.:* ").append(cleanCi).append("\n");
            sb.append("🎟️ *Total de Tickets:* ").append(res.getTotalTickets()).append("\n");
            sb.append("✅ *Tickets Habilitados:* ").append(res.getTicketsHabilitados()).append("\n\n");

            if (res.getRegistros() != null && !res.getRegistros().isEmpty()) {
                sb.append("📋 *Tus Equipos Registrados:*\n");
                for (RegistroClienteDto r : res.getRegistros()) {
                    String mod = r.getProductoModelo() != null ? r.getProductoModelo() : "Televisor Skyworth";
                    sb.append("• *").append(mod).append("*\n");
                    sb.append("  Serie: `").append(r.getSerial()).append("`\n");
                    sb.append("  Estado: *").append(r.getEstado() != null ? r.getEstado() : "PENDIENTE").append("*\n");
                    if (r.getCiudad() != null && !r.getCiudad().isBlank()) {
                        sb.append("  Ciudad: ").append(r.getCiudad()).append("\n");
                    }
                }
            }

            if (res.getTickets() != null && !res.getTickets().isEmpty()) {
                sb.append("\n🎟️ *Códigos de Ticket:*\n");
                int limit = Math.min(res.getTickets().size(), 8);
                for (int i = 0; i < limit; i++) {
                    TicketClienteDto t = res.getTickets().get(i);
                    String estadoStr = t.getEstado() != null ? t.getEstado().toUpperCase() : "NO_HABILITADO";
                    String icono = estadoStr.contains("HABILITADO") ? "🟢" : "🟡";
                    sb.append(icono).append(" `").append(t.getCodigoTicket()).append("` (").append(estadoStr).append(")\n");
                }
                if (res.getTickets().size() > 8) {
                    sb.append("... y ").append(res.getTickets().size() - 8).append(" ticket(s) más.\n");
                }
            }

            sb.append("\n🌐 Consulta el estado detallado y fechas de sorteos en:\nhttps://bono.skyworth.bo/mis-tickets");

            sesion.setEstado(EstadoSesion.INICIO);
            sesionRepo.save(sesion);

            whatsAppService.enviarMensajeTexto(telefono, sb.toString(), fromPhoneId, true);

        } catch (Exception e) {
            log.error("Error al consultar tickets en WhatsApp para CI {}", cleanCi, e);
            whatsAppService.enviarMensajeTexto(telefono, "Ocurrió un error al buscar tus tickets. Por favor intenta más tarde o visita https://bono.skyworth.bo/mis-tickets", fromPhoneId, true);
        }
    }

    // ── Paso: Validación de Serial del Televisor ─────────────────────
    private void procesarPasoSerial(WhatsAppRegistroSesion sesion, String telefono, String fromPhoneId, String serialInput) {
        String cleanSerial = serialInput != null ? serialInput.trim().toUpperCase() : "";
        if (cleanSerial.length() < 3) {
            whatsAppService.enviarMensajeTexto(telefono, "⚠️ Por favor ingresa el número de serie completo de tu televisor Skyworth (ej: SKY-55G22-00123):", fromPhoneId, true);
            return;
        }

        try {
            Map<String, Object> valRes = registroClienteService.validarSerialParaCliente(cleanSerial, sesion.getCampanaId());
            boolean valido = Boolean.TRUE.equals(valRes.get("valido"));

            if (!valido) {
                String errorMsg = (String) valRes.getOrDefault("mensaje", "El serial no es válido o ya fue registrado.");
                whatsAppService.enviarMensajeTexto(telefono,
                        "❌ *Serial no disponible:* " + errorMsg + "\n\nPor favor verifica tu número de serie e intenta nuevamente (o escribe 'cancelar' para salir):",
                        fromPhoneId, true);
                return;
            }

            // Serial válido
            Long productoId = valRes.get("productoId") != null ? Long.valueOf(valRes.get("productoId").toString()) : null;
            String modelo = valRes.get("modelo") != null ? valRes.get("modelo").toString() : "Skyworth Smart TV";
            Integer pulgadas = valRes.get("pulgadas") != null ? Integer.valueOf(valRes.get("pulgadas").toString()) : 0;
            Integer tickets = valRes.get("ticketsProyectados") != null ? Integer.valueOf(valRes.get("ticketsProyectados").toString()) : 1;

            sesion.setSerial(cleanSerial);
            sesion.setProductoId(productoId);
            sesion.setProductoModelo(modelo);
            sesion.setProductoPulgadas(pulgadas);
            sesion.setTicketsProyectados(tickets);

            ChatbotConfig.ModoRegistroWa modo = obtenerModoRegistro();

            if (modo == ChatbotConfig.ModoRegistroWa.COMPACTO) {
                sesion.setEstado(EstadoSesion.ESPERANDO_DATOS);
                sesionRepo.save(sesion);

                String promptDatos = "✅ *¡Serial Verificado Exitosamente!*\n" +
                        "Televisor: *" + modelo + (pulgadas > 0 ? " (" + pulgadas + "\")*" : "*") + "\n" +
                        "Tickets a recibir: *" + tickets + " ticket(s)* 🎟️\n\n" +
                        "━━━━━━━━━━━━━━━━━━━━\n" +
                        "📋 *Ingresa tus datos personales en un solo mensaje:*\n\n" +
                        "Por favor escribe en un solo mensaje tus datos siguiendo este orden:\n" +
                        "• *Nombre y Apellido*\n" +
                        "• *C.I.*\n" +
                        "• *Ciudad*\n" +
                        "• *Tienda o Comercio donde compraste*\n\n" +
                        "*Ejemplo:* Juan Pérez, 8492019, Santa Cruz, Tienda Bolívar\n" +
                        "_(O escribe cada dato en una línea)_";

                whatsAppService.enviarMensajeTexto(telefono, promptDatos, fromPhoneId, true);
            } else {
                // Modo PASO_A_PASO (por defecto)
                sesion.setEstado(EstadoSesion.ESPERANDO_NOMBRE);
                sesionRepo.save(sesion);

                String promptPaso1 = "✅ *¡Serial Verificado Exitosamente!*\n" +
                        "Televisor: *" + modelo + (pulgadas > 0 ? " (" + pulgadas + "\")*" : "*") + "\n" +
                        "Tickets a recibir: *" + tickets + " ticket(s)* 🎟️\n\n" +
                        "━━━━━━━━━━━━━━━━━━━━\n" +
                        "👤 *Paso 1 de 4: Nombre Completo*\n\n" +
                        "Por favor escribe tu *Nombre y Apellidos*:\n" +
                        "_(Ejemplo: Juan Pérez Gómez)_";

                whatsAppService.enviarMensajeTexto(telefono, promptPaso1, fromPhoneId, true);
            }

        } catch (Exception e) {
            log.error("Error al validar serial {} en WhatsApp", cleanSerial, e);
            whatsAppService.enviarMensajeTexto(telefono, "Ocurrió un error al verificar el serial. Por favor intenta de nuevo.", fromPhoneId, true);
        }
    }

    // ── Pasos Modo Paso a Paso ───────────────────────────────────────
    private void procesarPasoNombre(WhatsAppRegistroSesion sesion, String telefono, String fromPhoneId, String nombreInput) {
        String clean = nombreInput != null ? nombreInput.trim() : "";
        if (clean.length() < 2) {
            whatsAppService.enviarMensajeTexto(telefono, "⚠️ Por favor ingresa tu Nombre y Apellido completo:", fromPhoneId, true);
            return;
        }

        String[] parts = clean.split("\\s+");
        String nombre = parts[0];
        String apellido = parts.length > 1 ? String.join(" ", Arrays.copyOfRange(parts, 1, parts.length)) : "Cliente";

        sesion.setNombreCliente(nombre);
        sesion.setApellidoCliente(apellido);
        sesion.setEstado(EstadoSesion.ESPERANDO_CI);
        sesionRepo.save(sesion);

        String promptCi = "✅ Registrado: *" + nombre + " " + apellido + "*\n\n" +
                "🆔 *Paso 2 de 4: Cédula de Identidad (C.I.)*\n\n" +
                "Escribe tu número de *Cédula de Identidad*:\n" +
                "_(Ejemplo: 8492019 o 8492019 SC)_";
        whatsAppService.enviarMensajeTexto(telefono, promptCi, fromPhoneId, true);
    }

    private void procesarPasoCi(WhatsAppRegistroSesion sesion, String telefono, String fromPhoneId, String ciInput) {
        String clean = ciInput != null ? ciInput.replaceAll("[^0-9A-Za-z- ]", "").trim() : "";
        if (clean.length() < 4) {
            whatsAppService.enviarMensajeTexto(telefono, "⚠️ Por favor ingresa un número de C.I. válido (ejemplo: 8492019):", fromPhoneId, true);
            return;
        }

        sesion.setCi(clean);
        sesion.setEstado(EstadoSesion.ESPERANDO_CIUDAD);
        sesionRepo.save(sesion);

        String promptCiudad = "✅ C.I.: *" + clean + "*\n\n" +
                "📍 *Paso 3 de 4: Ciudad*\n\n" +
                "¿En qué ciudad te encuentras?\n" +
                "_(Ejemplo: Santa Cruz, La Paz, Cochabamba, El Alto, Sucre, Tarija, Oruro, Potosí, Trinidad, Cobija)_";
        whatsAppService.enviarMensajeTexto(telefono, promptCiudad, fromPhoneId, true);
    }

    private void procesarPasoCiudad(WhatsAppRegistroSesion sesion, String telefono, String fromPhoneId, String ciudadInput) {
        String clean = ciudadInput != null ? ciudadInput.trim() : "";
        if (clean.length() < 2) {
            whatsAppService.enviarMensajeTexto(telefono, "⚠️ Por favor escribe el nombre de tu ciudad (ej: Santa Cruz, La Paz, Cochabamba):", fromPhoneId, true);
            return;
        }

        sesion.setCiudad(clean);
        sesion.setEstado(EstadoSesion.ESPERANDO_TIENDA);
        sesionRepo.save(sesion);

        String promptTienda = "✅ Ciudad: *" + clean + "*\n\n" +
                "🏬 *Paso 4 de 4: Tienda o Comercio*\n\n" +
                "¿En qué tienda, distribuidor o comercio compraste tu televisor Skyworth?\n" +
                "_(Ejemplo: Tienda Bolívar, Mercado Los Pozos, Comercial Norte, etc.)_";
        whatsAppService.enviarMensajeTexto(telefono, promptTienda, fromPhoneId, true);
    }

    private void procesarPasoTienda(WhatsAppRegistroSesion sesion, String telefono, String fromPhoneId, String tiendaInput) {
        String clean = tiendaInput != null ? tiendaInput.trim() : "";
        if (clean.length() < 2) {
            whatsAppService.enviarMensajeTexto(telefono, "⚠️ Por favor escribe el nombre de la tienda o comercio donde compraste:", fromPhoneId, true);
            return;
        }

        sesion.setPuntoVenta(clean);
        sesion.setCelular(telefono);
        sesion.setEstado(EstadoSesion.ESPERANDO_FOTOS);
        sesion.setFotosRecibidas(0);
        sesionRepo.save(sesion);

        enviarInstruccionesFotos(sesion, telefono, fromPhoneId);
    }

    // ── Paso: Datos del Cliente en un Solo Mensaje (Modo COMPACTO) ─────
    private void procesarPasoDatos(WhatsAppRegistroSesion sesion, String telefono, String fromPhoneId, String datosInput) {
        ParsedClienteData parsed = parsearDatosCliente(datosInput, telefono);

        if (parsed.nombre == null || parsed.nombre.isBlank() || parsed.ci == null || parsed.ci.isBlank()) {
            whatsAppService.enviarMensajeTexto(telefono,
                    "⚠️ No pudimos identificar tu Nombre o C.I. claramente.\n\nPor favor envíalos en este formato en un solo mensaje:\n\n*Juan Pérez, 8492019, Santa Cruz, Tienda Bolívar*\n\n_(Nombre Apellido, C.I., Ciudad, Tienda)_",
                    fromPhoneId, true);
            return;
        }

        sesion.setNombreCliente(parsed.nombre);
        sesion.setApellidoCliente(parsed.apellido != null && !parsed.apellido.isBlank() ? parsed.apellido : "Cliente");
        sesion.setCi(parsed.ci);
        sesion.setCiudad(parsed.ciudad != null && !parsed.ciudad.isBlank() ? parsed.ciudad : "Santa Cruz");
        sesion.setPuntoVenta(parsed.puntoVenta != null && !parsed.puntoVenta.isBlank() ? parsed.puntoVenta : "Comercio Autorizado");
        sesion.setCelular(parsed.celular != null && !parsed.celular.isBlank() ? parsed.celular : telefono);
        sesion.setEstado(EstadoSesion.ESPERANDO_FOTOS);
        sesion.setFotosRecibidas(0);
        sesionRepo.save(sesion);

        enviarInstruccionesFotos(sesion, telefono, fromPhoneId);
    }

    // ── Helper: Instrucciones de Fotografías ──────────────────────────
    private void enviarInstruccionesFotos(WhatsAppRegistroSesion sesion, String telefono, String fromPhoneId) {
        int req = obtenerCantidadFotosRequeridas();
        StringBuilder sb = new StringBuilder();
        sb.append("📝 *Datos registrados correctamente:*\n");
        sb.append("• Cliente: *").append(sesion.getNombreCliente()).append(" ").append(sesion.getApellidoCliente()).append("*\n");
        sb.append("• C.I.: *").append(sesion.getCi()).append("*\n");
        sb.append("• Ciudad: *").append(sesion.getCiudad()).append("*\n");
        sb.append("• Tienda: *").append(sesion.getPuntoVenta()).append("*\n");
        sb.append("• Celular: *").append(sesion.getCelular()).append("*\n\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📸 *ÚLTIMO PASO: Fotografías de Respaldo*\n\n");
        sb.append("Para que el supervisor valide tu compra y habilite tus tickets, envía las *").append(req).append(" fotos* requeridas (puedes enviarlas juntas o una por una):\n\n");

        List<String> descList = getDescripcionFotosRequeridas(req);
        for (String d : descList) {
            sb.append(d).append("\n");
        }
        sb.append("\n📷 *Envía tus fotos a continuación:*");

        whatsAppService.enviarMensajeTexto(telefono, sb.toString(), fromPhoneId, true);
    }

    // ── Paso: Recepción de Fotografías de Respaldo ───────────────────
    private void procesarPasoFotos(WhatsAppRegistroSesion sesion, MensajeWhatsapp mensaje,
                                   String telefono, String fromPhoneId, TipoMensaje tipo, String contenido) {
        int req = obtenerCantidadFotosRequeridas();

        // Si el usuario envía texto mientras espera fotos
        if (tipo != TipoMensaje.IMAGEN) {
            String clean = contenido != null ? contenido.toLowerCase().trim() : "";
            if (clean.equals("listo") || clean.equals("finalizar") || clean.equals("terminar")) {
                if (sesion.getFotosRecibidas() >= 1) {
                    finalizarRegistro(sesion, telefono, fromPhoneId);
                    return;
                } else {
                    whatsAppService.enviarMensajeTexto(telefono, "📷 Aún no has enviado ninguna fotografía. Por favor envía las fotos de respaldo de tu compra.", fromPhoneId, true);
                    return;
                }
            }
            if (clean.equals("estado") || clean.equals("fotos") || clean.equals("cuantas")) {
                whatsAppService.enviarMensajeTexto(telefono, "Llevas *" + sesion.getFotosRecibidas() + " de " + req + "* fotografías enviadas. Por favor envía las fotos restantes o escribe 'listo' para finalizar.", fromPhoneId, true);
                return;
            }
            whatsAppService.enviarMensajeTexto(telefono, "📷 Estamos esperando las fotos de respaldo de tu compra. Envía las imágenes por WhatsApp (llevas " + sesion.getFotosRecibidas() + " de " + req + "):", fromPhoneId, true);
            return;
        }

        // Si es una imagen
        String mediaId = mensaje.getMediaId();
        if (mediaId == null || mediaId.isBlank()) {
            whatsAppService.enviarMensajeTexto(telefono, "⚠️ No pudimos procesar la imagen enviada. Por favor envíala de nuevo.", fromPhoneId, true);
            return;
        }

        int currentSlot = sesion.getFotosRecibidas() + 1;
        String prefix;
        String slotNombre;

        if (req == 2) {
            if (currentSlot == 1) {
                prefix = "tag";
                slotNombre = "Etiqueta/TAG del TV";
            } else {
                prefix = "nota";
                slotNombre = "Factura o Nota de Venta";
            }
        } else if (req == 1) {
            prefix = "tag";
            slotNombre = "Factura / Etiqueta TAG";
        } else if (req == 3) {
            prefix = switch (currentSlot) {
                case 1 -> "tag";
                case 2 -> "nota";
                default -> "poliza";
            };
            slotNombre = switch (currentSlot) {
                case 1 -> "Etiqueta/TAG del TV";
                case 2 -> "Factura o Nota de Venta";
                default -> "Póliza / C.I.";
            };
        } else if (req == 4) {
            prefix = switch (currentSlot) {
                case 1 -> "tag";
                case 2 -> "nota";
                case 3 -> "poliza";
                default -> "ci_anverso";
            };
            slotNombre = switch (currentSlot) {
                case 1 -> "Etiqueta/TAG del TV";
                case 2 -> "Factura o Nota de Venta";
                case 3 -> "Póliza de Garantía";
                default -> "C.I.";
            };
        } else {
            prefix = switch (currentSlot) {
                case 1 -> "tag";
                case 2 -> "poliza";
                case 3 -> "nota";
                case 4 -> "ci_anverso";
                default -> "ci_reverso";
            };
            slotNombre = switch (currentSlot) {
                case 1 -> "Etiqueta/TAG del TV";
                case 2 -> "Póliza de Garantía";
                case 3 -> "Nota de Venta / Factura";
                case 4 -> "C.I. Anverso";
                default -> "C.I. Reverso";
            };
        }

        String savedFilename = whatsAppService.descargarYGuardarFoto(mediaId, prefix);
        if (savedFilename == null) {
            whatsAppService.enviarMensajeTexto(telefono, "⚠️ Hubo un inconveniente al descargar la fotografía. Por favor reenvíala.", fromPhoneId, true);
            return;
        }

        if ("tag".equals(prefix)) sesion.setFotoTag(savedFilename);
        else if ("poliza".equals(prefix)) sesion.setFotoPoliza(savedFilename);
        else if ("nota".equals(prefix)) sesion.setFotoNota(savedFilename);
        else if ("ci_anverso".equals(prefix)) sesion.setFotoCiAnverso(savedFilename);
        else sesion.setFotoCiReverso(savedFilename);

        sesion.setFotosRecibidas(currentSlot);
        sesionRepo.save(sesion);

        if (currentSlot < req) {
            String prog = "📸 *Foto " + currentSlot + " de " + req + " recibida* (" + slotNombre + ") ✅\n" +
                    "Quedan *" + (req - currentSlot) + "* foto(s) por enviar.";
            whatsAppService.enviarMensajeTexto(telefono, prog, fromPhoneId, true);
        } else {
            // Se recibieron todas las fotos requeridas -> Finalizar automáticamente
            finalizarRegistro(sesion, telefono, fromPhoneId);
        }
    }

    // ── Helpers de Configuración de Chatbot y Fotos ──────────────────
    private int obtenerCantidadFotosRequeridas() {
        return configRepo.findFirstByOrderByIdAsc()
                .map(c -> c.getCantidadFotosRequeridas() != null && c.getCantidadFotosRequeridas() > 0 ? c.getCantidadFotosRequeridas() : 2)
                .orElse(2);
    }

    private ChatbotConfig.ModoRegistroWa obtenerModoRegistro() {
        return configRepo.findFirstByOrderByIdAsc()
                .map(c -> c.getModoRegistroWa() != null ? c.getModoRegistroWa() : ChatbotConfig.ModoRegistroWa.PASO_A_PASO)
                .orElse(ChatbotConfig.ModoRegistroWa.PASO_A_PASO);
    }

    private String obtenerMensajeRedireccionWeb() {
        return configRepo.findFirstByOrderByIdAsc()
                .map(c -> c.getMensajeRedireccionWeb() != null && !c.getMensajeRedireccionWeb().isBlank() ? c.getMensajeRedireccionWeb() : null)
                .orElse(null);
    }

    private List<String> getDescripcionFotosRequeridas(int totalReq) {
        if (totalReq == 1) {
            return List.of("1️⃣ Foto de la *Factura o Etiqueta/TAG* del televisor");
        }
        if (totalReq == 2) {
            return List.of(
                    "1️⃣ Foto de la *Etiqueta/TAG* trasera del televisor",
                    "2️⃣ Foto de la *Factura o Nota de venta*"
            );
        }
        if (totalReq == 3) {
            return List.of(
                    "1️⃣ Foto de la *Etiqueta/TAG* trasera del televisor",
                    "2️⃣ Foto de la *Factura o Nota de venta*",
                    "3️⃣ Foto de la *Póliza de Garantía o C.I.*"
            );
        }
        if (totalReq == 4) {
            return List.of(
                    "1️⃣ Foto de la *Etiqueta/TAG* trasera del televisor",
                    "2️⃣ Foto de la *Factura o Nota de venta*",
                    "3️⃣ Foto de la *Póliza de Garantía*",
                    "4️⃣ Foto de tu *Cédula de Identidad (C.I.)*"
            );
        }
        return List.of(
                "1️⃣ Foto de la *Etiqueta/TAG* trasera del televisor",
                "2️⃣ Foto de la *Póliza de Garantía*",
                "3️⃣ Foto de la *Factura o Nota de venta*",
                "4️⃣ Foto de tu *C.I. (Anverso / Frente)*",
                "5️⃣ Foto de tu *C.I. (Reverso / Atrás)*"
        );
    }

    // ── Finalización y Guardado Oficial del Registro ─────────────────
    private void finalizarRegistro(WhatsAppRegistroSesion sesion, String telefono, String fromPhoneId) {
        try {
            RegistroCliente reg = registroClienteService.registrarCompraClienteConRutas(
                    sesion.getCampanaId(),
                    RegistroCliente.CanalRegistro.CHATBOT,
                    null,
                    "CHATBOT_WHATSAPP",
                    sesion.getNombreCliente(),
                    sesion.getApellidoCliente(),
                    sesion.getCi(),
                    sesion.getCelular(),
                    null,
                    sesion.getCiudad(),
                    sesion.getPuntoVenta(),
                    sesion.getProductoId(),
                    sesion.getProductoModelo(),
                    sesion.getProductoPulgadas(),
                    sesion.getSerial(),
                    LocalDate.now(),
                    sesion.getFotoTag(),
                    sesion.getFotoPoliza(),
                    sesion.getFotoNota(),
                    sesion.getFotoCiAnverso(),
                    sesion.getFotoCiReverso()
            );

            sesion.setEstado(EstadoSesion.COMPLETADO);
            sesionRepo.save(sesion);
            whatsAppService.actualizarEstadoConversacion(telefono, com.ninabit.bono.modules.whatsapp.entity.WhatsAppConversacion.EstadoConversacion.FINALIZADA);

            String celebracion = "🎉 *¡REGISTRO COMPLETADO EXITOSAMENTE!* 🏆\n\n" +
                    "Estimado(a) *" + sesion.getNombreCliente() + " " + sesion.getApellidoCliente() + "*,\n" +
                    "Tu televisor ha sido registrado en el sistema oficial de sorteos de Skyworth:\n\n" +
                    "📋 *Código de Registro:* #" + reg.getId() + "\n" +
                    "📺 *Equipo:* " + sesion.getProductoModelo() + (sesion.getProductoPulgadas() > 0 ? " (" + sesion.getProductoPulgadas() + "\")" : "") + "\n" +
                    "🔢 *Serie / TAG:* `" + sesion.getSerial() + "`\n" +
                    "🎟️ *Tickets generados:* " + sesion.getTicketsProyectados() + " ticket(s)\n\n" +
                    "🔍 Nuestro equipo de supervisores validará tus respaldos fotográficos para habilitar tus tickets para los sorteos quincenales y la gran tómbola de hasta *Bs 100.000*.\n\n" +
                    "🌐 Puedes consultar el estado de tus tickets en cualquier momento escribiendo *'Mis tickets'* o ingresando a: https://bono.skyworth.bo/mis-tickets\n\n" +
                    "¡Muchas gracias por confiar en Skyworth y buena suerte! 🍀✨";

            whatsAppService.enviarMensajeTexto(telefono, celebracion, fromPhoneId, true);

        } catch (Exception e) {
            log.error("Error al registrar cliente desde WhatsApp para {}", telefono, e);
            whatsAppService.enviarMensajeTexto(telefono,
                    "⚠️ Ocurrió un error al procesar tu registro: " + e.getMessage() + ". Por favor contacta con soporte o intenta en https://bono.skyworth.bo/registro-cliente",
                    fromPhoneId, true);
        }
    }

    // ── Helper: Parser inteligente de datos en un solo mensaje ───────
    private static class ParsedClienteData {
        String nombre;
        String apellido;
        String ci;
        String ciudad;
        String puntoVenta;
        String celular;
    }

    private ParsedClienteData parsearDatosCliente(String text, String defaultCelular) {
        ParsedClienteData data = new ParsedClienteData();
        data.celular = defaultCelular;
        if (text == null || text.isBlank()) return data;

        String raw = text.replace("*", "").trim();

        // 1. Extraer C.I. (secuencia numérica de 6 a 10 dígitos, opcionalmente con sufijo o extensión)
        Pattern ciPattern = Pattern.compile("(?i)\\b(?:ci|c\\.i\\.|cedula|carnet)?\\s*:?\\s*(\\d{6,10}(?:[- ][0-9A-Za-z]{1,3})?)\\b");
        Matcher ciMatcher = ciPattern.matcher(raw);
        if (ciMatcher.find()) {
            data.ci = ciMatcher.group(1).trim();
            raw = raw.replace(ciMatcher.group(0), " ");
        }

        // 2. Extraer Ciudad si figura en el texto
        Pattern ciudadPattern = Pattern.compile("(?i)\\b(Santa Cruz|La Paz|Cochabamba|El Alto|Oruro|Sucre|Tarija|Potosi|Potosí|Beni|Pando|Montero|Trinidad)\\b");
        Matcher ciudadMatcher = ciudadPattern.matcher(raw);
        if (ciudadMatcher.find()) {
            data.ciudad = ciudadMatcher.group(1).trim();
            raw = raw.replace(ciudadMatcher.group(0), " ");
        } else {
            data.ciudad = "Santa Cruz";
        }

        // 3. Separar por comas o saltos de línea
        String[] partes = raw.split("[,\\n]+");
        List<String> tokens = new ArrayList<>();
        for (String p : partes) {
            String t = p.trim().replaceAll("^(?i)(nombre|apellido|nombres|apellidos|tienda|comercio|punto de venta|lugar)\\s*:?\\s*", "");
            if (!t.isBlank()) {
                tokens.add(t);
            }
        }

        if (!tokens.isEmpty()) {
            String primerToken = tokens.get(0);
            String[] palabras = primerToken.split("\\s+");
            if (palabras.length >= 2) {
                data.nombre = palabras[0];
                data.apellido = String.join(" ", Arrays.copyOfRange(palabras, 1, palabras.length));
            } else {
                data.nombre = primerToken;
                if (tokens.size() > 1 && !tokens.get(1).matches("\\d+")) {
                    data.apellido = tokens.get(1);
                    tokens.remove(1);
                } else {
                    data.apellido = "Cliente";
                }
            }

            // Tienda / Punto de venta (si hay tokens restantes)
            if (tokens.size() > 1) {
                String possibleTienda = tokens.get(tokens.size() - 1);
                if (!possibleTienda.equals(data.nombre) && !possibleTienda.equals(data.apellido)) {
                    data.puntoVenta = possibleTienda;
                }
            }
        }

        if (data.puntoVenta == null || data.puntoVenta.isBlank()) {
            data.puntoVenta = "Comercio Autorizado";
        }

        return data;
    }

    // ── Helpers de Detección de Comandos ─────────────────────────────
    private boolean esComandoCancelar(String text) {
        if (text == null) return false;
        String c = text.toLowerCase().trim();
        return c.equals("cancelar") || c.equals("salir") || c.equals("reiniciar") || c.equals("menu") || c.equals("menú");
    }

    private boolean esComandoRegistrarEquipo(String text) {
        if (text == null) return false;
        String c = text.toLowerCase().trim();
        return c.equals("btn_registrar_equipo") ||
               c.equals("registrar equipo") ||
               c.equals("registrar por whatsapp") ||
               c.equals("registrar") ||
               c.equals("registro") ||
               c.equals("1");
    }

    private boolean esComandoSaberTickets(String text) {
        if (text == null) return false;
        String c = text.toLowerCase().trim();
        return c.equals("btn_saber_tickets") ||
               c.equals("saber mis tickets") ||
               c.equals("consultar mis tickets") ||
               c.equals("mis tickets") ||
               c.equals("consultar tickets") ||
               c.equals("tickets") ||
               c.equals("ticket") ||
               c.equals("2");
    }

    private boolean esSaludo(String text) {
        if (text == null) return false;
        String c = text.toLowerCase().trim();
        return c.matches("^(hola|buenas|buenos dias|buen dia|buenos d.as|buenas tardes|buenas noches|inicio|menu|menú|empezar|comenzar|hi|hello|ayuda|info|información)(\\s.*)?$");
    }
}
