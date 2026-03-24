package com.ninabit.bono.modules.restrictedserial;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SerialRestringidoRepository extends JpaRepository<SerialRestringido, Long> {
    List<SerialRestringido> findBySerialContainingIgnoreCaseOrderByImportadoEnDesc(String serial);

    List<SerialRestringido> findAllByOrderByImportadoEnDesc();

    Optional<SerialRestringido> findBySerial(String serial);

    boolean existsBySerial(String serial);
}
