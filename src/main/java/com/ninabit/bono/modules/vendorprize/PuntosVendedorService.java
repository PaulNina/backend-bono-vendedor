package com.ninabit.bono.modules.vendorprize;

import com.ninabit.bono.modules.audit.AuditoriaService;
import com.ninabit.bono.modules.campaign.Campana;
import com.ninabit.bono.modules.campaign.CampanaProducto;
import com.ninabit.bono.modules.campaign.CampanaProductoRepository;
import com.ninabit.bono.modules.campaign.CampanaRepository;
import com.ninabit.bono.modules.sale.Venta;
import com.ninabit.bono.modules.sale.VentaCampana;
import com.ninabit.bono.modules.sale.VentaCampanaRepository;
import com.ninabit.bono.modules.sale.VentaRepository;
import com.ninabit.bono.modules.vendor.Vendedor;
import com.ninabit.bono.modules.vendor.VendedorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PuntosVendedorService {

    private final VendedorRepository vendedorRepository;
    private final VentaRepository ventaRepository;
    private final VentaCampanaRepository ventaCampanaRepository;
    private final CampanaRepository campanaRepository;
    private final CampanaProductoRepository campanaProductoRepository;
    private final PremioVendedorRepository premioVendedorRepository;
    private final CanjePuntosRepository canjePuntosRepository;
    private final MovimientoPuntosRepository movimientoPuntosRepository;
    private final TicketVendedorRepository ticketVendedorRepository;
    private final AuditoriaService auditoriaService;

    // ────────────────────────────────────────────────────────────────────────────
    // Cálculo de puntos globales y por campaña (§6 Informe Complementario)
    // ────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public int calcularPuntosGanados(Long vendedorId) {
        List<Venta> ventas = ventaRepository.findByVendedorId(vendedorId);
        return ventas.stream()
                .filter(v -> v.getEstado() == Venta.Estado.APROBADA)
                .mapToInt(v -> v.getPuntos() != null ? v.getPuntos() : 0)
                .sum();
    }

    @Transactional(readOnly = true)
    public int calcularPuntosGanadosPorCampana(Long vendedorId, Long campanaId) {
        if (campanaId == null || campanaId <= 0) return calcularPuntosGanados(vendedorId);
        List<VentaCampana> vcs = ventaCampanaRepository.findByVendedorIdAndCampanaId(vendedorId, campanaId);
        return vcs.stream()
                .filter(vc -> vc.getVenta() != null && vc.getVenta().getEstado() == Venta.Estado.APROBADA)
                .mapToInt(vc -> vc.getPuntosGanados() != null ? vc.getPuntosGanados() : 0)
                .sum();
    }

    @Transactional(readOnly = true)
    public int calcularPuntosConsumidos(Long vendedorId) {
        return canjePuntosRepository.sumPuntosConsumidosByVendedorIdAndEstados(
                vendedorId,
                List.of(CanjePuntos.EstadoCanje.PENDIENTE, CanjePuntos.EstadoCanje.APROBADO, CanjePuntos.EstadoCanje.ENTREGADO)
        );
    }

    @Transactional(readOnly = true)
    public int calcularPuntosConsumidosPorCampana(Long vendedorId, Long campanaId) {
        if (campanaId == null || campanaId <= 0) return calcularPuntosConsumidos(vendedorId);
        return canjePuntosRepository.sumPuntosConsumidosByVendedorIdAndCampanaIdAndEstados(
                vendedorId,
                campanaId,
                List.of(CanjePuntos.EstadoCanje.PENDIENTE, CanjePuntos.EstadoCanje.APROBADO, CanjePuntos.EstadoCanje.ENTREGADO)
        );
    }

    @Transactional(readOnly = true)
    public int getSaldoPuntos(Long vendedorId) {
        int ganados = calcularPuntosGanados(vendedorId);
        int consumidos = calcularPuntosConsumidos(vendedorId);
        return Math.max(0, ganados - consumidos);
    }

    @Transactional(readOnly = true)
    public int getSaldoPuntos(Long vendedorId, Long campanaId) {
        if (campanaId == null || campanaId <= 0) return getSaldoPuntos(vendedorId);
        int ganados = calcularPuntosGanadosPorCampana(vendedorId, campanaId);
        int consumidos = calcularPuntosConsumidosPorCampana(vendedorId, campanaId);
        return Math.max(0, ganados - consumidos);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getResumenPuntos(Long vendedorId) {
        return getResumenPuntos(vendedorId, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getResumenPuntos(Long vendedorId, Long campanaId) {
        int ganados = (campanaId != null && campanaId > 0)
                ? calcularPuntosGanadosPorCampana(vendedorId, campanaId)
                : calcularPuntosGanados(vendedorId);
        int consumidos = (campanaId != null && campanaId > 0)
                ? calcularPuntosConsumidosPorCampana(vendedorId, campanaId)
                : calcularPuntosConsumidos(vendedorId);
        int saldo = Math.max(0, ganados - consumidos);

        List<MovimientoPuntos> movimientos = (campanaId != null && campanaId > 0)
                ? movimientoPuntosRepository.findByVendedorIdAndCampanaIdOrderByFechaDesc(vendedorId, campanaId)
                : movimientoPuntosRepository.findByVendedorIdOrderByFechaDesc(vendedorId);

        List<CanjePuntos> canjes = (campanaId != null && campanaId > 0)
                ? canjePuntosRepository.findByVendedorIdAndPremio_Campana_IdOrderByFechaSolicitudDesc(vendedorId, campanaId)
                : canjePuntosRepository.findByVendedorIdOrderByFechaSolicitudDesc(vendedorId);

        Map<String, Object> res = new HashMap<>();
        res.put("campanaId", campanaId);
        res.put("puntosGanados", ganados);
        res.put("puntosConsumidos", consumidos);
        res.put("saldoDisponible", saldo);
        res.put("movimientos", movimientos);
        res.put("canjes", canjes);
        return res;
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Canje atómico de puntos con soporte de política de canje (§6.1)
    // ────────────────────────────────────────────────────────────────────────────

    @Transactional
    public CanjePuntos solicitarCanje(Long vendedorId, Long premioId, String observaciones, String usuario) {
        Vendedor vendedor = vendedorRepository.findById(vendedorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendedor no encontrado con ID: " + vendedorId));

        PremioVendedor premio = premioVendedorRepository.findById(premioId)
                .orElseThrow(() -> new IllegalArgumentException("Premio no encontrado con ID: " + premioId));

        if (!premio.isActivo()) {
            throw new IllegalStateException("El premio seleccionado no se encuentra activo.");
        }

        if (premio.getStock() <= 0) {
            throw new IllegalStateException("No hay stock disponible para el premio: " + premio.getNombre());
        }

        Long campanaId = premio.getCampana() != null ? premio.getCampana().getId() : null;
        String campanaNombre = premio.getCampana() != null ? premio.getCampana().getNombre() : null;

        // Saldo según ámbito de campaña (§6: puntos de campañas distintas no se mezclarán)
        int saldoActual = (campanaId != null) ? getSaldoPuntos(vendedorId, campanaId) : getSaldoPuntos(vendedorId);
        if (saldoActual < premio.getPuntosRequeridos()) {
            throw new IllegalStateException("Puntos insuficientes. Saldo actual: " + saldoActual + 
                    " pts, requerido: " + premio.getPuntosRequeridos() + " pts.");
        }

        // Reserva atómica de stock
        premio.setStock(premio.getStock() - 1);
        premioVendedorRepository.save(premio);

        // Crear registro de canje
        CanjePuntos canje = CanjePuntos.builder()
                .vendedor(vendedor)
                .premio(premio)
                .puntosConsumidos(premio.getPuntosRequeridos())
                .fechaSolicitud(LocalDateTime.now())
                .estado(CanjePuntos.EstadoCanje.PENDIENTE)
                .observaciones(observaciones)
                .build();
        canje = canjePuntosRepository.save(canje);

        int nuevoSaldo = saldoActual - premio.getPuntosRequeridos();

        // Registrar movimiento en el libro de puntos
        MovimientoPuntos mov = MovimientoPuntos.builder()
                .vendedor(vendedor)
                .tipo(MovimientoPuntos.TipoMovimiento.RESERVA_CANJE)
                .puntos(-premio.getPuntosRequeridos())
                .saldoResultante(premio.getPoliticaCanje() == PremioVendedor.PoliticaCanje.REINICIO_SALDO ? 0 : nuevoSaldo)
                .referenciaId(canje.getId())
                .campanaId(campanaId)
                .campanaNombre(campanaNombre)
                .descripcion("Reserva por solicitud de canje: " + premio.getNombre())
                .fecha(LocalDateTime.now())
                .usuario(usuario != null ? usuario : vendedor.getEmail())
                .build();
        movimientoPuntosRepository.save(mov);

        // Tratamiento de sobrantes si la política es REINICIO_SALDO (§6.1)
        if (premio.getPoliticaCanje() == PremioVendedor.PoliticaCanje.REINICIO_SALDO && nuevoSaldo > 0) {
            MovimientoPuntos reinicioMov = MovimientoPuntos.builder()
                    .vendedor(vendedor)
                    .tipo(MovimientoPuntos.TipoMovimiento.AJUSTE_MANUAL)
                    .puntos(-nuevoSaldo)
                    .saldoResultante(0)
                    .referenciaId(canje.getId())
                    .campanaId(campanaId)
                    .campanaNombre(campanaNombre)
                    .descripcion("Reinicio de saldo sobrante tras canje (política REINICIO_SALDO §6.1)")
                    .fecha(LocalDateTime.now())
                    .usuario(usuario != null ? usuario : vendedor.getEmail())
                    .build();
            movimientoPuntosRepository.save(reinicioMov);
        }

        auditoriaService.log(
                usuario != null ? usuario : vendedor.getEmail(),
                "CANJE_SOLICITADO",
                "Canje solicitado ID " + canje.getId() + ": " + premio.getNombre() + " (" + premio.getPuntosRequeridos() + " pts, política: " + premio.getPoliticaCanje() + ") por " + vendedor.getNombreCompleto(),
                "CanjePuntos",
                canje.getId().toString()
        );

        return canje;
    }

    /**
     * Resolución administrativa de canjes (APROBAR, RECHAZAR, ENTREGAR)
     */
    @Transactional
    public CanjePuntos cambiarEstadoCanje(
            Long canjeId,
            CanjePuntos.EstadoCanje nuevoEstado,
            String motivoRechazo,
            String fotoComprobante,
            String usuario
    ) {
        CanjePuntos canje = canjePuntosRepository.findById(canjeId)
                .orElseThrow(() -> new IllegalArgumentException("Canje no encontrado con ID: " + canjeId));

        CanjePuntos.EstadoCanje estadoAnterior = canje.getEstado();
        if (estadoAnterior == nuevoEstado) {
            return canje;
        }

        PremioVendedor premio = canje.getPremio();
        Vendedor vendedor = canje.getVendedor();
        Long campanaId = premio != null && premio.getCampana() != null ? premio.getCampana().getId() : null;
        String campanaNombre = premio != null && premio.getCampana() != null ? premio.getCampana().getNombre() : null;

        if (nuevoEstado == CanjePuntos.EstadoCanje.RECHAZADO) {
            // Reversión: reponer stock
            if (premio != null) {
                premio.setStock(premio.getStock() + 1);
                premioVendedorRepository.save(premio);
            }

            int saldoActual = getSaldoPuntos(vendedor.getId(), campanaId);
            int saldoRestaurado = saldoActual + canje.getPuntosConsumidos();

            // Libro mayor de puntos: reversión con campaña asociada (§6)
            MovimientoPuntos mov = MovimientoPuntos.builder()
                    .vendedor(vendedor)
                    .tipo(MovimientoPuntos.TipoMovimiento.REVERSION_CANJE)
                    .puntos(canje.getPuntosConsumidos())
                    .saldoResultante(saldoRestaurado)
                    .referenciaId(canje.getId())
                    .campanaId(campanaId)
                    .campanaNombre(campanaNombre)
                    .descripcion("Reversión por rechazo de canje: " + (motivoRechazo != null ? motivoRechazo : "Sin motivo"))
                    .fecha(LocalDateTime.now())
                    .usuario(usuario)
                    .build();
            movimientoPuntosRepository.save(mov);

            canje.setMotivoRechazo(motivoRechazo);
        } else if (nuevoEstado == CanjePuntos.EstadoCanje.ENTREGADO) {
            if (fotoComprobante != null && !fotoComprobante.isBlank()) {
                canje.setFotoComprobante(fotoComprobante);
            }
        }

        canje.setEstado(nuevoEstado);
        canje.setFechaResolucion(LocalDateTime.now());
        canje.setUsuarioResolucion(usuario);
        canje = canjePuntosRepository.save(canje);

        auditoriaService.log(
                usuario,
                "CANJE_ESTADO_ACTUALIZADO",
                "Canje ID " + canjeId + " cambió de " + estadoAnterior + " a " + nuevoEstado + " para " + vendedor.getNombreCompleto(),
                "CanjePuntos",
                canjeId.toString()
        );

        return canje;
    }

    /**
     * Emisión de ticket digital de vendedor cuando una venta es aprobada.
     * Solo emite si la campaña tiene tickets activados y en la cantidad
     * definida por modelo (§4.1 y §7 Informe Complementario).
     */
    @Transactional
    public Optional<TicketVendedor> emitirTicketVendedorSiAplica(Venta venta) {
        List<TicketVendedor> tickets = emitirTicketsVendedorSiAplica(venta);
        return tickets.isEmpty() ? Optional.empty() : Optional.of(tickets.get(0));
    }

    @Transactional
    public List<TicketVendedor> emitirTicketsVendedorSiAplica(Venta venta) {
        if (venta == null || venta.getId() == null) return List.of();

        if (ticketVendedorRepository.existsByVentaId(venta.getId())) {
            return ticketVendedorRepository.findByVentaId(venta.getId())
                    .map(List::of)
                    .orElse(List.of());
        }

        Vendedor vendedor = vendedorRepository.findById(venta.getVendedorId()).orElse(null);
        if (vendedor == null) return List.of();

        // Determinar campaña asociada
        List<VentaCampana> vcs = ventaCampanaRepository.findByVentaId(venta.getId());
        Campana campana = null;
        if (!vcs.isEmpty() && vcs.get(0).getCampana() != null) {
            campana = vcs.get(0).getCampana();
        } else {
            campana = campanaRepository.findFirstByActivoTrue().orElse(null);
        }

        if (campana == null) return List.of();

        // Verificar cantidad de tickets configurada para este modelo en la campaña (§4.1 y §7)
        int cantidadTickets = 0;
        int cantidadTicketsTienda = 0;
        if (venta.getProductoId() != null) {
            Optional<CampanaProducto> cpOpt = campanaProductoRepository
                    .findByCampanaIdAndProductoId(campana.getId(), venta.getProductoId());
            if (cpOpt.isPresent()) {
                CampanaProducto cp = cpOpt.get();
                cantidadTickets = cp.getTicketsDigitales() != null ? cp.getTicketsDigitales() : 0;
                cantidadTicketsTienda = cp.getTicketsDigitalesTienda() != null ? cp.getTicketsDigitalesTienda() : 0;
            }
        }

        // Si la campaña o el modelo no tienen tickets activos ni de vendedor ni de tienda, omite
        if (cantidadTickets <= 0 && cantidadTicketsTienda <= 0) {
            log.info("Campaña {} / Producto {} no tiene tickets digitales configurados; omite emisión.",
                    campana.getId(), venta.getProductoId());
            return List.of();
        }

        List<TicketVendedor> generados = new ArrayList<>();
        // Tickets de Vendedor
        for (int i = 1; i <= cantidadTickets; i++) {
            String suffix = cantidadTickets > 1 ? ("-" + i) : "";
            String codigo = "VND-" + String.format("%04d", campana.getId()) + "-" + String.format("%06d", venta.getId()) + suffix;

            TicketVendedor ticket = TicketVendedor.builder()
                    .campana(campana)
                    .venta(venta)
                    .vendedor(vendedor)
                    .numeroTicket(codigo)
                    .estado(TicketVendedor.EstadoTicket.HABILITADO)
                    .build();

            TicketVendedor saved = ticketVendedorRepository.save(ticket);
            generados.add(saved);
            log.info("Ticket digital de vendedor emitido: {} para venta {}", codigo, venta.getId());
        }

        // Tickets de Tienda
        for (int i = 1; i <= cantidadTicketsTienda; i++) {
            String suffix = cantidadTicketsTienda > 1 ? ("-" + i) : "";
            String codigo = "TND-" + String.format("%04d", campana.getId()) + "-" + String.format("%06d", venta.getId()) + suffix;

            TicketVendedor ticket = TicketVendedor.builder()
                    .campana(campana)
                    .venta(venta)
                    .vendedor(vendedor)
                    .numeroTicket(codigo)
                    .estado(TicketVendedor.EstadoTicket.HABILITADO)
                    .build();

            TicketVendedor saved = ticketVendedorRepository.save(ticket);
            generados.add(saved);
            log.info("Ticket digital de TIENDA emitido: {} para venta {}", codigo, venta.getId());
        }

        return generados;
    }
}
