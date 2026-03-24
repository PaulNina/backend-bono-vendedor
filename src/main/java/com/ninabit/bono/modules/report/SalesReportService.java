package com.ninabit.bono.modules.report;

import com.ninabit.bono.modules.citygroup.GrupoCiudad;
import com.ninabit.bono.modules.citygroup.GrupoCiudadRepository;
import com.ninabit.bono.modules.email.EmailService;
import com.ninabit.bono.modules.sale.Venta;
import com.ninabit.bono.modules.sale.VentaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesReportService {

    private final VentaRepository ventaRepository;
    private final SalesExcelGenerator excelGenerator;
    private final EmailService emailService;
    private final GrupoCiudadRepository grupoCiudadRepository;

    @Transactional(readOnly = true)
    public void processAndSendReport(String toEmail, String ciudadTarget, Long grupoIdTarget, Long campanaIdTarget,
            LocalDate start,
            LocalDate end) {
        List<Venta> sales;
        String subjectTarget = "";

        if (grupoIdTarget != null) {
            Optional<GrupoCiudad> grupoOp = grupoCiudadRepository.findById(grupoIdTarget);
            if (grupoOp.isPresent()) {
                GrupoCiudad grupo = grupoOp.get();
                subjectTarget = "Grupo: " + grupo.getNombre();
                if (grupo.getCiudades() != null && !grupo.getCiudades().isEmpty()) {
                    sales = ventaRepository.findByEstadoAndSaleDateBetweenAndCiudadIn(Venta.Estado.APROBADA, start, end,
                            grupo.getCiudades());
                } else {
                    sales = Collections.emptyList();
                }
            } else {
                log.warn("Grupo con ID {} no encontrado para enviar correo a {}", grupoIdTarget, toEmail);
                return;
            }
        } else if (ciudadTarget != null && !ciudadTarget.isEmpty()) {
            subjectTarget = ciudadTarget;
            sales = ventaRepository.findByEstadoAndSaleDateBetweenAndCiudadIn(Venta.Estado.APROBADA, start, end,
                    List.of(ciudadTarget));
        } else {
            // General
            subjectTarget = "Múltiples Ciudades";
            sales = ventaRepository.findByEstadoAndSaleDateBetween(Venta.Estado.APROBADA, start, end);
        }

        if (sales.isEmpty()) {
            log.info("No hay ventas para {} entre {} y {}. No se enviará reporte.", subjectTarget, start, end);
            return;
        }

        try {
            byte[] excelFile = excelGenerator.generateWeeklySalesReport(sales, campanaIdTarget, start, end,
                    subjectTarget);
            String subject = "Reporte de Ventas Aprobadas - " + subjectTarget;
            if (campanaIdTarget != null) {
                subject += " - Campaña " + campanaIdTarget;
            }
            String text = String.format(
                    "Hola,\n\nAdjunto encontrarás el reporte de ventas aprobadas correspondiente al periodo del %s al %s para %s.\n\nSaludos.",
                    start, end, subjectTarget);

            emailService.sendEmailWithAttachment(toEmail, subject, text,
                    "Reporte_Ventas_" + start + "_" + end + ".xlsx", excelFile);
            log.info("Reporte enviado con éxito a {}", toEmail);
        } catch (Exception e) {
            log.error("Fallo al generar y enviar reporte a {}", toEmail, e);
        }
    }
}
