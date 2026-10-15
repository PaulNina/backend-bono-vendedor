package com.ninabit.bono.initializer;

import com.ninabit.bono.modules.city.Ciudad;
import com.ninabit.bono.modules.city.CiudadRepository;
import com.ninabit.bono.modules.product.type.TipoProducto;
import com.ninabit.bono.modules.product.type.TipoProductoRepository;
import com.ninabit.bono.modules.configuracion.ConfiguracionGlobal;
import com.ninabit.bono.modules.configuracion.ConfiguracionGlobalRepository;
import com.ninabit.bono.modules.configuracion.RolPermiso;
import com.ninabit.bono.modules.configuracion.RolPermisoRepository;
import com.ninabit.bono.modules.configuracion.PermisoAccion;
import com.ninabit.bono.modules.configuracion.PermisoAccionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import com.ninabit.bono.modules.damagedequipment.CatalogoFalla;
import com.ninabit.bono.modules.damagedequipment.CatalogoFallaRepository;
import com.ninabit.bono.modules.user.Usuario;
import com.ninabit.bono.modules.user.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

        private final CiudadRepository ciudadRepository;
        private final TipoProductoRepository tipoProductoRepository;
        private final RolPermisoRepository rolPermisoRepository;
        private final PermisoAccionRepository permisoAccionRepository;
        private final ConfiguracionGlobalRepository configuracionGlobalRepository;
        private final CatalogoFallaRepository catalogoFallaRepository;
        private final JdbcTemplate jdbcTemplate;
        private final UsuarioRepository usuarioRepository;
        private final PasswordEncoder passwordEncoder;

        @Override
        public void run(String... args) {
                dropLegacyColumns();
                fixUserRolConstraint();
                seedRolesPermisos();
                seedGranularPermissions();
                seedConfiguracionGlobal();
                seedTipoProductos();
                seedCities();
                seedFallas();
                seedUsers();
                autoRepararTicketsRegistros();
                syncPostgresSequences();
                log.info("DataInitializer: Inicialización completada.");
        }

        private void seedRolesPermisos() {
                // ADMIN_TOTAL (Acceso total a todos los módulos)
                if (rolPermisoRepository.findById("ADMIN_TOTAL").isEmpty()) {
                        rolPermisoRepository.save(RolPermiso.builder()
                                        .rol("ADMIN_TOTAL")
                                        .modulosPermitidos("/admin/revisiones,/admin/auditoria,/admin/metricas,/admin/reportes,/admin/pagos,/admin/campanas,/admin/productos,/admin/serials,/admin/serials-restringidos,/admin/vendedores,/admin/tiendas,/admin/grupos-ciudades,/admin/ciudades,/admin/usuarios-roles,/admin/solicitudes-registro,/admin/configuracion,/admin/configuracion-smtp,/admin/whatsapp-config,/admin/popups,/admin/equipos-danados,/admin/bandeo-ruleta,/admin/campana-cliente,/admin/supervisor-cliente,/admin/inventarios-premios,/admin/tombola-sorteo")
                                        .updatedAt(LocalDateTime.now())
                                        .build());
                }

                // ADMIN_OPERATIVO (Módulos operativos sin configuraciones de sistema o roles)
                if (rolPermisoRepository.findById("ADMIN_OPERATIVO").isEmpty()) {
                        rolPermisoRepository.save(RolPermiso.builder()
                                        .rol("ADMIN_OPERATIVO")
                                        .modulosPermitidos("/admin/revisiones,/admin/metricas,/admin/reportes,/admin/pagos,/admin/campanas,/admin/productos,/admin/serials,/admin/serials-restringidos,/admin/vendedores,/admin/tiendas,/admin/grupos-ciudades,/admin/ciudades,/admin/solicitudes-registro,/admin/equipos-danados,/admin/bandeo-ruleta,/admin/campana-cliente,/admin/supervisor-cliente")
                                        .updatedAt(LocalDateTime.now())
                                        .build());
                }

                // REVISOR
                if (rolPermisoRepository.findById("REVISOR").isEmpty()) {
                        rolPermisoRepository.save(RolPermiso.builder()
                                        .rol("REVISOR")
                                        .modulosPermitidos("/admin/revisiones,/admin/reportes")
                                        .updatedAt(LocalDateTime.now())
                                        .build());
                }

                // ENCARGADO_TIENDA
                if (rolPermisoRepository.findById("ENCARGADO_TIENDA").isEmpty()) {
                        rolPermisoRepository.save(RolPermiso.builder()
                                        .rol("ENCARGADO_TIENDA")
                                        .modulosPermitidos("/admin/revisiones,/admin/vendedores,/admin/tiendas,/admin/reportes")
                                        .updatedAt(LocalDateTime.now())
                                        .build());
                }

                // VISOR (Solo lectura ejecutiva)
                if (rolPermisoRepository.findById("VISOR").isEmpty()) {
                        rolPermisoRepository.save(RolPermiso.builder()
                                        .rol("VISOR")
                                        .modulosPermitidos("/admin/reportes,/admin/metricas")
                                        .updatedAt(LocalDateTime.now())
                                        .build());
                }

                // Ensure TECNICO role is seeded
                if (rolPermisoRepository.findById("TECNICO").isEmpty()) {
                        rolPermisoRepository.save(RolPermiso.builder()
                                        .rol("TECNICO")
                                        .modulosPermitidos("/admin/equipos-danados,/admin/equipos-danados/nuevo")
                                        .updatedAt(LocalDateTime.now())
                                        .build());
                        log.info("DataInitializer: Rol TECNICO sembrado.");
                }

                // Update SUPERVISOR if exists or insert
                rolPermisoRepository.findById("SUPERVISOR").ifPresentOrElse(
                                sup -> {
                                        if (!sup.getModulosPermitidos().contains("/admin/equipos-danados")) {
                                                sup.setModulosPermitidos(sup.getModulosPermitidos() + ",/admin/equipos-danados,/admin/equipos-danados/nuevo");
                                                rolPermisoRepository.save(sup);
                                        }
                                },
                                () -> rolPermisoRepository.save(RolPermiso.builder()
                                                .rol("SUPERVISOR")
                                                .modulosPermitidos("/admin/revisiones,/admin/auditoria,/admin/metricas,/admin/reportes,/admin/vendedores,/admin/tiendas,/admin/equipos-danados,/admin/equipos-danados/nuevo")
                                                .updatedAt(LocalDateTime.now())
                                                .build())
                );

                if (rolPermisoRepository.findById("REVIEWER").isEmpty()) {
                        rolPermisoRepository.save(RolPermiso.builder()
                                        .rol("REVIEWER")
                                        .modulosPermitidos("/admin/revisiones,/admin/reportes")
                                        .updatedAt(LocalDateTime.now())
                                        .build());
                }
        }

        private void seedGranularPermissions() {
                if (permisoAccionRepository.count() > 0) return;

                log.info("DataInitializer: Sembrando matriz inicial de permisos granulares por acción...");
                // Reglas clave de Blindaje de Bajas:
                // Solo ADMIN_TOTAL y ADMIN pueden ELIMINAR en VENTAS
                permisoAccionRepository.save(PermisoAccion.builder().rol("ADMIN_TOTAL").modulo("VENTAS").accion("ELIMINAR").permitido(true).build());
                permisoAccionRepository.save(PermisoAccion.builder().rol("ADMIN").modulo("VENTAS").accion("ELIMINAR").permitido(true).build());
                permisoAccionRepository.save(PermisoAccion.builder().rol("ADMIN_OPERATIVO").modulo("VENTAS").accion("ELIMINAR").permitido(false).build());
                permisoAccionRepository.save(PermisoAccion.builder().rol("SUPERVISOR").modulo("VENTAS").accion("ELIMINAR").permitido(false).build());
                permisoAccionRepository.save(PermisoAccion.builder().rol("REVISOR").modulo("VENTAS").accion("ELIMINAR").permitido(false).build());
                permisoAccionRepository.save(PermisoAccion.builder().rol("REVIEWER").modulo("VENTAS").accion("ELIMINAR").permitido(false).build());
                permisoAccionRepository.save(PermisoAccion.builder().rol("ENCARGADO_TIENDA").modulo("VENTAS").accion("ELIMINAR").permitido(false).build());
                permisoAccionRepository.save(PermisoAccion.builder().rol("VISOR").modulo("VENTAS").accion("ELIMINAR").permitido(false).build());

                // Permisos de Aprobación
                permisoAccionRepository.save(PermisoAccion.builder().rol("ADMIN_TOTAL").modulo("VENTAS").accion("APROBAR").permitido(true).build());
                permisoAccionRepository.save(PermisoAccion.builder().rol("ADMIN").modulo("VENTAS").accion("APROBAR").permitido(true).build());
                permisoAccionRepository.save(PermisoAccion.builder().rol("ADMIN_OPERATIVO").modulo("VENTAS").accion("APROBAR").permitido(true).build());
                permisoAccionRepository.save(PermisoAccion.builder().rol("SUPERVISOR").modulo("VENTAS").accion("APROBAR").permitido(true).build());
                permisoAccionRepository.save(PermisoAccion.builder().rol("REVISOR").modulo("VENTAS").accion("APROBAR").permitido(true).build());
                permisoAccionRepository.save(PermisoAccion.builder().rol("REVIEWER").modulo("VENTAS").accion("APROBAR").permitido(true).build());

                // Pagos
                permisoAccionRepository.save(PermisoAccion.builder().rol("ADMIN_TOTAL").modulo("PAGOS").accion("PAGAR").permitido(true).build());
                permisoAccionRepository.save(PermisoAccion.builder().rol("ADMIN").modulo("PAGOS").accion("PAGAR").permitido(true).build());
                permisoAccionRepository.save(PermisoAccion.builder().rol("ADMIN_OPERATIVO").modulo("PAGOS").accion("PAGAR").permitido(true).build());
                permisoAccionRepository.save(PermisoAccion.builder().rol("SUPERVISOR").modulo("PAGOS").accion("PAGAR").permitido(false).build());
                log.info("DataInitializer: Matriz inicial de permisos granulares sembrada exitosamente.");
        }

        private void seedFallas() {
                if (catalogoFallaRepository.count() > 0)
                        return;

                List<String> fallasComunes = List.of(
                                "Pantalla rota / estrellada",
                                "Sin imagen / Pantalla negra",
                                "Líneas verticales / horizontales en pantalla",
                                "Falla de encendido / No prende",
                                "Falla de audio / Sin sonido",
                                "Control remoto / Sensor no responde",
                                "Falla de puertos HDMI / USB",
                                "Falla de placa / Mainboard / Fuente de poder",
                                "Daños físicos / Golpes en gabinete",
                                "Falla de conexión WiFi / Smart TV"
                );

                for (String nombre : fallasComunes) {
                        catalogoFallaRepository.save(CatalogoFalla.builder()
                                        .nombre(nombre)
                                        .descripcion("Falla común estándar")
                                        .activo(true)
                                        .build());
                }
                log.info("DataInitializer: {} fallas comunes sembradas.", fallasComunes.size());
        }

        private void dropLegacyColumns() {
                try {
                        log.info("Eliminando columnas obsoletas 'ciudad' de tiendas y vendedores...");
                        jdbcTemplate.execute("ALTER TABLE tiendas DROP COLUMN IF EXISTS ciudad");
                        jdbcTemplate.execute("ALTER TABLE vendedores DROP COLUMN IF EXISTS ciudad");
                        jdbcTemplate.execute("ALTER TABLE ventas ADD COLUMN IF NOT EXISTS puntos_tienda INTEGER DEFAULT 0");
                        jdbcTemplate.execute("ALTER TABLE ventas ADD COLUMN IF NOT EXISTS bono_bs_tienda INTEGER DEFAULT 0");
                        jdbcTemplate.execute("ALTER TABLE venta_campana ADD COLUMN IF NOT EXISTS puntos_tienda_ganados INTEGER DEFAULT 0");
                        jdbcTemplate.execute("ALTER TABLE venta_campana ADD COLUMN IF NOT EXISTS bono_tienda_ganado INTEGER DEFAULT 0");
                        jdbcTemplate.execute("UPDATE ventas SET puntos_tienda = 0 WHERE puntos_tienda IS NULL");
                        jdbcTemplate.execute("UPDATE ventas SET bono_bs_tienda = 0 WHERE bono_bs_tienda IS NULL");
                        log.info("Columnas actualizadas exitosamente.");
                } catch (Exception e) {
                        log.warn("No se pudo actualizar columnas de base de datos: " + e.getMessage());
                }
        }

        private void fixUserRolConstraint() {
                try {
                        log.info("Actualizando restricción usuarios_rol_check en la tabla usuarios...");
                        jdbcTemplate.execute("ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS usuarios_rol_check");
                        jdbcTemplate.execute("ALTER TABLE usuarios ADD CONSTRAINT usuarios_rol_check CHECK (rol IN ('ADMIN_TOTAL', 'ADMIN', 'ADMIN_OPERATIVO', 'SUPERVISOR', 'REVISOR', 'REVIEWER', 'ENCARGADO_TIENDA', 'VISOR', 'VENDOR', 'TECNICO', 'OPERADOR_BANDEO'))");
                        log.info("Restricción usuarios_rol_check actualizada exitosamente con roles granulares.");

                        log.info("Actualizando restricción ventas_estado_check en la tabla ventas...");
                        jdbcTemplate.execute("ALTER TABLE ventas DROP CONSTRAINT IF EXISTS ventas_estado_check");
                        jdbcTemplate.execute("ALTER TABLE ventas ADD CONSTRAINT ventas_estado_check CHECK (estado IN ('REGISTRADA', 'PENDIENTE', 'OBSERVADA', 'APROBADA', 'RECHAZADA', 'ANULADA', 'CERRADA'))");
                        log.info("Restricción ventas_estado_check actualizada exitosamente.");

                        log.info("Actualizando restricción chatbot_config_proveedor_ia_check y columna base_url...");
                        jdbcTemplate.execute("ALTER TABLE chatbot_config ADD COLUMN IF NOT EXISTS base_url VARCHAR(255)");
                        jdbcTemplate.execute("ALTER TABLE chatbot_config ADD COLUMN IF NOT EXISTS wa_header_image_url VARCHAR(500)");
                        jdbcTemplate.execute("ALTER TABLE chatbot_config DROP CONSTRAINT IF EXISTS chatbot_config_proveedor_ia_check");
                        jdbcTemplate.execute("ALTER TABLE chatbot_config ADD CONSTRAINT chatbot_config_proveedor_ia_check CHECK (proveedor_ia IN ('OPENAI', 'GEMINI', 'DEEPSEEK'))");
                        log.info("Restricción chatbot_config_proveedor_ia_check actualizada exitosamente.");

                        log.info("Haciendo vendedor_id nullable en inventario_impulsador...");
                        jdbcTemplate.execute("ALTER TABLE inventario_impulsador ALTER COLUMN vendedor_id DROP NOT NULL");
                        jdbcTemplate.execute("ALTER TABLE inventario_impulsador DROP CONSTRAINT IF EXISTS ukeh5mvyxdw4s7b0hhvfr71g5fa");
                        log.info("Tabla inventario_impulsador actualizada para múltiples roles.");
                } catch (Exception e) {
                        log.warn("No se pudo actualizar restricciones de base de datos: " + e.getMessage());
                }
        }

        private void seedCities() {
                String[][] cityData = {
                                { "La Paz", "0", "La Paz" }, { "El Alto", "1", "La Paz" },
                                { "Cochabamba", "2", "Cochabamba" },
                                { "Santa Cruz", "3", "Santa Cruz" }, { "Oruro", "4", "Oruro" },
                                { "Potosí", "5", "Potosí" },
                                { "Sucre", "6", "Chuquisaca" }, { "Tarija", "7", "Tarija" },
                                { "Trinidad", "8", "Beni" },
                                { "Cobija", "9", "Pando" }, { "Quillacollo", "10", "Cochabamba" },
                                { "Sacaba", "11", "Cochabamba" },
                                { "Montero", "12", "Santa Cruz" }, { "Warnes", "13", "Santa Cruz" },
                                { "Riberalta", "14", "Beni" },
                                { "Yacuiba", "15", "Tarija" }, { "Llallagua", "16", "Potosí" },
                                { "Camiri", "17", "Santa Cruz" }
                };

                if (ciudadRepository.count() > 0) {
                        // Update existing cities to have a department
                        for (Ciudad c : ciudadRepository.findAll()) {
                                if (c.getDepartamento() == null || c.getDepartamento().isEmpty()) {
                                        for (String[] cd : cityData) {
                                                if (c.getNombre().equalsIgnoreCase(cd[0])) {
                                                        c.setDepartamento(cd[2]);
                                                        ciudadRepository.save(c);
                                                }
                                        }
                                }
                        }
                        return;
                }
                for (String[] cd : cityData) {
                        ciudadRepository.save(Ciudad.builder()
                                        .nombre(cd[0])
                                        .orden(Integer.parseInt(cd[1]))
                                        .departamento(cd[2])
                                        .activo(true)
                                        .build());
                }
                log.info("DataInitializer: {} ciudades sembradas.", cityData.length);
        }

        private void seedUsers() {
                if (!usuarioRepository.existsByEmail("admin@skyworth.com")) {
                        usuarioRepository.save(Usuario.builder()
                                        .email("admin@skyworth.com")
                                        .password(passwordEncoder.encode("admin123."))
                                        .rol(Usuario.Rol.ADMIN_TOTAL)
                                        .nombreCompleto("Administrador Skyworth")
                                        .activo(true)
                                        .build());
                        log.info("DataInitializer: Usuario admin@skyworth.com creado con rol ADMIN_TOTAL.");
                }
                if (!usuarioRepository.existsByEmail("jose@skyworth.com")) {
                        usuarioRepository.save(Usuario.builder()
                                        .email("jose@skyworth.com")
                                        .password(passwordEncoder.encode("admin123."))
                                        .rol(Usuario.Rol.ADMIN)
                                        .nombreCompleto("José Skyworth")
                                        .activo(true)
                                        .build());
                        log.info("DataInitializer: Usuario jose@skyworth.com creado.");
                }
        }

        private void seedConfiguracionGlobal() {
                if (configuracionGlobalRepository.findById("auto_approve_enabled").isEmpty()) {
                        configuracionGlobalRepository.save(ConfiguracionGlobal.builder()
                                        .clave("auto_approve_enabled")
                                        .valor("false")
                                        .descripcion("Aprobación automática de ventas sin revisión manual")
                                        .build());
                }
                if (configuracionGlobalRepository.findById("venta_fecha_max_semanas").isEmpty()) {
                        configuracionGlobalRepository.save(ConfiguracionGlobal.builder()
                                        .clave("venta_fecha_max_semanas")
                                        .valor("2")
                                        .descripcion("Máximo de semanas hacia atrás permitidas para la fecha de venta al registrar")
                                        .build());
                }
        }

        private void seedTipoProductos() {
                if (tipoProductoRepository.count() > 0)
                        return;

                List<String> defaults = List.of("Televisores", "Audio", "Línea Blanca", "Accesorios");
                for (String nombre : defaults) {
                        tipoProductoRepository.save(TipoProducto.builder().nombre(nombre).activo(true).build());
                }
                log.info("DataInitializer: {} tipos de producto sembrados.", defaults.size());
        }

        private void syncPostgresSequences() {
                try {
                        List<String> tables = List.of(
                                        "usuarios", "ciudades", "tiendas", "vendedores", "ventas",
                                        "campanas", "producto", "tipo_producto", "pagos", "auditoria",
                                        "catalogo_falla", "equipo_danado", "campanas_cliente",
                                        "registros_cliente", "entregas_ruleta", "tickets_cliente",
                                        "sorteos_periodos", "premios_ruleta", "premios_vendedor",
                                        "canjes_puntos", "movimientos_puntos", "lotes_liquidacion",
                                        "lotes_liquidacion_detalle", "incidencias_regularizacion",
                                        "vendedor_tienda", "venta_campana", "seriales_restringidos", "serial"
                        );
                        for (String table : tables) {
                                try {
                                        jdbcTemplate.execute(
                                                        "SELECT setval(pg_get_serial_sequence('" + table + "', 'id'), COALESCE((SELECT MAX(id) FROM " + table + "), 1))"
                                        );
                                } catch (Exception ignored) {
                                        // Omite tablas que no tengan secuencia explícita sobre id o columnas UUID
                                }
                        }
                        log.info("DataInitializer: Secuencias de base de datos sincronizadas automáticamente.");
                } catch (Exception e) {
                        log.debug("Sincronización de secuencias omitida: {}", e.getMessage());
                }
        }

        private void autoRepararTicketsRegistros() {
                try {
                        String sql =
                                "INSERT INTO tickets_cliente (codigo_ticket, registro_cliente_id, campana_cliente_id, cliente_ci, cliente_nombre, cliente_celular, serial, producto_modelo, estado, excluido_quincenal, created_at) " +
                                "SELECT " +
                                "  'SKY-' || EXTRACT(YEAR FROM COALESCE(r.fecha_compra, CURRENT_DATE)) || '-' || LPAD(r.id::text, 6, '0') || '-1', " +
                                "  r.id, " +
                                "  r.campana_cliente_id, " +
                                "  COALESCE(TRIM(r.ci), ''), " +
                                "  TRIM(COALESCE(r.nombre_cliente, '') || ' ' || COALESCE(r.apellido_cliente, '')), " +
                                "  r.celular, " +
                                "  r.serial, " +
                                "  COALESCE(r.producto_modelo, 'Televisor Skyworth'), " +
                                "  CASE WHEN r.estado = 'APROBADO' THEN 'HABILITADO' WHEN r.estado = 'RECHAZADO' THEN 'ANULADO' ELSE 'NO_HABILITADO' END, " +
                                "  false, " +
                                "  NOW() " +
                                "FROM registros_cliente r " +
                                "LEFT JOIN serial s ON UPPER(TRIM(s.numero_serie)) = UPPER(TRIM(r.serial)) " +
                                "JOIN campana_cliente_productos ccp ON ccp.campana_cliente_id = r.campana_cliente_id " +
                                "  AND ccp.producto_id = COALESCE(r.producto_id, s.producto_id) " +
                                "  AND ccp.activo = true " +
                                "  AND ccp.tickets > 0 " +
                                "WHERE NOT EXISTS (SELECT 1 FROM tickets_cliente t WHERE t.registro_cliente_id = r.id) " +
                                "ON CONFLICT (codigo_ticket) DO NOTHING;";
                        int rows = jdbcTemplate.update(sql);
                        if (rows > 0) {
                                log.info("DataInitializer: Se auto-generaron {} tickets para registros que no contaban con tickets.", rows);
                        }
                } catch (Exception e) {
                        log.debug("Auto-reparación de tickets omitida: {}", e.getMessage());
                }
        }
}
