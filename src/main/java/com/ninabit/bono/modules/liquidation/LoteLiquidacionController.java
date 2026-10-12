package com.ninabit.bono.modules.liquidation;

import com.ninabit.bono.modules.liquidation.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/liquidaciones")
@RequiredArgsConstructor
@Tag(name = "Liquidaciones por Lotes e Incidencias")
public class LoteLiquidacionController {

    private final LoteLiquidacionService loteService;

    @PostMapping("/lotes/previsualizar")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL', 'SUPERVISOR')")
    @Operation(summary = "Previsualizar cálculo de lote de liquidación")
    public ResponseEntity<LotePreviewResponse> previewLote(@RequestBody PreviewLoteRequest req) {
        return ResponseEntity.ok(loteService.previewLote(req));
    }

    @PostMapping("/lotes")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL', 'SUPERVISOR')")
    @Operation(summary = "Crear nuevo lote de liquidación")
    public ResponseEntity<LoteLiquidacion> crearLote(@RequestBody CrearLoteRequest req, Authentication auth) {
        return ResponseEntity.ok(loteService.crearLote(req, auth.getName()));
    }

    @GetMapping("/lotes")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL', 'SUPERVISOR')")
    @Operation(summary = "Listar lotes de liquidación con filtros")
    public ResponseEntity<List<LoteLiquidacion>> listarLotes(
            @RequestParam(required = false) Long campanaId,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) LoteLiquidacion.EstadoLote estado,
            @RequestParam(required = false) String tipoBeneficiario) {
        return ResponseEntity.ok(loteService.listarLotes(campanaId, ciudad, estado, tipoBeneficiario));
    }

    @GetMapping("/lotes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL', 'SUPERVISOR')")
    @Operation(summary = "Obtener detalle completo de un lote")
    public ResponseEntity<LoteLiquidacion> obtenerLote(@PathVariable Long id) {
        return ResponseEntity.ok(loteService.obtenerPorId(id));
    }

    @PutMapping("/lotes/{id}/autorizar")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL')")
    @Operation(summary = "Autorizar lote de liquidación")
    public ResponseEntity<LoteLiquidacion> autorizarLote(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(loteService.autorizarLote(id, auth.getName()));
    }

    @PutMapping(value = "/lotes/{id}/pagar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL')")
    @Operation(summary = "Marcar lote como pagado y subir comprobante bancario global")
    public ResponseEntity<LoteLiquidacion> pagarLote(
            @PathVariable Long id,
            @RequestParam(value = "comprobante", required = false) MultipartFile comprobante,
            Authentication auth) {
        return ResponseEntity.ok(loteService.pagarLote(id, comprobante, auth.getName()));
    }

    @PutMapping("/lotes/{id}/observar")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL', 'SUPERVISOR')")
    @Operation(summary = "Observar lote de liquidación (§5 Informe Complementario)")
    public ResponseEntity<LoteLiquidacion> observarLote(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        String motivo = body != null ? body.getOrDefault("motivo", "Lote en observación") : "Lote en observación";
        return ResponseEntity.ok(loteService.observarLote(id, motivo, auth.getName()));
    }

    @PutMapping("/lotes/{id}/detalles/{detalleId}/observar")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL', 'SUPERVISOR')")
    @Operation(summary = "Observar un beneficiario / detalle individual sin congelar todo el lote (§5)")
    public ResponseEntity<LoteLiquidacionDetalle> observarDetalle(
            @PathVariable Long id,
            @PathVariable Long detalleId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        String motivo = body != null ? body.getOrDefault("motivo", "Beneficiario en observación") : "Beneficiario en observación";
        return ResponseEntity.ok(loteService.observarDetalle(id, detalleId, motivo, auth.getName()));
    }

    @PutMapping("/lotes/{id}/detalles/{detalleId}/reactivar")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL', 'SUPERVISOR')")
    @Operation(summary = "Reactivar detalle observado a estado PENDIENTE para habilitar su pago")
    public ResponseEntity<LoteLiquidacionDetalle> reactivarDetalle(
            @PathVariable Long id,
            @PathVariable Long detalleId,
            Authentication auth) {
        return ResponseEntity.ok(loteService.reactivarDetalle(id, detalleId, auth.getName()));
    }

    @PutMapping("/lotes/{id}/detalles/{detalleId}/excluir")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL', 'SUPERVISOR')")
    @Operation(summary = "Excluir detalle individual del lote de liquidación")
    public ResponseEntity<LoteLiquidacionDetalle> excluirDetalle(
            @PathVariable Long id,
            @PathVariable Long detalleId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        String motivo = body != null ? body.getOrDefault("motivo", "Excluido administrativamente") : "Excluido administrativamente";
        return ResponseEntity.ok(loteService.excluirDetalle(id, detalleId, motivo, auth.getName()));
    }

    @PutMapping("/lotes/{id}/anular")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL')")
    @Operation(summary = "Anular lote de liquidación y liberar ventas")
    public ResponseEntity<LoteLiquidacion> anularLote(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        String motivo = body != null ? body.getOrDefault("motivo", "Anulación administrativa") : "Anulación administrativa";
        return ResponseEntity.ok(loteService.anularLote(id, motivo, auth.getName()));
    }

    @GetMapping("/incidencias")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL', 'SUPERVISOR')")
    @Operation(summary = "Listar incidencias de regularización")
    public ResponseEntity<List<IncidenciaRegularizacion>> listarIncidencias(
            @RequestParam(required = false) IncidenciaRegularizacion.EstadoIncidencia estado,
            @RequestParam(required = false) Long vendedorId,
            @RequestParam(required = false) Long tiendaId) {
        return ResponseEntity.ok(loteService.listarIncidencias(estado, vendedorId, tiendaId));
    }

    @PostMapping("/incidencias")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL')")
    @Operation(summary = "Registrar manualmente una incidencia de regularización o compensación")
    public ResponseEntity<IncidenciaRegularizacion> registrarIncidencia(
            @RequestBody IncidenciaRegularizacion inc,
            Authentication auth) {
        return ResponseEntity.ok(loteService.registrarIncidencia(inc, auth.getName()));
    }
}
