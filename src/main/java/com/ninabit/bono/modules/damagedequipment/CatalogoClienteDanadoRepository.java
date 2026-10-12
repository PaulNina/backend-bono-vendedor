package com.ninabit.bono.modules.damagedequipment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogoClienteDanadoRepository extends JpaRepository<CatalogoClienteDanado, Long>, JpaSpecificationExecutor<CatalogoClienteDanado> {

    List<CatalogoClienteDanado> findByActivoTrueOrderByNombreAsc();
    List<CatalogoClienteDanado> findAllByOrderByNombreAsc();

    Optional<CatalogoClienteDanado> findByNombreIgnoreCase(String nombre);

    List<CatalogoClienteDanado> findByActivoTrueAndNombreContainingIgnoreCaseOrderByNombreAsc(String nombre);
    List<CatalogoClienteDanado> findByNombreContainingIgnoreCaseOrderByNombreAsc(String nombre);
}
