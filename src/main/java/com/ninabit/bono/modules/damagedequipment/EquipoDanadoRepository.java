package com.ninabit.bono.modules.damagedequipment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface EquipoDanadoRepository extends JpaRepository<EquipoDanado, Long>, JpaSpecificationExecutor<EquipoDanado> {

    List<EquipoDanado> findByRegistroClienteId(Long registroClienteId);

    boolean existsBySerialValidadoIgnoreCase(String serialValidado);
}

