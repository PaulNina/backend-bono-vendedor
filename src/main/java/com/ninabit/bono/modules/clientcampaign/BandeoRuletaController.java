package com.ninabit.bono.modules.clientcampaign;

import com.ninabit.bono.modules.clientcampaign.dto.*;
import com.ninabit.bono.modules.user.Usuario;
import com.ninabit.bono.modules.user.UsuarioRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/bandeo")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Bandeo e Impulsadores - Ruleta")
@PreAuthorize("isAuthenticated()")
public class BandeoRuletaController {

    private final RegistroClienteService registroClienteService;
    private final RuletaService ruletaService;
    private final InventarioImpulsadorRepository inventarioImpulsadorRepository;
    private final CampanaClienteRepository campanaClienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final RegistroClienteRepository registroClienteRepository;
    private final EntregaRuletaRepository entregaRuletaRepository;

    private Usuario getUsuario(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        String email = auth.getName().trim();
        return usuarioRepository.findByEmail(email)
                .or(() -> usuarioRepository.findByEmail(email.toLowerCase()))
                .or(() -> usuarioRepository.findByEmailAndActivoTrue(email))
                .orElse(null);
    }

    private Long getUsuarioId(Authentication auth) {
        Usuario u = getUsuario(auth);
        return u != null ? u.getId() : null;
    }

    private Long getVendedorId(Authentication auth) {
        Usuario u = getUsuario(auth);
        if (u == null) return null;
        return u.getVendedorId() != null ? u.getVendedorId() : u.getId();
    }

    @GetMapping("/premios-disponibles")
    @Operation(summary = "Obtener premios con stock disponible para la ruleta del impulsador")
    public ResponseEntity<List<Map<String, Object>>> obtenerPremiosDisponibles(
            @RequestParam(required = false) Long campanaId,
            Authentication auth
    ) {
        Usuario usuario = getUsuario(auth);
        Long usuarioId = usuario != null ? usuario.getId() : null;
        Long vendedorId = usuario != null && usuario.getVendedorId() != null ? usuario.getVendedorId() : usuarioId;
        String nombreCompleto = usuario != null ? usuario.getNombreCompleto() : null;
        Long tiendaId = usuario != null ? usuario.getTiendaId() : null;

        if (campanaId == null) {
            campanaId = campanaClienteRepository.findCurrentActiveCampaign(LocalDate.now())
                    .or(() -> campanaClienteRepository.findByActivoTrue().stream().findFirst())
                    .map(CampanaCliente::getId)
                    .orElse(1L);
        }

        List<Map<String, Object>> premios = ruletaService.obtenerPremiosDisponiblesParaRuleta(
                campanaId,
                usuarioId,
                vendedorId,
                nombreCompleto,
                tiendaId
        );
        return ResponseEntity.ok(premios);
    }

