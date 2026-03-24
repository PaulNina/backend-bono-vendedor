package com.ninabit.bono.modules.admin;

import com.ninabit.bono.modules.sale.Venta;
import com.ninabit.bono.modules.sale.VentaRepository;
import com.ninabit.bono.modules.serial.SerialRepository;
import com.ninabit.bono.modules.vendor.VendedorRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Dashboard")
public class AdminDashboardController {

        private final VentaRepository ventaRepository;
        private final VendedorRepository vendedorRepository;
        private final SerialRepository serialRepository;

        @GetMapping("/dashboard")
        @Operation(summary = "Estadísticas generales del dashboard")
        @org.springframework.transaction.annotation.Transactional(readOnly = true)
        public Map<String, Object> getDashboard(
                        @org.springframework.web.bind.annotation.RequestParam(required = false) Long campanaId,
                        @org.springframework.web.bind.annotation.RequestParam(required = false) String ciudad,
                        @org.springframework.web.bind.annotation.RequestParam(required = false) String startDate,
                        @org.springframework.web.bind.annotation.RequestParam(required = false) String endDate) {

                List<Venta> all = ventaRepository.findAll();

                if (campanaId != null) {
                        all = all.stream().filter(v -> v.getDetallesCampanas() != null &&
                                        v.getDetallesCampanas().stream()
                                                        .anyMatch(dc -> campanaId.equals(dc.getCampana().getId())))
                                        .collect(Collectors.toList());
                }

                if (ciudad != null && !ciudad.isBlank() && !ciudad.equalsIgnoreCase("all")) {
                        all = all.stream()
                                        .filter(v -> v.getCiudad() != null
                                                        && v.getCiudad().trim().equalsIgnoreCase(ciudad.trim()))
                                        .collect(Collectors.toList());
                }

                if (startDate != null && !startDate.isBlank()) {
                        java.time.LocalDate start = java.time.LocalDate.parse(startDate);
                        all = all.stream()
                                        .filter(v -> v.getCreatedAt() != null
                                                        && !v.getCreatedAt().toLocalDate().isBefore(start))
                                        .collect(Collectors.toList());
                }

                if (endDate != null && !endDate.isBlank()) {
                        java.time.LocalDate end = java.time.LocalDate.parse(endDate);
                        all = all.stream().filter(
                                        v -> v.getCreatedAt() != null && !v.getCreatedAt().toLocalDate().isAfter(end))
                                        .collect(Collectors.toList());
                }

                long total = all.size();
                long aprobadas = all.stream().filter(v -> v.getEstado() == Venta.Estado.APROBADA).count();
                long pendientes = all.stream().filter(v -> v.getEstado() == Venta.Estado.PENDIENTE).count();
                long rechazadas = all.stream().filter(v -> v.getEstado() == Venta.Estado.RECHAZADA).count();

                int totalBs = 0;
                int totalPuntos = 0;

                for (Venta v : all) {
                        if (v.getEstado() == Venta.Estado.APROBADA) {
                                if (campanaId != null && v.getDetallesCampanas() != null) {
                                        // Sum only points and bonuses from the selected campaign
                                        totalBs += v.getDetallesCampanas().stream()
                                                        .filter(dc -> campanaId.equals(dc.getCampana().getId()))
                                                        .mapToInt(dc -> dc.getBonoGanado() != null ? dc.getBonoGanado()
                                                                        : 0)
                                                        .sum();
                                        totalPuntos += v.getDetallesCampanas().stream()
                                                        .filter(dc -> campanaId.equals(dc.getCampana().getId()))
                                                        .mapToInt(dc -> dc.getPuntosGanados() != null
                                                                        ? dc.getPuntosGanados()
                                                                        : 0)
                                                        .sum();
                                } else {
                                        // Sum globally for the whole sale
                                        totalBs += v.getBonoBs();
                                        totalPuntos += v.getPuntos();
                                }
                        }
                }

                long totalVendedores = vendedorRepository.count();
                long serialTotal = serialRepository.count();
                long serialDisponibles = serialRepository.countDisponibles();
                long serialUsados = serialRepository.countUsados();

                // Top productos
                Map<String, Long> topProductos = all.stream()
                                .filter(v -> v.getProductModel() != null)
                                .collect(Collectors.groupingBy(Venta::getProductModel, Collectors.counting()));
                List<Map<String, Object>> topList = topProductos.entrySet().stream()
                                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                                .limit(5)
                                .map(e -> Map.<String, Object>of("model", e.getKey(), "count", e.getValue()))
                                .collect(Collectors.toList());

                // City stats (ventas por ciudad para el gráfico y la tabla)
                Map<String, Map<String, Object>> cityMap = new HashMap<>();
                for (Venta v : all) {
                        String c = v.getCiudad() != null && !v.getCiudad().isBlank() ? v.getCiudad() : "Otra";
                        cityMap.putIfAbsent(c, new HashMap<>(Map.of(
                                        "ciudad", c,
                                        "total", 0,
                                        "aprobadas", 0,
                                        "pendientes", 0,
                                        "rechazadas", 0,
                                        "bonusBs", 0)));
                        Map<String, Object> cStats = cityMap.get(c);
                        cStats.put("total", (int) cStats.get("total") + 1);

                        if (v.getEstado() == Venta.Estado.APROBADA) {
                                cStats.put("aprobadas", (int) cStats.get("aprobadas") + 1);

                                int bsToAdd = 0;
                                if (campanaId != null && v.getDetallesCampanas() != null) {
                                        bsToAdd = v.getDetallesCampanas().stream()
                                                        .filter(dc -> campanaId.equals(dc.getCampana().getId()))
                                                        .mapToInt(dc -> dc.getBonoGanado() != null ? dc.getBonoGanado()
                                                                        : 0)
                                                        .sum();
                                } else {
                                        bsToAdd = v.getBonoBs();
                                }
                                cStats.put("bonusBs", (int) cStats.get("bonusBs") + bsToAdd);

                        } else if (v.getEstado() == Venta.Estado.PENDIENTE) {
                                cStats.put("pendientes", (int) cStats.get("pendientes") + 1);
                        } else if (v.getEstado() == Venta.Estado.RECHAZADA) {
                                cStats.put("rechazadas", (int) cStats.get("rechazadas") + 1);
                        }
                }
                List<Map<String, Object>> ventasPorCiudad = new ArrayList<>(cityMap.values());

                // Ventas por Tipo de Producto
                Map<String, Map<String, Object>> typeMap = new HashMap<>();
                for (Venta v : all) {
                        String t = v.getProductType() != null && !v.getProductType().isBlank() ? v.getProductType()
                                        : "Otros";
                        typeMap.putIfAbsent(t, new HashMap<>(Map.of(
                                        "tipo", t,
                                        "total", 0,
                                        "bonusBs", 0)));
                        Map<String, Object> tStats = typeMap.get(t);
                        tStats.put("total", (int) tStats.get("total") + 1);

                        if (v.getEstado() == Venta.Estado.APROBADA) {
                                int bsToAdd = 0;
                                if (campanaId != null && v.getDetallesCampanas() != null) {
                                        bsToAdd = v.getDetallesCampanas().stream()
                                                        .filter(dc -> campanaId.equals(dc.getCampana().getId()))
                                                        .mapToInt(dc -> dc.getBonoGanado() != null ? dc.getBonoGanado()
                                                                        : 0)
                                                        .sum();
                                } else {
                                        bsToAdd = v.getBonoBs();
                                }
                                tStats.put("bonusBs", (int) tStats.get("bonusBs") + bsToAdd);
                        }
                }
                List<Map<String, Object>> ventasPorTipo = new ArrayList<>(typeMap.values());

                Map<String, Object> response = new HashMap<>();
                response.put("totalVentas", total);
                response.put("ventasAprobadas", aprobadas);
                response.put("ventasPendientes", pendientes);
                response.put("ventasRechazadas", rechazadas);
                response.put("totalBonusBs", totalBs);
                response.put("totalPuntos", totalPuntos);
                response.put("totalVendedores", totalVendedores);
                response.put("ventasPorCiudad", ventasPorCiudad);
                response.put("ventasPorTipo", ventasPorTipo);
                response.put("seriales",
                                Map.of("total", serialTotal, "available", serialDisponibles, "used", serialUsados));
                response.put("topProductos", topList);

                return response;
        }
}
