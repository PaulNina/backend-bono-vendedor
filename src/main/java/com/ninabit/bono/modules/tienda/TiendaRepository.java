package com.ninabit.bono.modules.tienda;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TiendaRepository extends JpaRepository<Tienda, Long> {
    List<Tienda> findByCiudadNombre(String ciudadNombre);

    List<Tienda> findByActivoTrue();

    List<Tienda> findByCiudadNombreAndActivoTrue(String ciudadNombre);

    java.util.Optional<Tienda> findByCodigo(String codigo);

    java.util.Optional<Tienda> findFirstByNombreIgnoreCase(String nombre);

    List<Tienda> findByDepartamentoAndActivoTrue(String departamento);
}
