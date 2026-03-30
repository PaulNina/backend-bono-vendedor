package com.ninabit.bono.modules.city;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CiudadRepository extends JpaRepository<Ciudad, Long> {
    List<Ciudad> findByActivoTrueOrderByOrdenAsc();

    List<Ciudad> findAllByOrderByOrdenAsc();

    Optional<Ciudad> findByNombreIgnoreCase(String nombre);
}
