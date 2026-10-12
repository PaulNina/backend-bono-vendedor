package com.ninabit.bono.modules.clientcampaign;

import com.ninabit.bono.modules.audit.AuditoriaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SorteoTombolaService {

    private final SorteoPeriodoRepository sorteoPeriodoRepository;
    private final SorteoGanadorRepository sorteoGanadorRepository;
    private final TicketClienteRepository ticketClienteRepository;
    private final com.ninabit.bono.modules.vendorprize.TicketVendedorRepository ticketVendedorRepository;
    private final CampanaClienteRepository campanaClienteRepository;
    private final AuditoriaService auditoriaService;

    private final SecureRandom random = new SecureRandom();

    /**
     * Obtiene el universo de tickets participantes para un sorteo quincenal de clientes.
     */
    @Transactional(readOnly = true)
    public List<TicketCliente> obtenerParticipantesQuincenal(Long campanaId) {
        return ticketClienteRepository.findEligibleForFortnightRaffle(campanaId);
    }

    /**
     * Obtiene el universo de tickets participantes para el Sorteo Final de clientes.
     */
    @Transactional(readOnly = true)
    public List<TicketCliente> obtenerParticipantesFinal(Long campanaId) {
        return ticketClienteRepository.findEligibleForFinalRaffle(campanaId);
    }

    /**
     * Obtiene el universo de tickets de vendedores habilitados para participar.
     */
    @Transactional(readOnly = true)
    public List<com.ninabit.bono.modules.vendorprize.TicketVendedor> obtenerParticipantesVendedor(Long campanaId) {
        if (campanaId != null && campanaId > 0) {
            return ticketVendedorRepository.findByCampanaIdAndEstado(campanaId, com.ninabit.bono.modules.vendorprize.TicketVendedor.EstadoTicket.HABILITADO);
        }
        return ticketVendedorRepository.findByEstado(com.ninabit.bono.modules.vendorprize.TicketVendedor.EstadoTicket.HABILITADO);
    }

    /**
     * Ejecuta el sorteo seleccionando un ganador entre los tickets elegibles según el tipo de participante.
     */
    @Transactional
    public SorteoGanador ejecutarSorteo(
            Long sorteoPeriodoId,
            String premioNombre,
            int ordenGanador,
            String usuarioEjecutor
    ) {
        SorteoPeriodo periodo = sorteoPeriodoRepository.findById(sorteoPeriodoId)
                .orElseThrow(() -> new IllegalArgumentException("Sorteo no encontrado con ID: " + sorteoPeriodoId));

        SorteoPeriodo.TipoParticipante tipoPart = periodo.getTipoParticipante() != null 
                ? periodo.getTipoParticipante() 
                : SorteoPeriodo.TipoParticipante.CLIENTE;

        SorteoGanador ganador;

        if (tipoPart == SorteoPeriodo.TipoParticipante.VENDEDOR || tipoPart == SorteoPeriodo.TipoParticipante.TIENDA) {
            Long campanaId = periodo.getCampana() != null ? periodo.getCampana().getId() : null;
            List<com.ninabit.bono.modules.vendorprize.TicketVendedor> elegibles = obtenerParticipantesVendedor(campanaId);

            if (elegibles.isEmpty()) {
                throw new IllegalStateException("No existen tickets de vendedor habilitados para participar en este sorteo.");
            }

            int winnerIndex = random.nextInt(elegibles.size());
            com.ninabit.bono.modules.vendorprize.TicketVendedor ticketGanador = elegibles.get(winnerIndex);

            ticketGanador.setEstado(com.ninabit.bono.modules.vendorprize.TicketVendedor.EstadoTicket.PREMIADO);
            ticketVendedorRepository.save(ticketGanador);

            ganador = SorteoGanador.builder()
                    .sorteoPeriodo(periodo)
                    .ticketVendedor(ticketGanador)
                    .tipoParticipante(tipoPart.name())
                    .clienteCi(ticketGanador.getVendedor() != null ? ticketGanador.getVendedor().getCi() : "S/CI")
                    .clienteNombre(ticketGanador.getVendedor() != null ? ticketGanador.getVendedor().getNombreCompleto() : "Vendedor")
                    .clienteCelular(ticketGanador.getVendedor() != null ? ticketGanador.getVendedor().getTelefono() : "")
                    .serial(ticketGanador.getVenta() != null ? ticketGanador.getVenta().getSerial() : "")
                    .productoModelo(ticketGanador.getVenta() != null ? ticketGanador.getVenta().getProductModel() : "")
                    .premio(premioNombre != null ? premioNombre : "Premio de Sorteo Vendedor")
                    .orden(ordenGanador)
                    .build();

            ganador = sorteoGanadorRepository.save(ganador);
        } else {
            // Sorteo de Cliente Final
            Long campanaId = periodo.getCampanaCliente() != null ? periodo.getCampanaCliente().getId() : 1L;
            List<TicketCliente> elegibles;

            if (periodo.getTipoSorteo() == SorteoPeriodo.TipoSorteo.QUINCENAL) {
                elegibles = ticketClienteRepository.findEligibleForFortnightRaffle(campanaId);
            } else {
                elegibles = ticketClienteRepository.findEligibleForFinalRaffle(campanaId);
            }

            if (elegibles.isEmpty()) {
                throw new IllegalStateException("No existen tickets habilitados disponibles para participar en este sorteo.");
            }

            // Selección aleatoria criptográfica
            int winnerIndex = random.nextInt(elegibles.size());
            TicketCliente ticketGanador = elegibles.get(winnerIndex);

            // Marcar ticket como PREMIADO
            ticketGanador.setEstado(TicketCliente.EstadoTicket.PREMIADO);
            ticketClienteRepository.save(ticketGanador);

            // Crear registro de ganador inmutable
            ganador = SorteoGanador.builder()
                    .sorteoPeriodo(periodo)
                    .ticketCliente(ticketGanador)
                    .tipoParticipante("CLIENTE")
                    .clienteCi(ticketGanador.getClienteCi())
                    .clienteNombre(ticketGanador.getClienteNombre())
                    .clienteCelular(ticketGanador.getClienteCelular())
                    .serial(ticketGanador.getSerial())
                    .productoModelo(ticketGanador.getProductoModelo())
                    .premio(premioNombre != null ? premioNombre : "Premio de Campaña")
                    .orden(ordenGanador)
                    .build();

            ganador = sorteoGanadorRepository.save(ganador);

            // REGLA CRÍTICA: Si es sorteo QUINCENAL, excluir todos los tickets de este cliente (por CI) para futuras quincenas
            if (periodo.getTipoSorteo() == SorteoPeriodo.TipoSorteo.QUINCENAL) {
                int excluidos = ticketClienteRepository.excludeFromFutureFortnights(campanaId, ticketGanador.getClienteCi());
                log.info("Cliente CI {} excluido de futuras quincenas ({} tickets actualizados)", ticketGanador.getClienteCi(), excluidos);
            }
        }

        periodo.setEjecutado(true);
        periodo.setFechaEjecucion(LocalDateTime.now());
        periodo.setUsuarioEjecutor(usuarioEjecutor);
        sorteoPeriodoRepository.save(periodo);

        String descGanador = ganador.getClienteNombre() + " (Doc/CI: " + ganador.getClienteCi() + ") Premio: " + ganador.getPremio();
        auditoriaService.log(
                usuarioEjecutor,
                "SORTEO_EJECUTADO",
                "Sorteo: " + periodo.getNombre() + " [" + tipoPart + "] Ganador: " + descGanador,
                "SorteoPeriodo",
                periodo.getId().toString()
        );

        return ganador;
    }


    @Transactional(readOnly = true)
    public List<SorteoGanador> listarGanadoresPorSorteo(Long sorteoId) {
        return sorteoGanadorRepository.findBySorteoPeriodoIdOrderByOrdenAsc(sorteoId);
    }

    @Transactional(readOnly = true)
    public List<SorteoGanador> listarGanadoresPorCampana(Long campanaId) {
        return sorteoGanadorRepository.findBySorteoPeriodoCampanaClienteIdOrderByCreatedAtDesc(campanaId);
    }
}
