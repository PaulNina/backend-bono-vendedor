package com.ninabit.bono.initializer;

import com.ninabit.bono.modules.city.Ciudad;
import com.ninabit.bono.modules.city.CiudadRepository;
import com.ninabit.bono.modules.product.type.TipoProducto;
import com.ninabit.bono.modules.product.type.TipoProductoRepository;
import com.ninabit.bono.modules.configuracion.ConfiguracionGlobal;
import com.ninabit.bono.modules.configuracion.ConfiguracionGlobalRepository;
import com.ninabit.bono.modules.configuracion.RolPermiso;
import com.ninabit.bono.modules.configuracion.RolPermisoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

        private final CiudadRepository ciudadRepository;
        private final TipoProductoRepository tipoProductoRepository;
        private final RolPermisoRepository rolPermisoRepository;
        private final ConfiguracionGlobalRepository configuracionGlobalRepository;
        private final JdbcTemplate jdbcTemplate;

        @Override
        public void run(String... args) {
                dropLegacyColumns();
                seedRolesPermisos();
                seedConfiguracionGlobal();
                seedTipoProductos();
                seedCities();
                log.info("DataInitializer: Inicialización completada.");
        }

        private void seedRolesPermisos() {
                if (rolPermisoRepository.count() > 0)
                        return;

                rolPermisoRepository.saveAll(List.of(
                                RolPermiso.builder()
                                                .rol("REVIEWER")
                                                .modulosPermitidos("/admin/revisiones")
                                                .updatedAt(LocalDateTime.now())
                                                .build(),
                                RolPermiso.builder()
                                                .rol("SUPERVISOR")
                                                .modulosPermitidos("/admin/revisiones,/admin/auditoria,/admin/metricas")
                                                .updatedAt(LocalDateTime.now())
                                                .build()));
                log.info("DataInitializer: RolesPermissions sembrados.");
        }

        private void seedConfiguracionGlobal() {
                // Only insert if not already set — respect admin changes on restart
                configuracionGlobalRepository.findById("auto_aprobar_vendedores").ifPresentOrElse(
                                cg -> {
                                }, // already exists, leave as-is
                                () -> configuracionGlobalRepository.save(ConfiguracionGlobal.builder()
                                                .clave("auto_aprobar_vendedores")
                                                .valor("false")
                                                .descripcion("Si es true, los vendedores se aprueban automáticamente al registrarse")
                                                .build()));

                configuracionGlobalRepository.findById("venta_fecha_max_semanas").ifPresentOrElse(
                                cg -> {
                                },
                                () -> configuracionGlobalRepository.save(ConfiguracionGlobal.builder()
                                                .clave("venta_fecha_max_semanas")
                                                .valor("0")
                                                .descripcion("Máximo de semanas atrás permitidas para registrar ventas (0 = solo semana actual)")
                                                .build()));
                log.info("DataInitializer: Configuraciones globales actualizadas exitosamente.");
        }

        private void seedTipoProductos() {
                if (tipoProductoRepository.count() > 0)
                        return;
                tipoProductoRepository.saveAll(List.of(
                                TipoProducto.builder().nombre("Televisores").build(),
                                TipoProducto.builder().nombre("Lavadoras").build(),
                                TipoProducto.builder().nombre("Heladeras").build(),
                                TipoProducto.builder().nombre("Otros").build()));
                log.info("DataInitializer: Tipos de producto sembrados.");
        }

        private void dropLegacyColumns() {
                try {
                        log.info("Eliminando columnas obsoletas 'ciudad' de tiendas y vendedores...");
                        jdbcTemplate.execute("ALTER TABLE tiendas DROP COLUMN IF EXISTS ciudad");
                        jdbcTemplate.execute("ALTER TABLE vendedores DROP COLUMN IF EXISTS ciudad");
                        log.info("Columnas eliminadas exitosamente.");
                } catch (Exception e) {
                        log.warn("No se pudo eliminar las columnas (quizás ya no existen): " + e.getMessage());
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
}
