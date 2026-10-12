package com.ninabit.bono.security;

import com.ninabit.bono.modules.configuracion.PermisoAccion;
import com.ninabit.bono.modules.configuracion.PermisoAccionRepository;
import com.ninabit.bono.modules.user.Usuario;
import com.ninabit.bono.modules.user.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service("authorizationService")
@RequiredArgsConstructor
@Slf4j
public class AuthorizationService {

    private final PermisoAccionRepository permisoAccionRepository;
    private final UsuarioRepository usuarioRepository;

    public boolean can(String modulo, String accion) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return can(auth, modulo, accion);
    }

    public boolean can(Authentication auth, String modulo, String accion) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return false;
        }

        String email = auth.getName();
        Optional<Usuario> userOpt = usuarioRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return false;
        }

        Usuario usuario = userOpt.get();
        if (!usuario.isActivo()) {
            return false;
        }

        Usuario.Rol rol = usuario.getRol();
        String rolName = rol.name();
        String modUpper = modulo.toUpperCase().trim();
        String accUpper = accion.toUpperCase().trim();

        // 1. ADMIN_TOTAL y ADMIN tienen superpoderes completos
        if (rol == Usuario.Rol.ADMIN_TOTAL || rol == Usuario.Rol.ADMIN) {
            return true;
        }

        // 2. Blindaje Estricto de Bajas: La acción ELIMINAR en VENTAS o USUARIOS
        // está reservada exclusivamente para ADMIN_TOTAL y ADMIN
        if (accUpper.equals("ELIMINAR") && (modUpper.equals("VENTAS") || modUpper.equals("USUARIOS") || modUpper.equals("CONFIG"))) {
            log.warn("Intento denegado de eliminación en {} por usuario {} con rol {}", modUpper, email, rolName);
            return false;
        }

        // 3. Consultar configuración explícita en base de datos
        Optional<PermisoAccion> dbPermiso = permisoAccionRepository.findByRolAndModuloAndAccion(rolName, modUpper, accUpper);
        if (dbPermiso.isPresent()) {
            return dbPermiso.get().isPermitido();
        }

        // 4. Jerarquía y políticas por defecto según el rol
        return getDefaultPermission(rol, modUpper, accUpper);
    }

    public boolean canDeleteSale(Authentication auth) {
        return can(auth, "VENTAS", "ELIMINAR");
    }

    private boolean getDefaultPermission(Usuario.Rol rol, String modulo, String accion) {
        switch (rol) {
            case ADMIN_OPERATIVO:
                // Puede hacer todo excepto eliminar ventas/usuarios y tocar config sensible
                return !accion.equals("ELIMINAR");

            case SUPERVISOR:
                if (modulo.equals("VENTAS")) {
                    return accion.equals("VER") || accion.equals("APROBAR") || accion.equals("EDITAR") || accion.equals("EXPORTAR");
                }
                if (modulo.equals("TIENDAS") || modulo.equals("VENDEDORES") || modulo.equals("REPORTES") || modulo.equals("AUDITORIA") || modulo.equals("EQUIPOS_DANADOS")) {
                    return accion.equals("VER") || accion.equals("EDITAR") || accion.equals("EXPORTAR");
                }
                return false;

            case REVISOR:
            case REVIEWER:
                if (modulo.equals("VENTAS")) {
                    return accion.equals("VER") || accion.equals("APROBAR");
                }
                if (modulo.equals("REPORTES")) {
                    return accion.equals("VER");
                }
                return false;

            case ENCARGADO_TIENDA:
                if (modulo.equals("VENTAS") || modulo.equals("TIENDAS") || modulo.equals("VENDEDORES")) {
                    return accion.equals("VER");
                }
                return false;

            case VISOR:
                return accion.equals("VER") || accion.equals("EXPORTAR");

            case VENDOR:
                return modulo.equals("VENTAS") && (accion.equals("VER") || accion.equals("CREAR"));

            case OPERADOR_BANDEO:
                return modulo.equals("BANDEO") && (accion.equals("VER") || accion.equals("CREAR"));

            case TECNICO:
                return modulo.equals("EQUIPOS_DANADOS") && (accion.equals("VER") || accion.equals("CREAR") || accion.equals("EDITAR"));

            default:
                return false;
        }
    }
}
