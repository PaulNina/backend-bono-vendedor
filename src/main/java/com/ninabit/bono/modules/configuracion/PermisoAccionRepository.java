package com.ninabit.bono.modules.configuracion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermisoAccionRepository extends JpaRepository<PermisoAccion, Long> {
    List<PermisoAccion> findByRol(String rol);
    Optional<PermisoAccion> findByRolAndModuloAndAccion(String rol, String modulo, String accion);
    void deleteByRol(String rol);
}
