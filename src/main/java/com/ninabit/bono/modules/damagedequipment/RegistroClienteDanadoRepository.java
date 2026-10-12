package com.ninabit.bono.modules.damagedequipment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;

public interface RegistroClienteDanadoRepository extends JpaRepository<RegistroClienteDanado, Long>, JpaSpecificationExecutor<RegistroClienteDanado> {
    List<RegistroClienteDanado> findByOrderByCreatedAtDesc();
}
