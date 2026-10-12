package com.ninabit.bono.modules.damagedequipment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CatalogoFallaRepository extends JpaRepository<CatalogoFalla, Long> {
    List<CatalogoFalla> findByActivoTrueOrderByNombreAsc();
    List<CatalogoFalla> findAllByOrderByNombreAsc();
    Optional<CatalogoFalla> findByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCase(String nombre);
}
