package com.ninabit.bono.modules.city;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CiudadRepository extends JpaRepository<Ciudad, Long> {
    List<Ciudad> findByActivoTrueOrderByOrdenAsc();

    List<Ciudad> findAllByOrderByOrdenAsc();
}
