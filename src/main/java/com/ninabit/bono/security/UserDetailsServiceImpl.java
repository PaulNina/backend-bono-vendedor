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

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByEmailAndActivoTrue(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()));

        if (usuario.getRol() == Usuario.Rol.ADMIN_TOTAL) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN_OPERATIVO"));
            authorities.add(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
            authorities.add(new SimpleGrantedAuthority("ROLE_REVIEWER"));
            authorities.add(new SimpleGrantedAuthority("ROLE_REVISOR"));
        } else if (usuario.getRol() == Usuario.Rol.ADMIN) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN_TOTAL"));
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN_OPERATIVO"));
            authorities.add(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
            authorities.add(new SimpleGrantedAuthority("ROLE_REVIEWER"));
            authorities.add(new SimpleGrantedAuthority("ROLE_REVISOR"));
        } else if (usuario.getRol() == Usuario.Rol.ADMIN_OPERATIVO) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
            authorities.add(new SimpleGrantedAuthority("ROLE_REVIEWER"));
            authorities.add(new SimpleGrantedAuthority("ROLE_REVISOR"));
        } else if (usuario.getRol() == Usuario.Rol.REVISOR) {
            authorities.add(new SimpleGrantedAuthority("ROLE_REVIEWER"));
        } else if (usuario.getRol() == Usuario.Rol.REVIEWER) {
            authorities.add(new SimpleGrantedAuthority("ROLE_REVISOR"));
        }

        return new org.springframework.security.core.userdetails.User(
                usuario.getEmail(),
                usuario.getPassword(),
                authorities);
    }
}
