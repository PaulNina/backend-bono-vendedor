package com.ninabit.bono.modules.clientcampaign;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SorteoPeriodoRepository extends JpaRepository<SorteoPeriodo, Long> {

    List<SorteoPeriodo> findByCampanaClienteIdOrderByFechaSorteoAsc(Long campanaClienteId);

    List<SorteoPeriodo> findByCampanaClienteIdAndEjecutadoFalseOrderByFechaSorteoAsc(Long campanaClienteId);

    List<SorteoPeriodo> findByCampanaIdOrderByFechaSorteoAsc(Long campanaId);

    List<SorteoPeriodo> findByTipoParticipanteOrderByFechaSorteoAsc(SorteoPeriodo.TipoParticipante tipoParticipante);
}

