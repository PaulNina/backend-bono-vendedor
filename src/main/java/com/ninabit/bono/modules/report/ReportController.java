package com.ninabit.bono.modules.report;

import com.ninabit.bono.modules.sale.Venta;
import com.ninabit.bono.modules.sale.VentaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Tag(name = "Reportes")
public class ReportController {

    private final VentaRepository ventaRepository;
    private final SalesReportService salesReportService;
    private final WeeklyReportJob weeklyReportJob;

    @GetMapping("/weekly")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reporte semanal acumulado")
    public List<Map<String, Object>> weeklyReport() {
        List<Venta> all = ventaRepository.findAllOrderByDate();

        // Agrupar por semana (número de semana de campaña)
        Map<LocalDate, List<Venta>> byWeek = new TreeMap<>();
        for (Venta v : all) {
            if (v.getSaleDate() == null)
                continue;
            // Obtener inicio de semana (lunes)
            LocalDate monday = v.getSaleDate().with(DayOfWeek.MONDAY);
            byWeek.computeIfAbsent(monday, k -> new ArrayList<>()).add(v);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        int weekNum = 1;
        int cumUnits = 0, cumBs = 0;

        for (Map.Entry<LocalDate, List<Venta>> entry : byWeek.entrySet()) {
            List<Venta> semVentas = entry.getValue();
            int units = semVentas.size();
            int monto = semVentas.stream().mapToInt(Venta::getBonoBs).sum();
            cumUnits += units;
            cumBs += monto;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("semana", "Semana " + weekNum++);
            row.put("fechaInicio", entry.getKey().toString());
            row.put("unidades", units);
            row.put("monto_bs", monto);
            row.put("acum_unidades", cumUnits);
            row.put("acum_monto_bs", cumBs);
            result.add(row);
        }

        return result;
    }

    @PostMapping("/send-manual")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enviar reporte de ventas manualmente")
    public ResponseEntity<Map<String, String>> sendManualReport(
            @RequestParam String toEmail,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) Long grupoId,
            @RequestParam(required = false) Long campanaId,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        salesReportService.processAndSendReport(toEmail, ciudad, grupoId, campanaId, start, end);

        return ResponseEntity.ok(Map.of("message", "Reporte manual despachado al correo: " + toEmail));
    }

    @PostMapping("/test-weekly-trigger")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Forzar ejecución del Job Semanal (Testing)")
    public ResponseEntity<Map<String, String>> triggerWeeklyJob() {
        weeklyReportJob.generateAndSendWeeklyReports();
        return ResponseEntity.ok(Map.of("message", "Job Semanal disparado manualmente."));
    }
}
