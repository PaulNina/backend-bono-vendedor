package com.ninabit.bono.modules.clientcampaign;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EntregaRuletaRepository extends JpaRepository<EntregaRuleta, Long> {

    Optional<EntregaRuleta> findByRegistroClienteId(Long registroClienteId);

    List<EntregaRuleta> findByCampanaClienteIdAndImpulsadorId(Long campanaClienteId, Long impulsadorId);

    List<EntregaRuleta> findByCampanaClienteIdAndPremioRuletaIdAndImpulsadorIdOrderByFechaGiroDesc(
            Long campanaClienteId, Long premioRuletaId, Long impulsadorId
    );

    List<EntregaRuleta> findByCampanaClienteIdAndPremioRuletaIdOrderByFechaGiroDesc(
            Long campanaClienteId, Long premioRuletaId
    );

    List<EntregaRuleta> findByCampanaClienteIdOrderByFechaGiroDesc(Long campanaClienteId);

    long countByCampanaClienteIdAndPremioRuletaIdAndRevertidoFalse(Long campanaClienteId, Long premioRuletaId);
}
