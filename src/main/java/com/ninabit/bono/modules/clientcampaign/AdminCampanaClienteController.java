package com.ninabit.bono.modules.clientcampaign;

import com.ninabit.bono.modules.audit.AuditoriaService;
import com.ninabit.bono.modules.clientcampaign.dto.*;
import com.ninabit.bono.modules.product.Producto;
import com.ninabit.bono.modules.product.ProductoRepository;
import com.ninabit.bono.modules.vendor.Vendedor;
import com.ninabit.bono.modules.vendor.VendedorRepository;
import com.ninabit.bono.modules.user.Usuario;
import com.ninabit.bono.modules.user.UsuarioRepository;
import com.ninabit.bono.modules.tienda.Tienda;
import com.ninabit.bono.modules.tienda.TiendaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/campanas-cliente")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - Campañas Cliente Final")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCampanaClienteController {

    private final CampanaClienteRepository campanaClienteRepository;
    private final CampanaClienteProductoRepository campanaClienteProductoRepository;
    private final PremioRuletaRepository premioRuletaRepository;
    private final InventarioImpulsadorRepository inventarioImpulsadorRepository;
    private final ProductoRepository productoRepository;
    private final VendedorRepository vendedorRepository;
    private final UsuarioRepository usuarioRepository;
    private final TiendaRepository tiendaRepository;
    private final AuditoriaService auditoriaService;
    private final EntregaRuletaRepository entregaRuletaRepository;

    // --- CAMPANAS ---
    @GetMapping
    @Operation(summary = "Listar todas las campañas de cliente final")
    public List<CampanaClienteDto> listarCampanas() {
        return campanaClienteRepository.findAll().stream()
                .map(CampanaClienteDto::fromEntity)
                .toList();
    }

    @PostMapping
    @Operation(summary = "Crear o actualizar una campaña de cliente final")
    public ResponseEntity<CampanaClienteDto> guardarCampana(@RequestBody CampanaCliente campana, Authentication auth) {
        CampanaCliente saved = campanaClienteRepository.save(campana);
        auditoriaService.log(
                auth != null ? auth.getName() : "ADMIN",
                "GUARDAR_CAMPANA_CLIENTE",
                "Campaña: " + saved.getNombre() + " (ID: " + saved.getId() + ")",
                "CampanaCliente",
                saved.getId().toString()
        );
        return ResponseEntity.ok(CampanaClienteDto.fromEntity(saved));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener detalle completo de una campaña con sus productos y premios")
    public ResponseEntity<CampanaDetalleDto> obtenerDetalle(@PathVariable Long id) {
        CampanaCliente campana = campanaClienteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaña no encontrada: " + id));

        List<CampanaClienteProductoDto> productosAsoc = campanaClienteProductoRepository.findByCampanaClienteId(id).stream()
                .map(CampanaClienteProductoDto::fromEntity)
                .toList();
        List<PremioRuletaDto> premiosRuleta = premioRuletaRepository.findByCampanaClienteId(id).stream()
                .map(PremioRuletaDto::fromEntity)
                .toList();

        CampanaDetalleDto resp = CampanaDetalleDto.builder()
                .campana(CampanaClienteDto.fromEntity(campana))
                .productos(productosAsoc)
                .premiosRuleta(premiosRuleta)
                .build();

        return ResponseEntity.ok(resp);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar o desactivar campaña de cliente final")
    public ResponseEntity<Map<String, Boolean>> eliminarCampana(@PathVariable Long id, Authentication auth) {
        campanaClienteRepository.findById(id).ifPresent(c -> {
            c.setActivo(false);
            campanaClienteRepository.save(c);
            auditoriaService.log(
                    auth != null ? auth.getName() : "ADMIN",
                    "DESACTIVAR_CAMPANA_CLIENTE",
                    "Campaña desactivada: " + c.getNombre(),
                    "CampanaCliente",
                    id.toString()
            );
        });
        return ResponseEntity.ok(Map.of("success", true));
    }

    // --- PRODUCTOS PARTICIPANTES Y MULTIPLICADORES ---
    @PostMapping("/{campanaId}/productos")
    @Operation(summary = "Asociar producto con multiplicador de tickets a la campaña")
    public ResponseEntity<CampanaClienteProductoDto> asociarProducto(
            @PathVariable Long campanaId,
            @RequestParam Long productoId,
            @RequestParam(defaultValue = "1") Integer multiplicadorTickets
    ) {
        CampanaCliente campana = campanaClienteRepository.findById(campanaId)
                .orElseThrow(() -> new IllegalArgumentException("Campaña no encontrada"));
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        Optional<CampanaClienteProducto> existente = campanaClienteProductoRepository
                .findByCampanaClienteIdAndProductoId(campanaId, productoId);

        CampanaClienteProducto item = existente.orElseGet(() -> CampanaClienteProducto.builder()
                .campanaCliente(campana)
                .producto(producto)
                .build());

        item.setMultiplicadorTickets(multiplicadorTickets);
        item.setActivo(true);
        CampanaClienteProducto saved = campanaClienteProductoRepository.save(item);
        return ResponseEntity.ok(CampanaClienteProductoDto.fromEntity(saved));
    }

    @DeleteMapping("/productos/{asociacionId}")
    @Operation(summary = "Desvincular producto de la campaña")
    public ResponseEntity<Map<String, Boolean>> desvincularProducto(@PathVariable Long asociacionId) {
        campanaClienteProductoRepository.deleteById(asociacionId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // --- PREMIOS DE RULETA Y PROBABILIDADES ---
    @PostMapping("/{campanaId}/premios")
    @Operation(summary = "Crear o actualizar un premio de ruleta para la campaña")
    public ResponseEntity<PremioRuletaDto> guardarPremio(
            @PathVariable Long campanaId,
            @RequestBody PremioRuleta premio
    ) {
        CampanaCliente campana = campanaClienteRepository.findById(campanaId)
                .orElseThrow(() -> new IllegalArgumentException("Campaña no encontrada"));
        premio.setCampanaCliente(campana);
        PremioRuleta saved = premioRuletaRepository.save(premio);
        return ResponseEntity.ok(PremioRuletaDto.fromEntity(saved));
    }

    @DeleteMapping("/premios/{premioId}")
    @Operation(summary = "Eliminar o desactivar premio de ruleta")
    public ResponseEntity<Map<String, Boolean>> desactivarPremio(@PathVariable Long premioId) {
        premioRuletaRepository.findById(premioId).ifPresent(p -> {
            p.setActivo(false);
            premioRuletaRepository.save(p);
        });
        return ResponseEntity.ok(Map.of("success", true));
    }

    // --- DESTINATARIOS PARA BANDEO (Operadores, Trabajadores Skyworth, Vendedores, Tiendas) ---
    @GetMapping("/destinatarios-bandeo")
    @Operation(summary = "Listar todos los posibles destinatarios de stock para bandeo")
    public ResponseEntity<Map<String, Object>> listarDestinatariosBandeo() {
        // 1. Operadores de bandeo
        List<Map<String, Object>> operadores = usuarioRepository.findByRol(Usuario.Rol.OPERADOR_BANDEO).stream()
                .filter(Usuario::isActivo)
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "nombre", u.getNombreCompleto() != null ? u.getNombreCompleto() : u.getEmail(),
                        "email", u.getEmail(),
                        "ciudad", u.getCiudad() != null ? u.getCiudad() : "",
                        "tipo", "OPERADOR_BANDEO"
                )).collect(Collectors.toList());

        // 2. Trabajadores de Skyworth (Admin, Supervisor, etc.)
        List<Map<String, Object>> trabajadoresSkyworth = usuarioRepository.findAll().stream()
                .filter(u -> u.isActivo() && u.getRol() != Usuario.Rol.OPERADOR_BANDEO && u.getRol() != Usuario.Rol.VENDOR)
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "nombre", u.getNombreCompleto() != null ? u.getNombreCompleto() : u.getEmail(),
                        "email", u.getEmail(),
                        "rol", u.getRol().name(),
                        "ciudad", u.getCiudad() != null ? u.getCiudad() : "",
                        "tipo", "TRABAJADOR_SKYWORTH"
                )).collect(Collectors.toList());

        // 3. Vendedores
        List<Map<String, Object>> vendedores = vendedorRepository.findAll().stream()
                .filter(Vendedor::isActivo)
                .map(v -> Map.<String, Object>of(
                        "id", v.getId(),
                        "nombre", v.getNombreCompleto(),
                        "email", v.getEmail(),
                        "ciudad", v.getCiudad() != null ? v.getCiudad().getNombre() : "",
                        "tipo", "VENDEDOR"
                )).collect(Collectors.toList());

        // 4. Tiendas
        List<Map<String, Object>> tiendas = tiendaRepository.findByActivoTrue().stream()
                .map(t -> Map.<String, Object>of(
                        "id", t.getId(),
                        "nombre", t.getNombre(),
                        "ciudad", t.getCiudad() != null ? t.getCiudad().getNombre() : "",
                        "tipo", "TIENDA"
                )).collect(Collectors.toList());

        Map<String, Object> resp = new HashMap<>();
        resp.put("operadoresBandeo", operadores);
        resp.put("trabajadoresSkyworth", trabajadoresSkyworth);
        resp.put("vendedores", vendedores);
        resp.put("tiendas", tiendas);

        return ResponseEntity.ok(resp);
    }

    // --- INVENTARIO Y CONCILIACION DE BANDEO ---
    @PostMapping("/{campanaId}/inventario/asignar")
    @Operation(summary = "Asignar stock de premio a un operador de bandeo, trabajador skyworth, vendedor o tienda")
    public ResponseEntity<InventarioImpulsadorDto> asignarInventario(
            @PathVariable Long campanaId,
            @RequestParam Long premioRuletaId,
            @RequestParam Integer cantidadAsignada,
            @RequestParam(required = false, defaultValue = "OPERADOR_BANDEO") String tipoDestinatario,
            @RequestParam(required = false) Long destinatarioId,
            @RequestParam(required = false) String destinatarioNombre,
            @RequestParam(required = false) Long vendedorId, // compatibilidad anterior
            @RequestParam(required = false) Long tiendaId,
            @RequestParam(required = false) String tiendaNombre,
            @RequestParam(required = false) String observaciones
    ) {
        CampanaCliente campana = campanaClienteRepository.findById(campanaId)
                .orElseThrow(() -> new IllegalArgumentException("Campaña no encontrada"));
        PremioRuleta premio = premioRuletaRepository.findById(premioRuletaId)
                .orElseThrow(() -> new IllegalArgumentException("Premio no encontrado"));

        Long finalId = destinatarioId != null ? destinatarioId : vendedorId;
        String finalNombre = destinatarioNombre;
        Long finalUsuarioId = null;
        Long finalVendedorId = null;

        if ("VENDEDOR".equalsIgnoreCase(tipoDestinatario)) {
            finalVendedorId = finalId;
            if (finalNombre == null && finalVendedorId != null) {
                finalNombre = vendedorRepository.findById(finalVendedorId).map(Vendedor::getNombreCompleto).orElse("Vendedor");
            }
        } else if ("TIENDA".equalsIgnoreCase(tipoDestinatario)) {
            tiendaId = finalId;
            if (finalNombre == null && tiendaId != null) {
                finalNombre = tiendaRepository.findById(tiendaId).map(Tienda::getNombre).orElse("Tienda");
            }
            tiendaNombre = finalNombre;
        } else {
            // OPERADOR_BANDEO o TRABAJADOR_SKYWORTH
            finalUsuarioId = finalId;
            if (finalNombre == null && finalUsuarioId != null) {
                finalNombre = usuarioRepository.findById(finalUsuarioId).map(Usuario::getNombreCompleto).orElse("Operador");
            }
        }

        // REGLA FUNDAMENTAL DE NEGOCIO:
        // Cada asignación para un operador o tienda (por fin de semana, evento, turno o punto de venta)
        // se registra como una NUEVA ASIGNACIÓN independiente en la base de datos.
        // Las asignaciones pasadas que fueron CONCILIADAS se preservan cerradas de forma permanente
        // para auditoría, actas de entrega, reportes e informes históricos.
        InventarioImpulsador inv = InventarioImpulsador.builder()
                .campanaCliente(campana)
                .premioRuleta(premio)
                .usuarioId(finalUsuarioId)
                .vendedorId(finalVendedorId)
                .vendedorNombre(finalNombre)
                .nombreDestinatario(finalNombre)
                .tipoDestinatario(tipoDestinatario.toUpperCase())
                .tiendaId(tiendaId)
                .tiendaNombre(tiendaNombre != null && !tiendaNombre.isBlank() ? tiendaNombre.trim() : "Punto General")
                .cantidadAsignada(cantidadAsignada)
                .cantidadEntregada(0)
                .cantidadDevuelta(0)
                .saldo(cantidadAsignada)
                .estadoConciliacion("ABIERTO")
                .observaciones(observaciones)
                .build();

        inv.calculateSaldo();
        InventarioImpulsador saved = inventarioImpulsadorRepository.save(inv);
        return ResponseEntity.ok(InventarioImpulsadorDto.fromEntity(saved));
    }

    @PostMapping("/inventario/{inventarioId}/devolucion")
    @Operation(summary = "Registrar devolución física de productos/premios y conciliar stock")
    public ResponseEntity<InventarioImpulsadorDto> registrarDevolucion(
            @PathVariable Long inventarioId,
            @RequestParam Integer cantidadDevuelta,
            @RequestParam(required = false) String observaciones
    ) {
        InventarioImpulsador inv = inventarioImpulsadorRepository.findById(inventarioId)
                .orElseThrow(() -> new IllegalArgumentException("Inventario no encontrado: " + inventarioId));

        if (cantidadDevuelta == null || cantidadDevuelta < 0) {
            throw new IllegalArgumentException("La cantidad devuelta no puede ser negativa.");
        }

        int maxDevolucionPosible = inv.getCantidadAsignada() - inv.getCantidadEntregada();
        if (cantidadDevuelta > maxDevolucionPosible) {
            throw new IllegalArgumentException("No puede devolver más unidades (" + cantidadDevuelta + ") de las asignadas pendientes (" + maxDevolucionPosible + ").");
        }

        inv.setCantidadDevuelta(cantidadDevuelta);
        if (observaciones != null && !observaciones.isBlank()) {
            String prev = inv.getObservaciones() != null ? inv.getObservaciones() + " | " : "";
            inv.setObservaciones(prev + "Devolución registrada: " + cantidadDevuelta + " u. " + observaciones);
        }
        inv.setFechaConciliacion(LocalDateTime.now());
        inv.calculateSaldo();

        InventarioImpulsador saved = inventarioImpulsadorRepository.save(inv);
        return ResponseEntity.ok(InventarioImpulsadorDto.fromEntity(saved));
    }

    @PostMapping("/inventario/{inventarioId}/conciliar")
    @Operation(summary = "Cerrar conciliación manual con observaciones")
    public ResponseEntity<InventarioImpulsadorDto> cerrarConciliacion(
            @PathVariable Long inventarioId,
            @RequestParam(defaultValue = "CONCILIADO") String estado,
            @RequestParam(required = false) String observaciones
    ) {
        InventarioImpulsador inv = inventarioImpulsadorRepository.findById(inventarioId)
                .orElseThrow(() -> new IllegalArgumentException("Inventario no encontrado: " + inventarioId));

        inv.setEstadoConciliacion(estado.toUpperCase());
        if (observaciones != null && !observaciones.isBlank()) {
            String prev = inv.getObservaciones() != null ? inv.getObservaciones() + " | " : "";
            inv.setObservaciones(prev + "Cierre conciliación: " + observaciones);
        }
        inv.setFechaConciliacion(LocalDateTime.now());
        InventarioImpulsador saved = inventarioImpulsadorRepository.save(inv);
        return ResponseEntity.ok(InventarioImpulsadorDto.fromEntity(saved));
    }

    @PutMapping("/inventario/{inventarioId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Editar asignación de inventario (solo Administradores)")
    public ResponseEntity<InventarioImpulsadorDto> editarInventario(
            @PathVariable Long inventarioId,
            @RequestParam(required = false) Integer cantidadAsignada,
            @RequestParam(required = false) Long tiendaId,
            @RequestParam(required = false) String tiendaNombre,
            @RequestParam(required = false) String observaciones,
            @RequestParam(required = false) String estadoConciliacion,
            Authentication auth
    ) {
        InventarioImpulsador inv = inventarioImpulsadorRepository.findById(inventarioId)
                .orElseThrow(() -> new IllegalArgumentException("Asignación de inventario no encontrada: " + inventarioId));

        if (cantidadAsignada != null) {
            int entregadasYDevueltas = (inv.getCantidadEntregada() != null ? inv.getCantidadEntregada() : 0) +
                                       (inv.getCantidadDevuelta() != null ? inv.getCantidadDevuelta() : 0);
            if (cantidadAsignada < entregadasYDevueltas) {
                throw new IllegalArgumentException("La cantidad asignada (" + cantidadAsignada + ") no puede ser inferior a las unidades ya entregadas o devueltas (" + entregadasYDevueltas + ").");
            }
            inv.setCantidadAsignada(cantidadAsignada);
        }

        if (tiendaId != null) {
            inv.setTiendaId(tiendaId);
            tiendaRepository.findById(tiendaId).ifPresent(t -> inv.setTiendaNombre(t.getNombre()));
        } else if (tiendaNombre != null && !tiendaNombre.isBlank()) {
            inv.setTiendaNombre(tiendaNombre.trim());
        }

        if (observaciones != null) {
            inv.setObservaciones(observaciones.trim());
        }

        if (estadoConciliacion != null && !estadoConciliacion.isBlank()) {
            inv.setEstadoConciliacion(estadoConciliacion.trim().toUpperCase());
        }

        inv.calculateSaldo();
        InventarioImpulsador saved = inventarioImpulsadorRepository.save(inv);

        auditoriaService.log(
                auth != null ? auth.getName() : "ADMIN",
                "EDITAR_INVENTARIO_PREMIOS",
                "Inventario ID: " + saved.getId() + " editado para " + saved.getNombreDestinatario() + ". Asignadas: " + saved.getCantidadAsignada() + ", Saldo: " + saved.getSaldo(),
                "InventarioImpulsador",
                saved.getId().toString()
        );

        return ResponseEntity.ok(InventarioImpulsadorDto.fromEntity(saved));
    }

    @DeleteMapping("/inventario/{inventarioId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar asignación de inventario (solo Administradores)")
    public ResponseEntity<Map<String, Object>> eliminarInventario(
            @PathVariable Long inventarioId,
            Authentication auth
    ) {
        InventarioImpulsador inv = inventarioImpulsadorRepository.findById(inventarioId)
                .orElseThrow(() -> new IllegalArgumentException("Asignación de inventario no encontrada: " + inventarioId));

        if (inv.getCantidadEntregada() != null && inv.getCantidadEntregada() > 0) {
            throw new IllegalStateException("No se puede eliminar la asignación ID " + inventarioId + 
                    " porque ya cuenta con " + inv.getCantidadEntregada() + " entrega(s) física(s) registrada(s) a clientes.");
        }

        String detalle = "Asignación ID: " + inv.getId() + ", Destinatario: " + inv.getNombreDestinatario() + 
                ", Premio: " + (inv.getPremioRuleta() != null ? inv.getPremioRuleta().getNombre() : "N/A") + 
                ", Asignadas: " + inv.getCantidadAsignada();

        inventarioImpulsadorRepository.delete(inv);

        auditoriaService.log(
                auth != null ? auth.getName() : "ADMIN",
                "ELIMINAR_INVENTARIO_PREMIOS",
                detalle,
                "InventarioImpulsador",
                inventarioId.toString()
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "mensaje", "Asignación de inventario eliminada exitosamente."
        ));
    }

    @GetMapping("/{campanaId}/inventario")
    @Operation(summary = "Consultar consolidado de inventarios por campaña")
    public List<InventarioImpulsadorDto> consultarInventarios(@PathVariable Long campanaId) {
        return inventarioImpulsadorRepository.findByCampanaClienteIdOrderByIdDesc(campanaId).stream()
                .filter(i -> i.getPremioRuleta() == null || !i.getPremioRuleta().isEsSinPremio())
                .map(InventarioImpulsadorDto::fromEntity)
                .toList();
    }

    @GetMapping("/inventario/{inventarioId}/entregas")
    @Operation(summary = "Obtener todas las entregas físicas a clientes realizadas de una asignación de inventario")
    public ResponseEntity<List<Map<String, Object>>> obtenerEntregasDeInventario(@PathVariable Long inventarioId) {
        InventarioImpulsador inv = inventarioImpulsadorRepository.findById(inventarioId)
                .orElseThrow(() -> new IllegalArgumentException("Inventario no encontrado: " + inventarioId));

        Long campanaId = inv.getCampanaCliente().getId();
        Long premioId = inv.getPremioRuleta().getId();
        Long usuarioId = inv.getUsuarioId();
        Long vendedorId = inv.getVendedorId();
        String destinatario = inv.getNombreDestinatario();

        List<EntregaRuleta> entregas = entregaRuletaRepository.findByCampanaClienteIdAndPremioRuletaIdOrderByFechaGiroDesc(
                campanaId, premioId
        );

        if (usuarioId != null || vendedorId != null || (destinatario != null && !destinatario.isBlank())) {
            entregas = entregas.stream()
                    .filter(e -> (usuarioId != null && Objects.equals(e.getImpulsadorId(), usuarioId)) ||
                                 (vendedorId != null && Objects.equals(e.getImpulsadorId(), vendedorId)) ||
                                 (destinatario != null && !destinatario.isBlank() && destinatario.equalsIgnoreCase(e.getImpulsadorNombre())))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> result = entregas.stream()
                .map(this::mapEntregaRuletaDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{campanaId}/entregas-todas")
    @Operation(summary = "Obtener el listado general de todas las entregas de bandeo de una campaña")
    public ResponseEntity<List<Map<String, Object>>> obtenerTodasLasEntregasCampana(@PathVariable Long campanaId) {
        List<EntregaRuleta> entregas = entregaRuletaRepository.findByCampanaClienteIdOrderByFechaGiroDesc(campanaId);
        List<Map<String, Object>> result = entregas.stream()
                .map(this::mapEntregaRuletaDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> mapEntregaRuletaDTO(EntregaRuleta e) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", e.getId());
        dto.put("fechaGiro", e.getFechaGiro() != null ? e.getFechaGiro().toString() : null);
        dto.put("fotoEntrega", e.getFotoEntrega());
        dto.put("impulsadorId", e.getImpulsadorId());
        dto.put("impulsadorNombre", e.getImpulsadorNombre());
        dto.put("revertido", e.isRevertido());
        dto.put("motivoReversion", e.getMotivoReversion());

        if (e.getPremioRuleta() != null) {
            Map<String, Object> premio = new HashMap<>();
            premio.put("id", e.getPremioRuleta().getId());
            premio.put("nombre", e.getPremioRuleta().getNombre() != null ? e.getPremioRuleta().getNombre() : "");
            premio.put("descripcion", e.getPremioRuleta().getDescripcion() != null ? e.getPremioRuleta().getDescripcion() : "");
            premio.put("colorHex", e.getPremioRuleta().getColorHex() != null ? e.getPremioRuleta().getColorHex() : "#3b82f6");
            dto.put("premio", premio);
        }

        if (e.getRegistroCliente() != null) {
            RegistroCliente rc = e.getRegistroCliente();
            Map<String, Object> cli = new HashMap<>();
            cli.put("id", rc.getId());
            cli.put("nombreCliente", rc.getNombreCliente());
            cli.put("apellidoCliente", rc.getApellidoCliente());
            cli.put("nombreCompleto", ((rc.getNombreCliente() != null ? rc.getNombreCliente() : "") + " " + (rc.getApellidoCliente() != null ? rc.getApellidoCliente() : "")).trim());
            cli.put("ci", rc.getCi());
            cli.put("celular", rc.getCelular());
            cli.put("email", rc.getEmail());
            cli.put("ciudad", rc.getCiudad());
            cli.put("puntoVenta", rc.getPuntoVenta());
            cli.put("productoModelo", rc.getProductoModelo());
            cli.put("productoPulgadas", rc.getProductoPulgadas());
            cli.put("serial", rc.getSerial());
            cli.put("fechaCompra", rc.getFechaCompra() != null ? rc.getFechaCompra().toString() : null);
            cli.put("estado", rc.getEstado() != null ? rc.getEstado().name() : "");
            cli.put("createdAt", rc.getCreatedAt() != null ? rc.getCreatedAt().toString() : null);
            dto.put("cliente", cli);
        }

        return dto;
    }
}