    @PostMapping(value = "/registro-y-ruleta", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Registrar cliente por impulsador y girar la ruleta de premios")
    public ResponseEntity<Map<String, Object>> registrarYRuleta(
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
            @RequestPart(value = "fotoCiReverso", required = false) MultipartFile fotoCiReverso,
            Authentication auth
    ) {
        try {
            Usuario usuario = getUsuario(auth);
            Long usuarioId = usuario != null ? usuario.getId() : null;
            Long vendedorId = usuario != null && usuario.getVendedorId() != null ? usuario.getVendedorId() : usuarioId;
            String impulsadorNombre = usuario != null && usuario.getNombreCompleto() != null && !usuario.getNombreCompleto().isBlank()
                    ? usuario.getNombreCompleto()
                    : (auth != null ? auth.getName() : "IMPULSADOR");
            Long tiendaId = usuario != null ? usuario.getTiendaId() : null;

            // 1. Guardar registro de cliente
            RegistroCliente registro = registroClienteService.registrarCompraCliente(
                    campanaId,
                    RegistroCliente.CanalRegistro.BANDEO,
                    vendedorId,
                    impulsadorNombre,
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

            // 2. Ejecutar giro de ruleta y descontar stock automáticamente si corresponde premio físico
            EntregaRuleta entrega = ruletaService.girarRuletaYAsignarPremio(
                    campanaId,
                    registro.getId(),
                    usuarioId,
                    vendedorId,
                    impulsadorNombre,
                    tiendaId
            );

            if (entrega == null) {
                // Siga Participando: NO genera entrega física ni descuenta inventario
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("registroId", registro.getId());
                resp.put("entregaId", null);
                resp.put("resultado", "SIGA_PARTICIPANDO");
                Map<String, Object> premioSiga = new HashMap<>();
                premioSiga.put("id", -1L);
                premioSiga.put("nombre", "¡Sigue participando!");
                premioSiga.put("descripcion", "Gran Sorteo Nacional Skyworth Bs 100.000");
                premioSiga.put("colorHex", "#1e293b");
                premioSiga.put("esSinPremio", true);
                resp.put("premioGanado", premioSiga);
                return ResponseEntity.ok(resp);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "registroId", registro.getId(),
                    "entregaId", entrega.getId(),
                    "premioGanado", PremioRuletaDto.fromEntity(entrega.getPremioRuleta())
            ));
        } catch (Exception e) {
            log.error("Error en bandeo y ruleta", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping(value = "/confirmar-entrega", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Cargar foto de respaldo de entrega del premio de la ruleta")
    public ResponseEntity<Map<String, Object>> confirmarEntrega(
            @RequestParam Long entregaId,
            @RequestPart("fotoEntrega") MultipartFile fotoEntrega,
            @RequestParam(required = false) String observacion
    ) {
        try {
            EntregaRuleta entrega = ruletaService.confirmarEntregaConFoto(entregaId, fotoEntrega, observacion);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "mensaje", "Foto de entrega registrada exitosamente.",
                    "entregaId", entrega.getId()
            ));
        } catch (Exception e) {
            log.error("Error al confirmar entrega de ruleta", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/mi-inventario")
    @Operation(summary = "Consultar inventario de premios asignados, entregados y saldo")
    public ResponseEntity<List<InventarioImpulsadorDto>> miInventario(
            @RequestParam(required = false) Long campanaId,
            Authentication auth
    ) {
        Long vendedorId = getVendedorId(auth);
        if (vendedorId == null) {
            return ResponseEntity.badRequest().body(Collections.emptyList());
        }

        if (campanaId == null) {
            campanaId = campanaClienteRepository.findCurrentActiveCampaign(LocalDate.now())
                    .map(CampanaCliente::getId)
                    .orElse(null);
        }

        if (campanaId == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<InventarioImpulsadorDto> list = inventarioImpulsadorRepository
                .findByCampanaClienteIdAndVendedorId(campanaId, vendedorId).stream()
                .map(InventarioImpulsadorDto::fromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/mis-registros")
    @Operation(summary = "Obtener registros realizados por el impulsador con estadísticas de estados")
    public ResponseEntity<Map<String, Object>> misRegistros(
            @RequestParam(required = false) Long campanaId,
            Authentication auth
    ) {
        Long vendedorId = getVendedorId(auth);
        if (vendedorId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Usuario no identificado como vendedor/impulsador"));
        }

        if (campanaId == null) {
            campanaId = campanaClienteRepository.findCurrentActiveCampaign(LocalDate.now())
                    .map(CampanaCliente::getId)
                    .orElse(null);
        }

        List<RegistroCliente> registros = registroClienteRepository.findByImpulsadorIdAndCampana(vendedorId, campanaId);

        long total = registros.size();
        long pendientes = registros.stream().filter(r -> r.getEstado() == RegistroCliente.EstadoRegistro.PENDIENTE).count();
        long aprobados = registros.stream().filter(r -> r.getEstado() == RegistroCliente.EstadoRegistro.APROBADO).count();
        long observados = registros.stream().filter(r -> r.getEstado() == RegistroCliente.EstadoRegistro.OBSERVADO).count();
        long rechazados = registros.stream().filter(r -> r.getEstado() == RegistroCliente.EstadoRegistro.RECHAZADO).count();

        List<Map<String, Object>> items = registros.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("nombreCliente", r.getNombreCliente());
            map.put("apellidoCliente", r.getApellidoCliente());
            map.put("ci", r.getCi());
            map.put("celular", r.getCelular());
            map.put("email", r.getEmail());
            map.put("ciudad", r.getCiudad());
            map.put("puntoVenta", r.getPuntoVenta());
            map.put("productoModelo", r.getProductoModelo());
            map.put("productoPulgadas", r.getProductoPulgadas());
            map.put("serial", r.getSerial());
            map.put("fechaCompra", r.getFechaCompra());
            map.put("estado", r.getEstado() != null ? r.getEstado().name() : "PENDIENTE");
            map.put("motivoObservacion", r.getMotivoObservacion());
            map.put("motivoRechazo", r.getEstado() == RegistroCliente.EstadoRegistro.RECHAZADO ? r.getMotivoObservacion() : null);
            map.put("fotoTag", r.getFotoTag());
            map.put("fotoPoliza", r.getFotoPoliza());
            map.put("fotoNota", r.getFotoNota());
            map.put("fotoCiAnverso", r.getFotoCiAnverso());
            map.put("fotoCiReverso", r.getFotoCiReverso());
            map.put("createdAt", r.getCreatedAt());

            entregaRuletaRepository.findByRegistroClienteId(r.getId()).ifPresentOrElse(entrega -> {
                boolean esSinPremio = entrega.getPremioRuleta() != null && entrega.getPremioRuleta().isEsSinPremio();
                map.put("entregaId", entrega.getId());
                map.put("premioNombre", entrega.getPremioRuleta() != null ? entrega.getPremioRuleta().getNombre() : null);
                map.put("premioImagen", entrega.getPremioRuleta() != null ? entrega.getPremioRuleta().getImagenUrl() : null);
                map.put("premioColor", entrega.getPremioRuleta() != null ? entrega.getPremioRuleta().getColorHex() : null);
                map.put("esSinPremio", esSinPremio);
                map.put("premioEntregado", esSinPremio || (entrega.getFotoEntrega() != null && !entrega.getFotoEntrega().isBlank()));
                map.put("revertido", entrega.isRevertido());
                map.put("fotoEntrega", entrega.getFotoEntrega());
            }, () -> {
                if (r.getCanalRegistro() == RegistroCliente.CanalRegistro.BANDEO) {
                    map.put("entregaId", null);
                    map.put("premioNombre", "¡Sigue participando!");
                    map.put("premioImagen", null);
                    map.put("premioColor", "#334155");
                    map.put("esSinPremio", true);
                    map.put("premioEntregado", true);
                    map.put("revertido", false);
                    map.put("fotoEntrega", null);
                }
            });

            return map;
        }).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("total", total);
        response.put("pendientes", pendientes);
        response.put("aprobados", aprobados);
        response.put("observados", observados);
        response.put("rechazados", rechazados);
        response.put("registros", items);

        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/subsanar-observacion", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subsanar documentos de un registro observado desde el perfil de bandeo")
    public ResponseEntity<Map<String, Object>> subsanarObservacion(
            @RequestParam Long registroId,
            @RequestPart(value = "fotoTag", required = false) MultipartFile fotoTag,
            @RequestPart(value = "fotoPoliza", required = false) MultipartFile fotoPoliza,
            @RequestPart(value = "fotoNota", required = false) MultipartFile fotoNota,
            @RequestPart(value = "fotoCiAnverso", required = false) MultipartFile fotoCiAnverso,
            @RequestPart(value = "fotoCiReverso", required = false) MultipartFile fotoCiReverso,
            Authentication auth
    ) {
        try {
            Long vendedorId = getVendedorId(auth);
            RegistroCliente actualizado = registroClienteService.subsanarObservacionImpulsador(
                    registroId, vendedorId, fotoTag, fotoPoliza, fotoNota, fotoCiAnverso, fotoCiReverso
            );
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "mensaje", "Registro subsanado correctamente. Vuelve a revisión pendiente.",
                    "registro", RegistroClienteDto.fromEntity(actualizado)
            ));
        } catch (Exception e) {
            log.error("Error al subsanar observación desde bandeo", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
