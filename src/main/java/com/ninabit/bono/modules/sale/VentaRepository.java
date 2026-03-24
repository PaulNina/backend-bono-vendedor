package com.ninabit.bono.modules.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface VentaRepository extends JpaRepository<Venta, Long>, JpaSpecificationExecutor<Venta> {
        List<Venta> findByVendedorId(Long vendedorId);

        List<Venta> findByEstado(Venta.Estado estado);

        List<Venta> findByCiudad(String ciudad);

        List<Venta> findByEstadoAndCiudad(Venta.Estado estado, String ciudad);

        boolean existsBySerial(String serial);

        java.util.Optional<Venta> findBySerial(String serial);

        @Query("SELECT v FROM Venta v ORDER BY v.saleDate DESC")
        List<Venta> findAllOrderByDate();

        @Query("SELECT v.ciudad, COUNT(v) FROM Venta v WHERE v.estado = 'APROBADA' GROUP BY v.ciudad")
        List<Object[]> countApprovedByCity();

        @Query("SELECT v FROM Venta v WHERE v.estado = :estado AND v.saleDate >= :startDate AND v.saleDate <= :endDate")
        List<Venta> findByEstadoAndSaleDateBetween(Venta.Estado estado, java.time.LocalDate startDate,
                        java.time.LocalDate endDate);

        @Query("SELECT v FROM Venta v WHERE v.estado = :estado AND v.saleDate >= :startDate AND v.saleDate <= :endDate AND v.ciudad IN :ciudades")
        List<Venta> findByEstadoAndSaleDateBetweenAndCiudadIn(Venta.Estado estado, java.time.LocalDate startDate,
                        java.time.LocalDate endDate, List<String> ciudades);
}
