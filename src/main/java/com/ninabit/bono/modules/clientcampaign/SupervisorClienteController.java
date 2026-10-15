package com.ninabit.bono.modules.clientcampaign;

import com.ninabit.bono.modules.clientcampaign.dto.*;
import com.ninabit.bono.modules.user.Usuario;
import com.ninabit.bono.modules.user.UsuarioRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/supervisor/cliente")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Supervisor - Cliente Final")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'REVIEWER')")
public class SupervisorClienteController {

    private final SupervisorClienteService supervisorClienteService;
    private final UsuarioRepository usuarioRepository;

    private Long getUsuarioId(Authentication auth) {
        if (auth == null) return null;
        return usuarioRepository.findByEmail(auth.getName())
                .map(Usuario::getId)
                .orElse(null);
    }

    private String getUsuarioNombre(Authentication auth) {
        if (auth == null) return "SUPERVISOR";
        return usuarioRepository.findByEmail(auth.getName())
                .map(u -> u.getNombreCompleto() != null ? u.getNombreCompleto() : u.getEmail())
                .orElse(auth.getName());
    }

    @GetMapping("/registros")
    @Operation(summary = "Listar registros de clientes paginados y filtrados para supervisión")
    public Page<RegistroClienteDto> listarRegistros(
            @RequestParam(required = false) Long campanaId,
            @RequestParam(required = false) RegistroCliente.EstadoRegistro estado,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return supervisorClienteService.listarRegistros(
                campanaId, estado, ciudad, search, fechaInicio, fechaFin, page, size
        ).map(RegistroClienteDto::fromEntity);
    }

    @GetMapping("/kpis")
    @Operation(summary = "Obtener contadores de estado de registros para el panel")
    public Map<String, Long> obtenerKpis(@RequestParam(required = false) Long campanaId) {
        return supervisorClienteService.obtenerKpis(campanaId);
    }

    @PostMapping("/registros/{id}/aprobar")
    @Operation(summary = "Aprobar registro de cliente y habilitar sus tickets para sorteos")
    public ResponseEntity<Map<String, Object>> aprobarRegistro(@PathVariable Long id, Authentication auth) {
        try {
            Long userId = getUsuarioId(auth);
            String userName = getUsuarioNombre(auth);
            RegistroCliente aprobado = supervisorClienteService.aprobarRegistro(id, userId, userName);
            return ResponseEntity.ok(Map.of("success", true, "mensaje", "Registro aprobado exitosamente y tickets habilitados.", "registro", RegistroClienteDto.fromEntity(aprobado)));
        } catch (Exception e) {
            log.error("Error al aprobar registro", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/registros/{id}/observar")
    @Operation(summary = "Observar registro de cliente especificando motivo obligatorio")
    public ResponseEntity<Map<String, Object>> observarRegistro(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {
        try {
            Long userId = getUsuarioId(auth);
            String userName = getUsuarioNombre(auth);
            String motivo = body.get("motivo");
            RegistroCliente observado = supervisorClienteService.observarRegistro(id, userId, userName, motivo);
            return ResponseEntity.ok(Map.of("success", true, "mensaje", "Registro observado correctamente.", "registro", RegistroClienteDto.fromEntity(observado)));
        } catch (Exception e) {
            log.error("Error al observar registro", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/registros/{id}/rechazar")
    @Operation(summary = "Rechazar registro de cliente especificando motivo obligatorio")
    public ResponseEntity<Map<String, Object>> rechazarRegistro(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {
        try {
            Long userId = getUsuarioId(auth);
            String userName = getUsuarioNombre(auth);
            String motivo = body.get("motivo");
            RegistroCliente rechazado = supervisorClienteService.rechazarRegistro(id, userId, userName, motivo);
            return ResponseEntity.ok(Map.of("success", true, "mensaje", "Registro rechazado correctamente.", "registro", RegistroClienteDto.fromEntity(rechazado)));
        } catch (Exception e) {
            log.error("Error al rechazar registro", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/registros/{id}")
    @Operation(summary = "Eliminar o anular registro de cliente y liberar su serial")
    public ResponseEntity<Map<String, Object>> eliminarRegistro(
            @PathVariable Long id,
            @RequestParam(required = false) String motivo,
            Authentication auth
    ) {
        try {
            Long userId = getUsuarioId(auth);
            String userName = getUsuarioNombre(auth);
            supervisorClienteService.eliminarRegistro(id, userId, userName, motivo);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "mensaje", "Registro eliminado y anulado exitosamente. El serial ha sido liberado para nuevo registro."
            ));
        } catch (Exception e) {
            log.error("Error al eliminar registro", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/registros/{id}/eliminar")
    @Operation(summary = "Eliminar o anular registro de cliente (vía POST) y liberar su serial")
    public ResponseEntity<Map<String, Object>> anularYEliminarRegistro(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth
    ) {
        try {
            Long userId = getUsuarioId(auth);
            String userName = getUsuarioNombre(auth);
            String motivo = body != null ? body.get("motivo") : null;
            supervisorClienteService.eliminarRegistro(id, userId, userName, motivo);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "mensaje", "Registro eliminado y anulado exitosamente. El serial ha sido liberado para nuevo registro."
            ));
        } catch (Exception e) {
            log.error("Error al anular/eliminar registro", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping(value = "/registros/{id}/evidencia", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Reemplazar o subir una evidencia fotográfica por el supervisor/admin")
    public ResponseEntity<Map<String, Object>> actualizarEvidencia(
            @PathVariable Long id,
            @RequestParam("tipo") String tipo,
            @RequestPart("archivo") MultipartFile archivo,
            Authentication auth
    ) {
        try {
            Long userId = getUsuarioId(auth);
            String userName = getUsuarioNombre(auth);
            RegistroCliente actualizado = supervisorClienteService.actualizarEvidencia(id, tipo, archivo, userId, userName);
            return ResponseEntity.ok(Map.of("success", true, "mensaje", "Evidencia actualizada exitosamente.", "registro", RegistroClienteDto.fromEntity(actualizado)));
        } catch (Exception e) {
            log.error("Error al actualizar evidencia", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/registros/{id}/evidencia/{tipo}")
    @Operation(summary = "Quitar una evidencia fotográfica por el supervisor/admin")
    public ResponseEntity<Map<String, Object>> eliminarEvidencia(
            @PathVariable Long id,
            @PathVariable String tipo,
            Authentication auth
    ) {
        try {
            Long userId = getUsuarioId(auth);
            String userName = getUsuarioNombre(auth);
            RegistroCliente actualizado = supervisorClienteService.eliminarEvidencia(id, tipo, userId, userName);
            return ResponseEntity.ok(Map.of("success", true, "mensaje", "Evidencia eliminada exitosamente.", "registro", RegistroClienteDto.fromEntity(actualizado)));
        } catch (Exception e) {
            log.error("Error al eliminar evidencia", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/registros/{id}/entrega-ruleta")
    @Operation(summary = "Obtener entrega física de ruleta y foto de bandeo")
    public ResponseEntity<EntregaRuletaDto> obtenerEntregaRuleta(@PathVariable Long id) {
        return supervisorClienteService.obtenerEntregaRuleta(id)
                .map(EntregaRuletaDto::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
