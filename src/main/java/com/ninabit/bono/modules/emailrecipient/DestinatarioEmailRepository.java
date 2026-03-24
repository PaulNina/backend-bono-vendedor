package com.ninabit.bono.modules.emailrecipient;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DestinatarioEmailRepository extends JpaRepository<DestinatarioEmail, Long> {
    List<DestinatarioEmail> findByCiudad(String ciudad);

    List<DestinatarioEmail> findByCampanaId(Long campanaId);

    List<DestinatarioEmail> findByGrupoId(Long grupoId);
}
