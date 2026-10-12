package com.ninabit.bono.modules.clientcampaign;

import com.ninabit.bono.modules.campaign.CampanaRepository;
import com.ninabit.bono.modules.clientcampaign.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sorteos")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Sorteos y Tómbolas")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
public class SorteosAdminController {

    private final SorteoTombolaService sorteoTombolaService;
    private final SorteoPeriodoRepository sorteoPeriodoRepository;
    private final SorteoGanadorRepository sorteoGanadorRepository;
    private final CampanaClienteRepository campanaClienteRepository;
    private final CampanaRepository campanaRepository;

    @GetMapping("/periodos")
    @Operation(summary = "Listar periodos de sorteo quincenales, final y de vendedores")
    public List<SorteoPeriodoDto> listarPeriodos(
            @RequestParam(required = false) Long campanaId,
            @RequestParam(required = false) SorteoPeriodo.TipoParticipante tipoParticipante
    ) {
        if (tipoParticipante != null) {
            if (tipoParticipante == SorteoPeriodo.TipoParticipante.VENDEDOR || tipoParticipante == SorteoPeriodo.TipoParticipante.TIENDA) {
                List<SorteoPeriodo> lista;
                if (campanaId != null) {
                    lista = sorteoPeriodoRepository.findByCampanaIdOrderByFechaSorteoAsc(campanaId);
                } else {
                    lista = sorteoPeriodoRepository.findByTipoParticipanteOrderByFechaSorteoAsc(tipoParticipante);
                }
                return lista.stream().map(SorteoPeriodoDto::fromEntity).toList();
            }
        }

        if (campanaId != null) {
            List<SorteoPeriodo> periodos = sorteoPeriodoRepository.findByCampanaClienteIdOrderByFechaSorteoAsc(campanaId);
            if (periodos.isEmpty()) {
                CampanaCliente campana = campanaClienteRepository.findById(campanaId).orElse(null);
                if (campana != null) {
                    LocalDate baseDate = campana.getFechaInicio() != null ? campana.getFechaInicio() : LocalDate.now();
                    List<SorteoPeriodo> defaults = List.of(
                        SorteoPeriodo.builder()
                            .campanaCliente(campana)
                            .nombre("1er Sorteo Quincenal")
                            .tipoSorteo(SorteoPeriodo.TipoSorteo.QUINCENAL)
                            .tipoParticipante(SorteoPeriodo.TipoParticipante.CLIENTE)
                            .fechaSorteo(baseDate.plusDays(15))
                            .fechaCorteRegistro(baseDate.plusDays(15))
                            .descripcionPremios("Televisor Skyworth 55\" Smart 4K")
                            .ejecutado(false)
                            .build(),
                        SorteoPeriodo.builder()
                            .campanaCliente(campana)
                            .nombre("2do Sorteo Quincenal")
                            .tipoSorteo(SorteoPeriodo.TipoSorteo.QUINCENAL)
                            .tipoParticipante(SorteoPeriodo.TipoParticipante.CLIENTE)
                            .fechaSorteo(baseDate.plusDays(30))
                            .fechaCorteRegistro(baseDate.plusDays(30))
                            .descripcionPremios("Televisor Skyworth 55\" Smart 4K")
                            .ejecutado(false)
                            .build(),
                        SorteoPeriodo.builder()
                            .campanaCliente(campana)
                            .nombre("3er Sorteo Quincenal")
                            .tipoSorteo(SorteoPeriodo.TipoSorteo.QUINCENAL)
                            .tipoParticipante(SorteoPeriodo.TipoParticipante.CLIENTE)
                            .fechaSorteo(baseDate.plusDays(45))
                            .fechaCorteRegistro(baseDate.plusDays(45))
                            .descripcionPremios("Televisor Skyworth 65\" Smart 4K")
                            .ejecutado(false)
                            .build(),
                        SorteoPeriodo.builder()
                            .campanaCliente(campana)
                            .nombre("Gran Sorteo Final Bs. 100.000")
                            .tipoSorteo(SorteoPeriodo.TipoSorteo.FINAL)
                            .tipoParticipante(SorteoPeriodo.TipoParticipante.CLIENTE)
                            .fechaSorteo(campana.getFechaFin() != null ? campana.getFechaFin() : baseDate.plusDays(60))
                            .fechaCorteRegistro(campana.getFechaFin() != null ? campana.getFechaFin() : baseDate.plusDays(60))
                            .descripcionPremios("Premio Mayor en Efectivo Bs. 100.000")
                            .ejecutado(false)
                            .build()
                    );
                    return sorteoPeriodoRepository.saveAll(defaults).stream().map(SorteoPeriodoDto::fromEntity).toList();
                }
            }
            return periodos.stream().map(SorteoPeriodoDto::fromEntity).toList();
        }

        return sorteoPeriodoRepository.findAll().stream().map(SorteoPeriodoDto::fromEntity).toList();
    }

