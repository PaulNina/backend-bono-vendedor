package com.ninabit.bono.modules.citygroup;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GrupoCiudadRepository extends JpaRepository<GrupoCiudad, Long> {
    List<GrupoCiudad> findAllByOrderByOrdenAsc();
}
