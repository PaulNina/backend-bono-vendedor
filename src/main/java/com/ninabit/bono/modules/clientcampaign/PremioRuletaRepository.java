package com.ninabit.bono.modules.clientcampaign;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PremioRuletaRepository extends JpaRepository<PremioRuleta, Long> {

    List<PremioRuleta> findByCampanaClienteId(Long campanaClienteId);

    List<PremioRuleta> findByCampanaClienteIdAndActivoTrueOrderByOrdenRuletaAsc(Long campanaClienteId);

    List<PremioRuleta> findByCampanaClienteIdOrderByOrdenRuletaAsc(Long campanaClienteId);

    java.util.Optional<PremioRuleta> findFirstByCampanaClienteIdAndEsSinPremioTrue(Long campanaClienteId);
}
