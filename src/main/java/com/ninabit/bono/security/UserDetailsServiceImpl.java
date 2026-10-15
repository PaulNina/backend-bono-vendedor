package com.ninabit.bono.security;

import com.ninabit.bono.modules.user.Usuario;
import com.ninabit.bono.modules.user.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));

        Set<GrantedAuthority> authorities = new java.util.HashSet<>();
        Set<Usuario.Rol> userRoles = usuario.getRoles();
        if (userRoles == null || userRoles.isEmpty()) {
            if (usuario.getRol() != null) {
                userRoles = Set.of(usuario.getRol());
            } else {
                userRoles = Set.of();
            }
        }

        for (Usuario.Rol r : userRoles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + r.name()));
            if (r == Usuario.Rol.ADMIN_TOTAL) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN_OPERATIVO"));
                authorities.add(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
                authorities.add(new SimpleGrantedAuthority("ROLE_REVIEWER"));
                authorities.add(new SimpleGrantedAuthority("ROLE_REVISOR"));
            } else if (r == Usuario.Rol.ADMIN) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN_TOTAL"));
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN_OPERATIVO"));
                authorities.add(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
                authorities.add(new SimpleGrantedAuthority("ROLE_REVIEWER"));
                authorities.add(new SimpleGrantedAuthority("ROLE_REVISOR"));
            } else if (r == Usuario.Rol.ADMIN_OPERATIVO) {
                authorities.add(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
                authorities.add(new SimpleGrantedAuthority("ROLE_REVIEWER"));
                authorities.add(new SimpleGrantedAuthority("ROLE_REVISOR"));
            } else if (r == Usuario.Rol.REVISOR) {
                authorities.add(new SimpleGrantedAuthority("ROLE_REVIEWER"));
            } else if (r == Usuario.Rol.REVIEWER) {
                authorities.add(new SimpleGrantedAuthority("ROLE_REVISOR"));
            }
        }

        return new org.springframework.security.core.userdetails.User(
                usuario.getEmail(),
                usuario.getPassword(),
                authorities);
    }
}
