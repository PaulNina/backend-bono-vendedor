package com.ninabit.bono.modules.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Dashboard endpoint rewritten to use SQL aggregates (GROUP BY / SUM / COUNT)
 * instead of loading all Venta entities into the JVM heap.
 *
 * PostgreSQL fix: NEVER pass null to JPQL numeric/date params — use sentinels:
 *   campanaId = -1L  → all campaigns
 *   ciudad    = ""   → all cities
 *   startDate / endDate → always real LocalDateTime values
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_TOTAL', 'SUPERVISOR')")
@Tag(name = "Admin Dashboard")
public class AdminDashboardController {

    private final com.ninabit.bono.modules.sale.VentaRepository ventaRepository;
    private final com.ninabit.bono.modules.vendor.VendedorRepository vendedorRepository;
    private final com.ninabit.bono.modules.serial.SerialRepository serialRepository;
    private final com.ninabit.bono.modules.sale.VentaCampanaRepository ventaCampanaRepository;
    private final com.ninabit.bono.modules.tienda.TiendaRepository tiendaRepository;
    private final com.ninabit.bono.modules.liquidation.LoteLiquidacionRepository loteLiquidacionRepository;
    private final com.ninabit.bono.modules.clientcampaign.RegistroClienteRepository registroClienteRepository;
    private final com.ninabit.bono.modules.clientcampaign.TicketClienteRepository ticketClienteRepository;

    // Epoch start — used as "no lower bound" sentinel for date range
    private static final LocalDateTime EPOCH_START = LocalDateTime.of(2000, 1, 1, 0, 0);
    // Far-future date — used as "no upper bound" sentinel
    private static final LocalDateTime FAR_FUTURE  = LocalDateTime.of(2099, 12, 31, 23, 59, 59);

