package com.ninabit.bono.modules.clientcampaign;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventarioImpulsadorRepository extends JpaRepository<InventarioImpulsador, Long> {

    @Query("SELECT i FROM InventarioImpulsador i WHERE i.campanaCliente.id = :campanaClienteId AND (i.vendedorId = :operadorId OR i.usuarioId = :operadorId)")
    List<InventarioImpulsador> findByCampanaAndOperador(Long campanaClienteId, Long operadorId);

    default List<InventarioImpulsador> findByCampanaClienteIdAndVendedorId(Long campanaClienteId, Long vendedorId) {
        return findByCampanaAndOperador(campanaClienteId, vendedorId);
    }

    List<InventarioImpulsador> findByCampanaClienteIdAndUsuarioId(Long campanaClienteId, Long usuarioId);

    @Query("SELECT i FROM InventarioImpulsador i WHERE i.campanaCliente.id = :campanaClienteId AND (i.vendedorId = :operadorId OR i.usuarioId = :operadorId) AND i.saldo > 0")
    List<InventarioImpulsador> findAvailableByCampanaAndVendedor(Long campanaClienteId, Long operadorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventarioImpulsador i WHERE i.id = :id")
    Optional<InventarioImpulsador> findByIdForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventarioImpulsador i WHERE i.campanaCliente.id = :campanaClienteId AND (i.vendedorId = :operadorId OR i.usuarioId = :operadorId) AND i.premioRuleta.id = :premioId AND i.saldo > 0")
    List<InventarioImpulsador> findForUpdate(Long campanaClienteId, Long operadorId, Long premioId);

    List<InventarioImpulsador> findByCampanaClienteIdAndVendedorIdAndPremioRuletaId(Long campanaClienteId, Long operadorId, Long premioRuletaId);

    List<InventarioImpulsador> findByCampanaClienteIdAndUsuarioIdAndPremioRuletaId(Long campanaClienteId, Long usuarioId, Long premioRuletaId);

    List<InventarioImpulsador> findByCampanaClienteIdAndTiendaIdAndPremioRuletaId(Long campanaClienteId, Long tiendaId, Long premioRuletaId);

    List<InventarioImpulsador> findByCampanaClienteIdOrderByIdDesc(Long campanaClienteId);

    List<InventarioImpulsador> findByCampanaClienteId(Long campanaClienteId);
}
