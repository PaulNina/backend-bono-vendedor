package com.ninabit.bono.modules.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PagoRepository extends JpaRepository<Pago, Long> {
    List<Pago> findByVendedorIdOrderByFechaDesc(Long vendedorId);
}
