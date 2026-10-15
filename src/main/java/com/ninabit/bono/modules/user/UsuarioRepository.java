package com.ninabit.bono.modules.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmailAndActivoTrue(String email);

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Usuario> findByVendedorId(Long vendedorId);

    java.util.List<Usuario> findByRol(Usuario.Rol rol);

    java.util.List<Usuario> findByRolAndActivoTrue(Usuario.Rol rol);

    java.util.Optional<Usuario> findFirstByTiendaIdAndActivoTrue(Long tiendaId);

    java.util.List<Usuario> findByTiendaId(Long tiendaId);
}
