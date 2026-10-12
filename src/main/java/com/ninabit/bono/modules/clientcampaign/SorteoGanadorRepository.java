package com.ninabit.bono.modules.clientcampaign;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SorteoGanadorRepository extends JpaRepository<SorteoGanador, Long> {

    List<SorteoGanador> findBySorteoPeriodoIdOrderByOrdenAsc(Long sorteoPeriodoId);

    List<SorteoGanador> findBySorteoPeriodoCampanaClienteIdOrderByCreatedAtDesc(Long campanaClienteId);

    boolean existsBySorteoPeriodoCampanaClienteIdAndClienteCi(Long campanaClienteId, String clienteCi);
}
