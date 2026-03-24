package com.ninabit.bono.modules.serial;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface SerialRepository extends JpaRepository<Serial, Long>, JpaSpecificationExecutor<Serial> {

    // Búsqueda por número de serie (campo nuevo)
    Optional<Serial> findByNumeroSerie(String numeroSerie);

    // Compatibilidad con código existente que busca por campo "serial" (alias)
    default Optional<Serial> findBySerial(String serial) {
        return findByNumeroSerie(serial);
    }

    List<Serial> findByEstado(Serial.Estado estado);

    @Query("SELECT COUNT(s) FROM Serial s WHERE s.estado = 'DISPONIBLE'")
    long countDisponibles();

    @Query("SELECT COUNT(s) FROM Serial s WHERE s.estado = 'USADO'")
    long countUsados();

    @Query("SELECT COUNT(s) FROM Serial s WHERE s.bloqueado = true")
    long countBloqueados();

    boolean existsByNumeroSerie(String numeroSerie);

    // Compatibilidad con código existente
    default boolean existsBySerial(String serial) {
        return existsByNumeroSerie(serial);
    }
}