    @PostMapping("/periodos")
    @Operation(summary = "Crear un nuevo periodo de sorteo")
    public ResponseEntity<SorteoPeriodoDto> crearPeriodo(
            @RequestBody SorteoPeriodo periodo,
            @RequestParam(required = false) Long campanaId
    ) {
        if (periodo.getTipoParticipante() == null) {
            periodo.setTipoParticipante(SorteoPeriodo.TipoParticipante.CLIENTE);
        }

        if (periodo.getTipoParticipante() == SorteoPeriodo.TipoParticipante.VENDEDOR || periodo.getTipoParticipante() == SorteoPeriodo.TipoParticipante.TIENDA) {
            if (campanaId != null) {
                campanaRepository.findById(campanaId).ifPresent(periodo::setCampana);
            }
        } else {
            if (campanaId != null) {
                campanaClienteRepository.findById(campanaId).ifPresent(periodo::setCampanaCliente);
            }
        }

        if (periodo.getFechaSorteo() == null) {
            periodo.setFechaSorteo(LocalDate.now());
        }
        if (periodo.getFechaCorteRegistro() == null) {
            periodo.setFechaCorteRegistro(periodo.getFechaSorteo());
        }
        if (periodo.getTipoSorteo() == null) {
            periodo.setTipoSorteo(SorteoPeriodo.TipoSorteo.QUINCENAL);
        }
        periodo.setEjecutado(false);
        SorteoPeriodo saved = sorteoPeriodoRepository.save(periodo);
        return ResponseEntity.ok(SorteoPeriodoDto.fromEntity(saved));
    }

    @GetMapping("/participantes-quincenal")
    @Operation(summary = "Obtener universo de tickets habilitados para sorteo quincenal (excluyendo ganadores previos)")
    public ResponseEntity<List<TicketClienteDto>> participantesQuincenal(@RequestParam Long campanaId) {
        List<TicketClienteDto> dtos = sorteoTombolaService.obtenerParticipantesQuincenal(campanaId).stream()
                .map(TicketClienteDto::fromEntity)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/participantes-final")
    @Operation(summary = "Obtener universo de todos los tickets habilitados para el Gran Sorteo Final de Bs. 100.000")
    public ResponseEntity<List<TicketClienteDto>> participantesFinal(@RequestParam Long campanaId) {
        List<TicketClienteDto> dtos = sorteoTombolaService.obtenerParticipantesFinal(campanaId).stream()
                .map(TicketClienteDto::fromEntity)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/participantes-vendedor")
    @Operation(summary = "Obtener universo de tickets habilitados para sorteo de vendedores")
    public ResponseEntity<List<TicketVendedorDto>> participantesVendedor(
            @RequestParam(required = false) Long campanaId
    ) {
        List<TicketVendedorDto> dtos = sorteoTombolaService.obtenerParticipantesVendedor(campanaId).stream()
                .map(TicketVendedorDto::fromEntity)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/ejecutar")
    @Operation(summary = "Ejecutar la tómbola digital y emitir el ticket ganador")
    public ResponseEntity<Map<String, Object>> ejecutarSorteo(
            @RequestParam Long sorteoPeriodoId,
            @RequestParam(required = false) String premioNombre,
            @RequestParam(defaultValue = "1") int ordenGanador,
            Authentication auth
    ) {
        try {
            String usuarioEjecutor = auth != null ? auth.getName() : "ADMIN_SORTEO";
            SorteoGanador ganador = sorteoTombolaService.ejecutarSorteo(
                    sorteoPeriodoId, premioNombre, ordenGanador, usuarioEjecutor
            );
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "mensaje", "¡Ganador seleccionado exitosamente!",
                    "ganador", SorteoGanadorDto.fromEntity(ganador)
            ));
        } catch (Exception e) {
            log.error("Error al ejecutar sorteo", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/ganadores")
    @Operation(summary = "Listar ganadores de sorteos por periodo o por campaña")
    public ResponseEntity<List<SorteoGanadorDto>> listarGanadores(
            @RequestParam(required = false) Long sorteoPeriodoId,
            @RequestParam(required = false) Long campanaId
    ) {
        if (sorteoPeriodoId != null) {
            List<SorteoGanadorDto> list = sorteoTombolaService.listarGanadoresPorSorteo(sorteoPeriodoId).stream()
                    .map(SorteoGanadorDto::fromEntity)
                    .toList();
            return ResponseEntity.ok(list);
        }
        if (campanaId != null) {
            List<SorteoGanadorDto> list = sorteoTombolaService.listarGanadoresPorCampana(campanaId).stream()
                    .map(SorteoGanadorDto::fromEntity)
                    .toList();
            return ResponseEntity.ok(list);
        }
        List<SorteoGanadorDto> list = sorteoGanadorRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(SorteoGanadorDto::fromEntity)
                .toList();
        return ResponseEntity.ok(list);
    }
}
