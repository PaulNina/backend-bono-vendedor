package com.ninabit.bono.modules.liquidation;

import com.ninabit.bono.modules.audit.AuditoriaService;
import com.ninabit.bono.modules.campaign.Campana;
import com.ninabit.bono.modules.campaign.CampanaRepository;
import com.ninabit.bono.modules.liquidation.dto.*;
import com.ninabit.bono.modules.payment.Pago;
import com.ninabit.bono.modules.payment.PagoRepository;
import com.ninabit.bono.modules.sale.Venta;
import com.ninabit.bono.modules.sale.VentaCampana;
import com.ninabit.bono.modules.sale.VentaCampanaRepository;
import com.ninabit.bono.modules.sale.VentaRepository;
import com.ninabit.bono.modules.tienda.Tienda;
import com.ninabit.bono.modules.tienda.TiendaRepository;
import com.ninabit.bono.modules.vendor.Vendedor;
import com.ninabit.bono.modules.vendor.VendedorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoteLiquidacionService {

    private final LoteLiquidacionRepository loteRepository;
    private final LoteLiquidacionDetalleRepository detalleRepository;
    private final IncidenciaRegularizacionRepository incidenciaRepository;
    private final VentaCampanaRepository ventaCampanaRepository;
    private final VentaRepository ventaRepository;
    private final VendedorRepository vendedorRepository;
    private final TiendaRepository tiendaRepository;
    private final CampanaRepository campanaRepository;
    private final PagoRepository pagoRepository;
    private final AuditoriaService auditoriaService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Transactional(readOnly = true)
    public LotePreviewResponse previewLote(PreviewLoteRequest req) {
        List<VentaCampana> candidatas = getCandidateSales(req);

        Map<Long, List<VentaCampana>> porBeneficiario = new HashMap<>();
        boolean esPorTienda = "TIENDA".equalsIgnoreCase(req.getTipoBeneficiario());

        for (VentaCampana vc : candidatas) {
            Venta v = vc.getVenta();
            Long key = esPorTienda ? (v.getTiendaId() != null ? v.getTiendaId() : -1L) : v.getVendedorId();
            porBeneficiario.computeIfAbsent(key, k -> new ArrayList<>()).add(vc);
        }

        List<BeneficiarioResumenDTO> resumenes = new ArrayList<>();
        List<LoteLiquidacionDetalle> detalles = new ArrayList<>();

        BigDecimal globalBruto = BigDecimal.ZERO;
        BigDecimal globalDeducciones = BigDecimal.ZERO;
        BigDecimal globalNeto = BigDecimal.ZERO;
        int globalVentas = 0;

        for (Map.Entry<Long, List<VentaCampana>> entry : porBeneficiario.entrySet()) {
            Long benefId = entry.getKey();
            List<VentaCampana> vcs = entry.getValue();

            String benefNombre = "Desconocido";
            String ciudadBenef = "";
            String tiendaNombre = "";
            String fotoQr = null;
            String ciBenef = "";

            if (esPorTienda) {
                Tienda t = tiendaRepository.findById(benefId).orElse(null);
                if (t != null) {
                    benefNombre = t.getNombre();
                    ciudadBenef = t.getCiudad() != null ? t.getCiudad().getNombre() : "";
                    tiendaNombre = t.getNombre();
                }
            } else {
                Vendedor v = vendedorRepository.findById(benefId).orElse(null);
                if (v != null) {
                    benefNombre = v.getNombreCompleto();
                    ciudadBenef = v.getCiudad() != null ? v.getCiudad().getNombre() : "";
                    tiendaNombre = v.getTienda() != null ? v.getTienda() : "";
                    fotoQr = v.getFotoQr();
                    ciBenef = v.getCi();
                }
            }

            BigDecimal montoBrutoBenef = BigDecimal.ZERO;
            for (VentaCampana vc : vcs) {
                int bono = vc.getBonoGanado() != null ? vc.getBonoGanado() : 0;
                montoBrutoBenef = montoBrutoBenef.add(BigDecimal.valueOf(bono));
            }

            // Deducciones de incidencias pendientes
            BigDecimal saldoPendienteCompensar = BigDecimal.ZERO;
            List<IncidenciaRegularizacion> incidencias = esPorTienda
                    ? incidenciaRepository.findByTiendaIdAndEstado(benefId, IncidenciaRegularizacion.EstadoIncidencia.PENDIENTE)
                    : incidenciaRepository.findByVendedorIdAndEstado(benefId, IncidenciaRegularizacion.EstadoIncidencia.PENDIENTE);

            for (IncidenciaRegularizacion inc : incidencias) {
                if (inc.getSaldoPendiente() != null) {
                    saldoPendienteCompensar = saldoPendienteCompensar.add(inc.getSaldoPendiente());
                }
            }

            BigDecimal deduccionBenef = montoBrutoBenef.min(saldoPendienteCompensar);
            BigDecimal montoNetoBenef = montoBrutoBenef.subtract(deduccionBenef);

            resumenes.add(BeneficiarioResumenDTO.builder()
                    .beneficiarioId(benefId)
                    .beneficiarioNombre(benefNombre)
                    .tipoBeneficiario(esPorTienda ? "TIENDA" : "VENDEDOR")
                    .ciudad(ciudadBenef)
                    .tiendaNombre(tiendaNombre)
                    .cantidadVentas(vcs.size())
                    .montoBruto(montoBrutoBenef)
                    .deduccionIncidencias(deduccionBenef)
                    .montoNeto(montoNetoBenef)
                    .fotoQr(fotoQr)
                    .build());

            // Repartir detalles individuales
            BigDecimal deduccionRestantePorDistribuir = deduccionBenef;
            for (VentaCampana vc : vcs) {
                Venta v = vc.getVenta();
                BigDecimal bonoVenta = BigDecimal.valueOf(vc.getBonoGanado() != null ? vc.getBonoGanado() : 0);
                BigDecimal dedVenta = bonoVenta.min(deduccionRestantePorDistribuir);
                deduccionRestantePorDistribuir = deduccionRestantePorDistribuir.subtract(dedVenta);
                BigDecimal netoVenta = bonoVenta.subtract(dedVenta);

                detalles.add(LoteLiquidacionDetalle.builder()
                        .ventaCampanaId(vc.getId())
                        .ventaId(v.getId())
                        .serial(v.getSerial())
                        .productModel(v.getProductModel())
                        .vendedorId(v.getVendedorId())
                        .vendedorNombre(v.getVendorName())
                        .vendedorCi(ciBenef)
                        .tiendaId(v.getTiendaId())
                        .tiendaNombre(v.getStoreName() != null ? v.getStoreName() : tiendaNombre)
                        .ciudad(v.getCiudad() != null ? v.getCiudad() : ciudadBenef)
                        .tipoBeneficiario(esPorTienda ? "TIENDA" : "VENDEDOR")
                        .montoBruto(bonoVenta)
                        .deduccionIncidencias(dedVenta)
                        .montoNeto(netoVenta)
                        .estado(LoteLiquidacionDetalle.EstadoDetalle.PENDIENTE)
                        .build());
            }

            globalBruto = globalBruto.add(montoBrutoBenef);
            globalDeducciones = globalDeducciones.add(deduccionBenef);
            globalNeto = globalNeto.add(montoNetoBenef);
            globalVentas += vcs.size();
        }

        return LotePreviewResponse.builder()
                .totalVentas(globalVentas)
                .totalBeneficiarios(resumenes.size())
                .totalMontoBrutoBs(globalBruto)
                .totalDeduccionesBs(globalDeducciones)
                .totalMontoNetoBs(globalNeto)
                .beneficiarios(resumenes)
                .detalles(detalles)
                .build();
    }

    @Transactional
    public LoteLiquidacion crearLote(CrearLoteRequest req, String username) {
        PreviewLoteRequest previewReq = new PreviewLoteRequest();
        previewReq.setCampanaId(req.getCampanaId());
        previewReq.setCiudad(req.getCiudad());
        previewReq.setTipoBeneficiario(req.getTipoBeneficiario());
        previewReq.setPeriodoInicio(req.getPeriodoInicio());
        previewReq.setPeriodoFin(req.getPeriodoFin());

        LotePreviewResponse preview = previewLote(previewReq);
        if (preview.getDetalles().isEmpty()) {
            throw new IllegalArgumentException("No hay ventas aprobadas pendientes para liquidar con los filtros seleccionados.");
        }

        LocalDate today = LocalDate.now();
        long count = loteRepository.countByFechaCierreBetween(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth())) + 1;
        String codigo = String.format("LOTE-%d-%02d-%04d", today.getYear(), today.getMonthValue(), count);
        while (loteRepository.existsByCodigoLote(codigo)) {
            count++;
            codigo = String.format("LOTE-%d-%02d-%04d", today.getYear(), today.getMonthValue(), count);
        }

        String campanaNombre = "Todas las campañas";
        if (req.getCampanaId() != null) {
            campanaNombre = campanaRepository.findById(req.getCampanaId())
                    .map(Campana::getNombre)
                    .orElse("Campaña " + req.getCampanaId());
        }

        LoteLiquidacion lote = LoteLiquidacion.builder()
                .codigoLote(codigo)
                .fechaCierre(today)
                .estado(req.getEstado() != null ? req.getEstado() : LoteLiquidacion.EstadoLote.BORRADOR)
                .campanaId(req.getCampanaId())
                .campanaNombre(campanaNombre)
                .ciudad(req.getCiudad() != null && !req.getCiudad().equalsIgnoreCase("todas") ? req.getCiudad() : null)
                .tipoBeneficiario(req.getTipoBeneficiario() != null ? req.getTipoBeneficiario() : "VENDEDOR")
                .periodoInicio(req.getPeriodoInicio())
                .periodoFin(req.getPeriodoFin())
                .totalMontoBrutoBs(preview.getTotalMontoBrutoBs())
                .totalDeduccionesBs(preview.getTotalDeduccionesBs())
                .totalMontoNetoBs(preview.getTotalMontoNetoBs())
                .totalVentas(preview.getTotalVentas())
                .totalBeneficiarios(preview.getTotalBeneficiarios())
                .usuarioCreador(username != null ? username : "SISTEMA")
                .observaciones(req.getObservaciones())
                .build();

        LoteLiquidacion savedLote = loteRepository.save(lote);

        for (LoteLiquidacionDetalle d : preview.getDetalles()) {
            d.setLote(savedLote);
            detalleRepository.save(d);
        }

        auditoriaService.log(username, "LOTE_LIQUIDACION_CREADO",
                "Creado lote " + savedLote.getCodigoLote() + " con " + savedLote.getTotalVentas() + " ventas y neto Bs " + savedLote.getTotalMontoNetoBs(),
                "LoteLiquidacion", savedLote.getId().toString());

        return savedLote;
    }

    @Transactional
    public LoteLiquidacion autorizarLote(Long id, String username) {
        LoteLiquidacion lote = loteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado con ID: " + id));

        if (lote.getEstado() == LoteLiquidacion.EstadoLote.PAGADO || lote.getEstado() == LoteLiquidacion.EstadoLote.ANULADO) {
            throw new IllegalStateException("El lote no puede ser autorizado en estado " + lote.getEstado());
        }

        lote.setEstado(LoteLiquidacion.EstadoLote.AUTORIZADO);
        lote.setUsuarioAutorizador(username != null ? username : "SISTEMA");
        lote.setFechaAutorizacion(LocalDateTime.now());

        LoteLiquidacion updated = loteRepository.save(lote);

        auditoriaService.log(username, "LOTE_LIQUIDACION_AUTORIZADO",
                "Autorizado lote " + lote.getCodigoLote() + " por " + username,
                "LoteLiquidacion", lote.getId().toString());

        return updated;
    }

    @Transactional
    public LoteLiquidacion pagarLote(Long id, MultipartFile comprobante, String username) {
        LoteLiquidacion lote = loteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado con ID: " + id));

        if (lote.getEstado() == LoteLiquidacion.EstadoLote.PAGADO) {
            throw new IllegalStateException("El lote ya fue marcado como PAGADO.");
        }
        if (lote.getEstado() == LoteLiquidacion.EstadoLote.ANULADO) {
            throw new IllegalStateException("No se puede pagar un lote ANULADO.");
        }

        String comprobanteUrl = null;
        if (comprobante != null && !comprobante.isEmpty()) {
            try {
                comprobanteUrl = saveFile(comprobante);
                lote.setComprobantePagoGlobalUrl(comprobanteUrl);
            } catch (IOException e) {
                log.error("Error al guardar comprobante global para lote {}: {}", id, e.getMessage());
                throw new RuntimeException("Error al guardar comprobante de pago", e);
            }
        }

        List<LoteLiquidacionDetalle> detalles = detalleRepository.findByLoteId(id);

        // Agrupar detalles por vendedor para emitir / vincular registros Pago
        // (§5 Informe Complementario: solo pagar detalles en estado PENDIENTE, excluyendo OBSERVADOS y EXCLUIDOS)
        Map<Long, List<LoteLiquidacionDetalle>> porVendedor = detalles.stream()
                .filter(d -> d.getVendedorId() != null && d.getEstado() == LoteLiquidacionDetalle.EstadoDetalle.PENDIENTE)
                .collect(Collectors.groupingBy(LoteLiquidacionDetalle::getVendedorId));

        for (Map.Entry<Long, List<LoteLiquidacionDetalle>> entry : porVendedor.entrySet()) {
            Long vendedorId = entry.getKey();
            List<LoteLiquidacionDetalle> list = entry.getValue();

            BigDecimal montoNetoTotal = list.stream()
                    .map(LoteLiquidacionDetalle::getMontoNeto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Vendedor vendedor = vendedorRepository.findById(vendedorId).orElse(null);
            if (vendedor != null) {
                Pago pago = Pago.builder()
                        .vendedor(vendedor)
                        .montoTotal(montoNetoTotal.intValue())
                        .fecha(LocalDate.now())
                        .fotoComprobante(comprobanteUrl)
                        .estado(Pago.Estado.PAGADO)
                        .build();
                Pago savedPago = pagoRepository.save(pago);

                for (LoteLiquidacionDetalle det : list) {
                    det.setEstado(LoteLiquidacionDetalle.EstadoDetalle.PAGADO);
                    detalleRepository.save(det);

                    if (det.getVentaCampanaId() != null) {
                        ventaCampanaRepository.findById(det.getVentaCampanaId()).ifPresent(vc -> {
                            vc.setPago(savedPago);
                            ventaCampanaRepository.save(vc);
                        });
                    }
                }
            }
        }

        // Regularizar incidencias compensadas en este lote
        for (LoteLiquidacionDetalle det : detalles) {
            if (det.getEstado() == LoteLiquidacionDetalle.EstadoDetalle.PAGADO 
                    && det.getDeduccionIncidencias() != null 
                    && det.getDeduccionIncidencias().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dedRestante = det.getDeduccionIncidencias();
                List<IncidenciaRegularizacion> pending = det.getVendedorId() != null
                        ? incidenciaRepository.findByVendedorIdAndEstado(det.getVendedorId(), IncidenciaRegularizacion.EstadoIncidencia.PENDIENTE)
                        : (det.getTiendaId() != null
                            ? incidenciaRepository.findByTiendaIdAndEstado(det.getTiendaId(), IncidenciaRegularizacion.EstadoIncidencia.PENDIENTE)
                            : List.of());

                for (IncidenciaRegularizacion inc : pending) {
                    if (dedRestante.compareTo(BigDecimal.ZERO) <= 0) break;
                    BigDecimal saldo = inc.getSaldoPendiente();
                    if (saldo == null) saldo = inc.getMontoACompensar();

                    BigDecimal montoADescontar = saldo.min(dedRestante);
                    BigDecimal nuevoSaldo = saldo.subtract(montoADescontar);
                    dedRestante = dedRestante.subtract(montoADescontar);

                    inc.setSaldoPendiente(nuevoSaldo);
                    if (nuevoSaldo.compareTo(BigDecimal.ZERO) <= 0) {
                        inc.setEstado(IncidenciaRegularizacion.EstadoIncidencia.COMPENSADA);
                        inc.setFechaCompensacion(LocalDateTime.now());
                        inc.setLoteCompensadorId(lote.getId());
                    }
                    incidenciaRepository.save(inc);
                }
            }
        }

        // Si todos los detalles están pagados o excluidos, marcar lote como PAGADO
        boolean tienePendientes = detalles.stream().anyMatch(d -> d.getEstado() == LoteLiquidacionDetalle.EstadoDetalle.PENDIENTE);
        boolean tieneObservados = detalles.stream().anyMatch(d -> d.getEstado() == LoteLiquidacionDetalle.EstadoDetalle.OBSERVADO);

        if (!tienePendientes && !tieneObservados) {
            lote.setEstado(LoteLiquidacion.EstadoLote.PAGADO);
        } else if (tieneObservados) {
            lote.setEstado(LoteLiquidacion.EstadoLote.OBSERVADO);
        } else {
            lote.setEstado(LoteLiquidacion.EstadoLote.PAGADO);
        }
        lote.setUsuarioPagador(username != null ? username : "SISTEMA");
        lote.setFechaPago(LocalDateTime.now());

        LoteLiquidacion saved = loteRepository.save(lote);

        auditoriaService.log(username, "LOTE_LIQUIDACION_PAGADO",
                "Lote " + lote.getCodigoLote() + " procesado. Monto total neto pagado en esta corrida: Bs " + lote.getTotalMontoNetoBs() +
                (tieneObservados ? " | Quedan detalles OBSERVADOS retenidos para revisión." : ""),
                "LoteLiquidacion", lote.getId().toString());

        return saved;
    }

    @Transactional
    public LoteLiquidacion observarLote(Long id, String motivo, String username) {
        LoteLiquidacion lote = loteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado con ID: " + id));

        if (lote.getEstado() == LoteLiquidacion.EstadoLote.PAGADO) {
            throw new IllegalStateException("No se puede observar un lote en estado PAGADO.");
        }
        if (lote.getEstado() == LoteLiquidacion.EstadoLote.ANULADO) {
            throw new IllegalStateException("No se puede observar un lote ANULADO.");
        }

        lote.setEstado(LoteLiquidacion.EstadoLote.OBSERVADO);
        lote.setMotivoObservacion(motivo);
        lote.setUsuarioObservador(username != null ? username : "SISTEMA");
        lote.setFechaObservacion(LocalDateTime.now());
        String obsActual = lote.getObservaciones() != null ? lote.getObservaciones() + " | " : "";
        lote.setObservaciones(obsActual + "OBSERVADO por " + username + ": " + motivo);

        LoteLiquidacion saved = loteRepository.save(lote);
        auditoriaService.log(username, "LOTE_LIQUIDACION_OBSERVADO",
                "Lote " + lote.getCodigoLote() + " marcado como OBSERVADO. Motivo: " + motivo,
                "LoteLiquidacion", lote.getId().toString());

        return saved;
    }

    @Transactional
    public LoteLiquidacionDetalle observarDetalle(Long loteId, Long detalleId, String motivo, String username) {
        LoteLiquidacionDetalle det = detalleRepository.findById(detalleId)
                .orElseThrow(() -> new IllegalArgumentException("Detalle no encontrado con ID: " + detalleId));

        if (!det.getLote().getId().equals(loteId)) {
            throw new IllegalArgumentException("El detalle no pertenece al lote indicado.");
        }
        if (det.getEstado() == LoteLiquidacionDetalle.EstadoDetalle.PAGADO) {
            throw new IllegalStateException("No se puede observar un detalle ya PAGADO.");
        }

        det.setEstado(LoteLiquidacionDetalle.EstadoDetalle.OBSERVADO);
        det.setMotivoObservacion(motivo);
        LoteLiquidacionDetalle saved = detalleRepository.save(det);

        auditoriaService.log(username, "DETALLE_LOTE_OBSERVADO",
                "Detalle ID " + detalleId + " (Venta #" + det.getVentaId() + ", Vendedor: " + det.getVendedorNombre() + ") OBSERVADO. Motivo: " + motivo,
                "LoteLiquidacionDetalle", detalleId.toString());

        return saved;
    }

    @Transactional
    public LoteLiquidacionDetalle reactivarDetalle(Long loteId, Long detalleId, String username) {
        LoteLiquidacionDetalle det = detalleRepository.findById(detalleId)
                .orElseThrow(() -> new IllegalArgumentException("Detalle no encontrado con ID: " + detalleId));

        if (!det.getLote().getId().equals(loteId)) {
            throw new IllegalArgumentException("El detalle no pertenece al lote indicado.");
        }
        if (det.getEstado() == LoteLiquidacionDetalle.EstadoDetalle.PAGADO) {
            throw new IllegalStateException("No se puede reactivar un detalle ya PAGADO.");
        }

        det.setEstado(LoteLiquidacionDetalle.EstadoDetalle.PENDIENTE);
        det.setMotivoObservacion(null);
        LoteLiquidacionDetalle saved = detalleRepository.save(det);

        auditoriaService.log(username, "DETALLE_LOTE_REACTIVADO",
                "Detalle ID " + detalleId + " reactivado a PENDIENTE de pago",
                "LoteLiquidacionDetalle", detalleId.toString());

        return saved;
    }

    @Transactional
    public LoteLiquidacionDetalle excluirDetalle(Long loteId, Long detalleId, String motivo, String username) {
        LoteLiquidacionDetalle det = detalleRepository.findById(detalleId)
                .orElseThrow(() -> new IllegalArgumentException("Detalle no encontrado con ID: " + detalleId));

        if (!det.getLote().getId().equals(loteId)) {
            throw new IllegalArgumentException("El detalle no pertenece al lote indicado.");
        }
        if (det.getEstado() == LoteLiquidacionDetalle.EstadoDetalle.PAGADO) {
            throw new IllegalStateException("No se puede excluir un detalle ya PAGADO.");
        }

        det.setEstado(LoteLiquidacionDetalle.EstadoDetalle.EXCLUIDO);
        det.setMotivoObservacion(motivo);
        LoteLiquidacionDetalle saved = detalleRepository.save(det);

        auditoriaService.log(username, "DETALLE_LOTE_EXCLUIDO",
                "Detalle ID " + detalleId + " excluido del lote. Motivo: " + motivo,
                "LoteLiquidacionDetalle", detalleId.toString());

        return saved;
    }

    @Transactional
    public LoteLiquidacion anularLote(Long id, String motivo, String username) {
        LoteLiquidacion lote = loteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado con ID: " + id));

        if (lote.getEstado() == LoteLiquidacion.EstadoLote.PAGADO) {
            throw new IllegalStateException("No se puede anular un lote en estado PAGADO. Los fondos ya fueron desembolsados.");
        }

        lote.setEstado(LoteLiquidacion.EstadoLote.ANULADO);
        String obsActual = lote.getObservaciones() != null ? lote.getObservaciones() + " | " : "";
        lote.setObservaciones(obsActual + "ANULADO por " + username + ". Motivo: " + motivo);

        List<LoteLiquidacionDetalle> detalles = detalleRepository.findByLoteId(id);
        for (LoteLiquidacionDetalle d : detalles) {
            d.setEstado(LoteLiquidacionDetalle.EstadoDetalle.EXCLUIDO);
            detalleRepository.save(d);
        }

        LoteLiquidacion updated = loteRepository.save(lote);

        auditoriaService.log(username, "LOTE_LIQUIDACION_ANULADO",
                "Lote " + lote.getCodigoLote() + " anulado. Motivo: " + motivo,
                "LoteLiquidacion", lote.getId().toString());

        return updated;
    }

    @Transactional(readOnly = true)
    public List<LoteLiquidacion> listarLotes(Long campanaId, String ciudad, LoteLiquidacion.EstadoLote estado, String tipoBeneficiario) {
        if ("todas".equalsIgnoreCase(ciudad) || "todaslasciudades".equalsIgnoreCase(ciudad)) {
            ciudad = null;
        }
        return loteRepository.findWithFilters(campanaId, ciudad, estado, tipoBeneficiario);
    }

    @Transactional(readOnly = true)
    public LoteLiquidacion obtenerPorId(Long id) {
        LoteLiquidacion lote = loteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado con ID: " + id));
        // Force initialization of items
        lote.getDetalles().size();
        return lote;
    }

    @Transactional
    public IncidenciaRegularizacion registrarIncidencia(IncidenciaRegularizacion inc, String username) {
        inc.setFechaRegistro(LocalDateTime.now());
        if (inc.getSaldoPendiente() == null) {
            inc.setSaldoPendiente(inc.getMontoACompensar());
        }
        if (inc.getEstado() == null) {
            inc.setEstado(IncidenciaRegularizacion.EstadoIncidencia.PENDIENTE);
        }
        IncidenciaRegularizacion saved = incidenciaRepository.save(inc);

        auditoriaService.log(username, "INCIDENCIA_REGULARIZACION_REGISTRADA",
                "Registrada incidencia " + saved.getTipo() + " por Bs " + saved.getMontoACompensar() + " para vendedor/tienda",
                "IncidenciaRegularizacion", saved.getId().toString());

        return saved;
    }

    @Transactional(readOnly = true)
    public List<IncidenciaRegularizacion> listarIncidencias(IncidenciaRegularizacion.EstadoIncidencia estado, Long vendedorId, Long tiendaId) {
        return incidenciaRepository.findWithFilters(estado, vendedorId, tiendaId);
    }

    private List<VentaCampana> getCandidateSales(PreviewLoteRequest req) {
        List<VentaCampana> todasAprobadasSinPago = ventaCampanaRepository
                .findByPagoIsNullAndVenta_Estado(Venta.Estado.APROBADA);

        List<Long> activeVentaCampanaIds = detalleRepository.findActiveVentaCampanaIds();
        Set<Long> setActivos = new HashSet<>(activeVentaCampanaIds);

        return todasAprobadasSinPago.stream()
                .filter(vc -> !setActivos.contains(vc.getId()))
                .filter(vc -> {
                    if (req.getCampanaId() != null && !req.getCampanaId().equals(vc.getCampana().getId())) {
                        return false;
                    }
                    Venta v = vc.getVenta();
                    if (req.getCiudad() != null && !req.getCiudad().isBlank() && !"todas".equalsIgnoreCase(req.getCiudad())) {
                        String vCiudad = v.getCiudad();
                        if (vCiudad == null && v.getVendedor() != null && v.getVendedor().getCiudad() != null) {
                            vCiudad = v.getVendedor().getCiudad().getNombre();
                        }
                        if (vCiudad == null || !vCiudad.equalsIgnoreCase(req.getCiudad())) {
                            return false;
                        }
                    }
                    if (req.getPeriodoInicio() != null && v.getSaleDate().isBefore(req.getPeriodoInicio())) {
                        return false;
                    }
                    if (req.getPeriodoFin() != null && v.getSaleDate().isAfter(req.getPeriodoFin())) {
                        return false;
                    }
                    if ("TIENDA".equalsIgnoreCase(req.getTipoBeneficiario()) && v.getTiendaId() == null) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    private String saveFile(MultipartFile file) throws IOException {
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path path = Paths.get(uploadDir, filename);
        Files.write(path, file.getBytes());
        return "uploads/" + filename;
    }
}
