package com.ninabit.bono.modules.clientcampaign;

import com.ninabit.bono.modules.clientcampaign.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/cliente")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cliente Final - Público")
public class CampanaClientePublicController {

    private final RegistroClienteService registroClienteService;
    private final CampanaClienteRepository campanaClienteRepository;
    private final CampanaClienteProductoRepository campanaClienteProductoRepository;

    @GetMapping("/campana-activa")
    @Operation(summary = "Obtener información de la campaña de cliente final activa")
    public ResponseEntity<Map<String, Object>> obtenerCampanaActiva() {
        Optional<CampanaCliente> campanaOpt = campanaClienteRepository.findCurrentActiveCampaign(LocalDate.now());
        if (campanaOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("activa", false, "mensaje", "No hay campañas activas actualmente."));
        }

        CampanaCliente campana = campanaOpt.get();
        List<CampanaClienteProducto> productos = campanaClienteProductoRepository
                .findByCampanaClienteIdAndActivoTrue(campana.getId());

        List<CampanaClienteProductoDto> productosDto = productos.stream()
                .map(CampanaClienteProductoDto::fromEntity)
                .toList();

        Map<String, Object> data = new HashMap<>();
        data.put("activa", true);
        data.put("campana", CampanaClienteDto.fromEntity(campana));
        data.put("productos", productosDto);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/validar-serial/{serial}")
    @Operation(summary = "Validar serial/TAG para registro de cliente final")
    public ResponseEntity<Map<String, Object>> validarSerial(
            @PathVariable String serial,
            @RequestParam(required = false) Long campanaId
    ) {
        return ResponseEntity.ok(registroClienteService.validarSerialParaCliente(serial, campanaId));
    }

    @PostMapping(value = "/registro-publico", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Registro público de compra por Landing Web con 5 respaldos")
    public ResponseEntity<Map<String, Object>> registrarCompraWeb(
            @RequestParam Long campanaId,
            @RequestParam String nombreCliente,
            @RequestParam String apellidoCliente,
            @RequestParam(required = false) String ci,
            @RequestParam String celular,
            @RequestParam(required = false) String email,
            @RequestParam String ciudad,
            @RequestParam(required = false) String puntoVenta,
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) String productoModelo,
            @RequestParam(required = false) Integer productoPulgadas,
            @RequestParam String serial,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaCompra,
            @RequestPart(value = "fotoTag", required = false) MultipartFile fotoTag,
            @RequestPart(value = "fotoPoliza", required = false) MultipartFile fotoPoliza,
            @RequestPart(value = "fotoNota", required = false) MultipartFile fotoNota,
            @RequestPart(value = "fotoCiAnverso", required = false) MultipartFile fotoCiAnverso,
            @RequestPart(value = "fotoCiReverso", required = false) MultipartFile fotoCiReverso
    ) {
        try {
            RegistroCliente registro = registroClienteService.registrarCompraCliente(
                    campanaId,
                    RegistroCliente.CanalRegistro.WEB,
                    null,
                    "WEB_PUBLICO",
                    nombreCliente,
                    apellidoCliente,
                    ci,
                    celular,
                    email,
                    ciudad,
                    puntoVenta,
                    productoId,
                    productoModelo,
                    productoPulgadas,
                    serial,
                    fechaCompra != null ? fechaCompra : LocalDate.now(),
                    fotoTag,
                    fotoPoliza,
                    fotoNota,
                    fotoCiAnverso,
                    fotoCiReverso
            );
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "mensaje", "Registro completado con éxito. Tus tickets están pendientes de validación por el supervisor.",
                    "registroId", registro.getId()
            ));
        } catch (Exception e) {
            log.error("Error en registro público de cliente", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/consultar-tickets/{ci}")
    @Operation(summary = "Consulta pública de tickets y estado de registros por CI")
    public ResponseEntity<ConsultarTicketsResponseDto> consultarTickets(@PathVariable String ci) {
        return ResponseEntity.ok(registroClienteService.consultarTicketsPorCi(ci));
    }

    @PostMapping(value = "/subsanar-observacion", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subsanar y resubir documentos de un registro observado")
    public ResponseEntity<Map<String, Object>> subsanarObservacion(
            @RequestParam Long registroId,
            @RequestParam String ci,
            @RequestPart(value = "fotoTag", required = false) MultipartFile fotoTag,
            @RequestPart(value = "fotoPoliza", required = false) MultipartFile fotoPoliza,
            @RequestPart(value = "fotoNota", required = false) MultipartFile fotoNota,
            @RequestPart(value = "fotoCiAnverso", required = false) MultipartFile fotoCiAnverso,
            @RequestPart(value = "fotoCiReverso", required = false) MultipartFile fotoCiReverso
    ) {
        try {
            RegistroCliente actualizado = registroClienteService.subsanarObservacion(
                    registroId, ci, fotoTag, fotoPoliza, fotoNota, fotoCiAnverso, fotoCiReverso
            );
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "mensaje", "Documentos subsanados correctamente. Tu registro pasará nuevamente a revisión.",
                    "registro", RegistroClienteDto.fromEntity(actualizado)
            ));
        } catch (Exception e) {
            log.error("Error al subsanar observación", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/chatbot-webhook")
    @Operation(summary = "Webhook API para registro guiado por Chatbot / WhatsApp")
    public ResponseEntity<Map<String, Object>> chatbotWebhook(@RequestBody Map<String, Object> payload) {
        try {
            Long campanaId = payload.get("campanaId") != null ? Long.valueOf(payload.get("campanaId").toString()) : null;
            if (campanaId == null) {
                Optional<CampanaCliente> activa = campanaClienteRepository.findCurrentActiveCampaign(LocalDate.now());
                if (activa.isPresent()) {
                    campanaId = activa.get().getId();
                } else {
                    return ResponseEntity.badRequest().body(Map.of("success", false, "error", "No hay campaña activa para chatbot"));
                }
            }

            String nombre = (String) payload.get("nombreCliente");
            String apellido = (String) payload.get("apellidoCliente");
            String ci = (String) payload.get("ci");
            String celular = (String) payload.get("celular");
            String ciudad = (String) payload.get("ciudad");
            String serial = (String) payload.get("serial");

            RegistroCliente reg = registroClienteService.registrarCompraCliente(
                    campanaId,
                    RegistroCliente.CanalRegistro.CHATBOT,
                    null,
                    "CHATBOT_WHATSAPP",
                    nombre != null ? nombre : "Cliente",
                    apellido != null ? apellido : "Chatbot",
                    ci != null ? ci : "",
                    celular != null ? celular : "",
                    (String) payload.get("email"),
                    ciudad != null ? ciudad : "Santa Cruz",
                    (String) payload.get("puntoVenta"),
                    null,
                    (String) payload.get("productoModelo"),
                    null,
                    serial,
                    LocalDate.now(),
                    null, null, null, null, null
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "mensaje", "Registro recibido vía Chatbot. Código: " + reg.getId(),
                    "registroId", reg.getId()
            ));
        } catch (Exception e) {
            log.error("Error en chatbot webhook", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
