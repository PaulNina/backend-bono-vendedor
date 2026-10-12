package com.ninabit.bono.modules.vendorprize;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketVendedorRepository extends JpaRepository<TicketVendedor, Long> {
    List<TicketVendedor> findByVendedorIdOrderByCreatedAtDesc(Long vendedorId);
    List<TicketVendedor> findByCampanaIdAndEstado(Long campanaId, TicketVendedor.EstadoTicket estado);
    List<TicketVendedor> findByEstado(TicketVendedor.EstadoTicket estado);
    boolean existsByVentaId(Long ventaId);
    Optional<TicketVendedor> findByVentaId(Long ventaId);
}