    @GetMapping("/dashboard")
    @Operation(summary = "Estadísticas generales del dashboard")
    @Transactional(readOnly = true)
    public Map<String, Object> getDashboard(
            @RequestParam(required = false) Long campanaId,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        // ── Sentinel values (never null to avoid PostgreSQL type inference error) ──
        Long campanaParam = (campanaId != null) ? campanaId : -1L;
        String ciudadParam = (ciudad == null || ciudad.isBlank() || ciudad.equalsIgnoreCase("all"))
                ? "" : ciudad.trim();

        LocalDateTime startDt = (startDate != null && !startDate.isBlank())
                ? LocalDate.parse(startDate).atStartOfDay() : EPOCH_START;
        LocalDateTime endDt = (endDate != null && !endDate.isBlank())
                ? LocalDate.parse(endDate).atTime(LocalTime.MAX) : FAR_FUTURE;

        // ── 1. COUNT by estado ─────────────────────────────────────────────────
        long total = 0, aprobadas = 0, pendientes = 0, observadas = 0, rechazadas = 0, anuladas = 0;

        List<Object[]> countRows = (campanaParam == -1)
            ? ventaRepository.countByEstadoAll(ciudadParam, startDt, endDt)
            : ventaRepository.countByEstadoByCampana(campanaParam, ciudadParam, startDt, endDt);

        for (Object[] row : countRows) {
            String estado = row[0].toString();
            long   count  = ((Number) row[1]).longValue();
            total += count;
            switch (estado) {
                case "APROBADA"  -> aprobadas  = count;
                case "PENDIENTE" -> pendientes = count;
                case "OBSERVADA" -> observadas = count;
                case "RECHAZADA" -> rechazadas = count;
                case "ANULADA"   -> anuladas   = count;
            }
        }

        // ── 2. SUM bonus & puntos ──────────────────────────────────────────────
        int totalBs = 0, totalPuntos = 0;
        List<Object[]> bonusList = (campanaParam == -1)
            ? ventaRepository.sumBonusAll(ciudadParam, startDt, endDt)
            : ventaRepository.sumBonusByCampana(campanaParam, ciudadParam, startDt, endDt);

        if (bonusList != null && !bonusList.isEmpty() && bonusList.get(0) != null) {
            Object[] row = bonusList.get(0);
            Object val0 = row[0] instanceof Object[] ? ((Object[]) row[0])[0] : row[0];
            Object val1 = row[1] instanceof Object[] ? ((Object[]) row[1])[0] : row[1];
            totalBs     = val0 != null ? ((Number) val0).intValue() : 0;
            totalPuntos = val1 != null ? ((Number) val1).intValue() : 0;
        }

        int totalBonusPendienteBs = ventaCampanaRepository.sumPendingBonus(campanaParam);
        int totalBonusPagadoBs = ventaCampanaRepository.sumPaidBonus(campanaParam);

        // ── 3. Entity counts ───────────────────────────────────────────────────
        long totalVendedores   = vendedorRepository.count();
        long totalTiendas      = tiendaRepository.count();
        long serialTotal       = serialRepository.count();
        long serialDisponibles = serialRepository.countDisponibles();
        long serialUsados      = serialRepository.countUsados();
        long lotesTotal        = loteLiquidacionRepository.count();
        long lotesPagados      = loteLiquidacionRepository.findWithFilters(null, null, com.ninabit.bono.modules.liquidation.LoteLiquidacion.EstadoLote.PAGADO, null).size();
        long lotesPendientes   = loteLiquidacionRepository.findWithFilters(null, null, com.ninabit.bono.modules.liquidation.LoteLiquidacion.EstadoLote.AUTORIZADO, null).size()
                + loteLiquidacionRepository.findWithFilters(null, null, com.ninabit.bono.modules.liquidation.LoteLiquidacion.EstadoLote.EN_REVISION, null).size()
                + loteLiquidacionRepository.findWithFilters(null, null, com.ninabit.bono.modules.liquidation.LoteLiquidacion.EstadoLote.BORRADOR, null).size();

        long totalRegistrosCliente = registroClienteRepository.count();
        long totalTicketsCliente   = ticketClienteRepository.count();

        // ── 4. City breakdown ─────────────────────────────────────────────────
        Map<String, Map<String, Object>> cityMap = new LinkedHashMap<>();
        List<Object[]> cityRows = (campanaParam == -1)
            ? ventaRepository.groupByCiudadEstadoAll(ciudadParam, startDt, endDt)
            : ventaRepository.groupByCiudadEstadoByCampana(campanaParam, ciudadParam, startDt, endDt);

        for (Object[] row : cityRows) {
            String c      = row[0] != null && !row[0].toString().isBlank() ? row[0].toString() : "Otra";
            String estado = row[1].toString();
            Object val2 = row[2] instanceof Object[] ? ((Object[]) row[2])[0] : row[2];
            Object val3 = row[3] instanceof Object[] ? ((Object[]) row[3])[0] : row[3];
            long   count  = val2 != null ? ((Number) val2).longValue() : 0;
            int    bonus  = val3 != null ? ((Number) val3).intValue() : 0;

            cityMap.computeIfAbsent(c, k -> new HashMap<>(Map.of(
                    "ciudad", k, "total", 0L, "aprobadas", 0L,
                    "pendientes", 0L, "observadas", 0L, "rechazadas", 0L, "bonusBs", 0)));
            Map<String, Object> cs = cityMap.get(c);
            cs.put("total", (long) cs.get("total") + count);
            switch (estado) {
                case "APROBADA"  -> { cs.put("aprobadas",  (long) cs.get("aprobadas")  + count);
                                      cs.put("bonusBs",    (int)  cs.get("bonusBs")    + bonus); }
                case "PENDIENTE" -> cs.put("pendientes", (long) cs.get("pendientes") + count);
                case "OBSERVADA" -> cs.put("observadas", (long) cs.get("observadas") + count);
                case "RECHAZADA" -> cs.put("rechazadas", (long) cs.get("rechazadas") + count);
            }
        }
        List<Map<String, Object>> ventasPorCiudad = new ArrayList<>(cityMap.values());

        // ── 5. Product-type breakdown ──────────────────────────────────────────
        Map<String, Map<String, Object>> typeMap = new LinkedHashMap<>();
        List<Object[]> typeRows = (campanaParam == -1)
            ? ventaRepository.groupByTipoEstadoAll(ciudadParam, startDt, endDt)
            : ventaRepository.groupByTipoEstadoByCampana(campanaParam, ciudadParam, startDt, endDt);

        for (Object[] row : typeRows) {
            String t      = row[0] != null && !row[0].toString().isBlank() ? row[0].toString() : "Otros";
            String estado = row[1].toString();
            Object val2   = row[2] instanceof Object[] ? ((Object[]) row[2])[0] : row[2];
            Object val3   = row[3] instanceof Object[] ? ((Object[]) row[3])[0] : row[3];
            long   count  = val2 != null ? ((Number) val2).longValue() : 0;
            int    bonus  = val3 != null ? ((Number) val3).intValue() : 0;

            typeMap.computeIfAbsent(t, k -> new HashMap<>(Map.of("tipo", k, "total", 0L, "bonusBs", 0)));
            Map<String, Object> ts = typeMap.get(t);
            ts.put("total", (long) ts.get("total") + count);
            if ("APROBADA".equals(estado)) ts.put("bonusBs", (int) ts.get("bonusBs") + bonus);
        }
        List<Map<String, Object>> ventasPorTipo = new ArrayList<>(typeMap.values());

        // ── 6. Top 5 products ─────────────────────────────────────────────────
        List<Map<String, Object>> topList = new ArrayList<>();
        List<Object[]> topRows = (campanaParam == -1)
            ? ventaRepository.topProductosAll(ciudadParam, startDt, endDt)
            : ventaRepository.topProductosByCampana(campanaParam, ciudadParam, startDt, endDt);

        for (int i = 0; i < Math.min(5, topRows.size()); i++) {
            Object[] row = topRows.get(i);
            topList.add(Map.of("model", row[0].toString(), "count", ((Number) row[1]).longValue()));
        }

        // ── 7. Daily breakdown ─────────────────────────────────────────────────
        Map<String, Map<String, Object>> dayMap = new TreeMap<>();
        
        // Use provided dates, else default to last 7 days for the chart ONLY
        LocalDateTime dayChartStart = (startDate != null && !startDate.isBlank())
                ? LocalDate.parse(startDate).atStartOfDay() : LocalDate.now().minusDays(6).atStartOfDay();
        LocalDateTime dayChartEnd = (endDate != null && !endDate.isBlank())
                ? LocalDate.parse(endDate).atTime(LocalTime.MAX) : LocalDate.now().atTime(LocalTime.MAX);
        
        List<Object[]> dayRows = (campanaParam == -1)
            ? ventaRepository.groupByDayEstadoAll(ciudadParam, dayChartStart, dayChartEnd)
            : ventaRepository.groupByDayEstadoByCampana(campanaParam, ciudadParam, dayChartStart, dayChartEnd);

        for (Object[] row : dayRows) {
            String dateStr = row[0].toString();
            String key = dateStr.length() > 10 ? dateStr.substring(0, 10) : dateStr;
            String estado  = row[1].toString();
            long   count   = ((Number) row[2]).longValue();
            
            dayMap.computeIfAbsent(key, k -> new HashMap<>(Map.of(
                    "fecha", k, "total", 0L,
                    "aprobadas", 0L, "pendientes", 0L, "observadas", 0L, "rechazadas", 0L)));
            
            Map<String, Object> ds = dayMap.get(key);
            ds.put("total", (long) ds.get("total") + count);
            switch (estado) {
                case "APROBADA"  -> ds.put("aprobadas",  (long) ds.get("aprobadas")  + count);
                case "PENDIENTE" -> ds.put("pendientes", (long) ds.get("pendientes") + count);
                case "OBSERVADA" -> ds.put("observadas", (long) ds.get("observadas") + count);
                case "RECHAZADA" -> ds.put("rechazadas", (long) ds.get("rechazadas") + count);
            }
        }

        LocalDate minD = dayChartStart.toLocalDate();
        LocalDate maxD = dayChartEnd.toLocalDate();
        
        for (LocalDate d = minD; !d.isAfter(maxD); d = d.plusDays(1)) {
            dayMap.putIfAbsent(d.toString(), new HashMap<>(Map.of(
                    "fecha", d.toString(), "total", 0L,
                    "aprobadas", 0L, "pendientes", 0L, "observadas", 0L, "rechazadas", 0L)));
        }
        
        List<Map<String, Object>> ventasPorDia = new ArrayList<>(dayMap.values());

        // ── 8. Assemble response ───────────────────────────────────────────────
        Map<String, Object> response = new HashMap<>();
        response.put("totalVentas",              total);
        response.put("ventasAprobadas",          aprobadas);
        response.put("ventasPendientes",         pendientes);
        response.put("ventasObservadas",         observadas);
        response.put("ventasRechazadas",         rechazadas);
        response.put("ventasAnuladas",           anuladas);
        response.put("totalBonusBs",             totalBs);
        response.put("totalBonusPendienteBs",    totalBonusPendienteBs);
        response.put("totalBonusPagadoBs",       totalBonusPagadoBs);
        response.put("totalPuntos",              totalPuntos);
        response.put("totalVendedores",          totalVendedores);
        response.put("totalTiendas",             totalTiendas);
        response.put("lotesLiquidacion",         Map.of(
                "total", lotesTotal,
                "pendientes", lotesPendientes,
                "pagados", lotesPagados));
        response.put("clienteFinal",             Map.of(
                "totalRegistros", totalRegistrosCliente,
                "totalTickets", totalTicketsCliente));
        response.put("ventasPorCiudad",          ventasPorCiudad);
        response.put("ventasPorTipo",            ventasPorTipo);
        response.put("ventasPorDia",             ventasPorDia);
        response.put("seriales",                 Map.of(
                "total",     serialTotal,
                "available", serialDisponibles,
                "used",      serialUsados));
        response.put("topProductos",             topList);

        return response;
    }
}
