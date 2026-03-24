package com.ninabit.bono.modules.report;

import com.ninabit.bono.modules.emailrecipient.DestinatarioEmail;
import com.ninabit.bono.modules.emailrecipient.DestinatarioEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WeeklyReportJob {

    private final DestinatarioEmailRepository destinatarioRepository;
    private final SalesReportService salesReportService;

    // Ejecuta todos los Martes a las 09:00 AM hora de Bolivia
    @Scheduled(cron = "0 0 9 * * TUE", zone = "America/La_Paz")
    public void generateAndSendWeeklyReports() {
        log.info("Iniciando ejecución de WeeklyReportJob - Reporte Semanal de Ventas");

        LocalDate today = LocalDate.now(ZoneId.of("America/La_Paz"));
        // El lunes de la semana pasada
        LocalDate lastMonday = today.minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        // El domingo de la semana pasada
        LocalDate lastSunday = lastMonday.plusDays(6);

        executeReportForDateRange(lastMonday, lastSunday);
    }

    public void executeReportForDateRange(LocalDate startDate, LocalDate endDate) {
        List<DestinatarioEmail> destinatarios = destinatarioRepository.findAll();

        if (destinatarios.isEmpty()) {
            log.info("No hay destinatarios configurados. Saltando generación de reportes.");
            return;
        }

        log.info("Procesando reportes del {} al {} para {} destinatarios", startDate, endDate, destinatarios.size());

        for (DestinatarioEmail dest : destinatarios) {
            try {
                salesReportService.processAndSendReport(
                        dest.getEmail(),
                        dest.getCiudad(),
                        dest.getGrupoId(),
                        dest.getCampanaId(),
                        startDate,
                        endDate);
            } catch (Exception e) {
                log.error("Error al despachar reporte para el correo {}", dest.getEmail(), e);
            }
        }
    }
}
