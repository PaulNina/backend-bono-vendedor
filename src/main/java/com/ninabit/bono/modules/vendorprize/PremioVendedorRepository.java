package com.ninabit.bono.modules.vendorprize;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PremioVendedorRepository extends JpaRepository<PremioVendedor, Long> {
    List<PremioVendedor> findByActivoTrueOrderByIdDesc();
    List<PremioVendedor> findAllByOrderByIdDesc();
    List<PremioVendedor> findByCampanaIdAndActivoTrue(Long campanaId);
}
