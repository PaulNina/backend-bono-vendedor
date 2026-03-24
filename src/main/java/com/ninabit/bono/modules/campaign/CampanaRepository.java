package com.ninabit.bono.modules.campaign;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CampanaRepository extends JpaRepository<Campana, Long> {
    Optional<Campana> findFirstByActivoTrue();
}
