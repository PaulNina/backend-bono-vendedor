package com.ninabit.bono.modules.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VentaCampanaRepository extends JpaRepository<VentaCampana, Long> {
        List<VentaCampana> findByVentaId(Long ventaId);

        List<VentaCampana> findByPagoIsNullAndVenta_Estado(com.ninabit.bono.modules.sale.Venta.Estado estado);

        List<VentaCampana> findByPagoIsNullAndVenta_EstadoAndVenta_VendedorId(
                        com.ninabit.bono.modules.sale.Venta.Estado estado, Long vendedorId);

        @org.springframework.data.jpa.repository.Query("SELECT new com.ninabit.bono.modules.payment.CommissionReportDTO("
                        +
                        "v.id, v.nombreCompleto, COALESCE(c.nombre, ''), COALESCE(t.nombre, ''), v.fotoQr, v.tallaPolera, "
                        +
                        "COUNT(vc.id), SUM(vc.bonoGanado), " +
                        "CASE WHEN vc.pago IS NULL THEN 'Pendiente' ELSE 'Pagado' END) " +
                        "FROM VentaCampana vc " +
                        "JOIN vc.venta venta " +
                        "JOIN com.ninabit.bono.modules.vendor.Vendedor v ON v.id = venta.vendedorId " +
                        "LEFT JOIN v.tienda t " +
                        "LEFT JOIN t.ciudad c " +
                        "WHERE venta.estado = 'APROBADA' " +
                        "AND (:campanaId IS NULL OR vc.campana.id = :campanaId) " +
                        "AND (:ciudad IS NULL OR :ciudad = '' OR c.nombre = :ciudad) " +
                        "AND (cast(:startDate as date) IS NULL OR venta.saleDate >= :startDate) " +
                        "AND (cast(:endDate as date) IS NULL OR venta.saleDate <= :endDate) " +
                        "AND (:estadoPago IS NULL OR :estadoPago = 'Todos' " +
                        "     OR (:estadoPago = 'Pendiente' AND vc.pago IS NULL) " +
                        "     OR (:estadoPago = 'Pagado' AND vc.pago IS NOT NULL)) " +
                        "GROUP BY v.id, v.nombreCompleto, c.nombre, t.nombre, v.fotoQr, v.tallaPolera, "
                        +
                        "CASE WHEN vc.pago IS NULL THEN 'Pendiente' ELSE 'Pagado' END " +
                        "ORDER BY v.nombreCompleto ASC")
        List<com.ninabit.bono.modules.payment.CommissionReportDTO> getCommissionReport(
                        @org.springframework.data.repository.query.Param("campanaId") Long campanaId,
                        @org.springframework.data.repository.query.Param("ciudad") String ciudad,
                        @org.springframework.data.repository.query.Param("startDate") java.time.LocalDate startDate,
                        @org.springframework.data.repository.query.Param("endDate") java.time.LocalDate endDate,
                        @org.springframework.data.repository.query.Param("estadoPago") String estadoPago);

        @org.springframework.data.jpa.repository.Query("SELECT vc FROM VentaCampana vc " +
                        "JOIN vc.venta venta " +
                        "JOIN com.ninabit.bono.modules.vendor.Vendedor v ON v.id = venta.vendedorId " +
                        "LEFT JOIN v.tienda t " +
                        "LEFT JOIN t.ciudad c " +
                        "WHERE venta.estado = 'APROBADA' AND vc.pago IS NULL " +
                        "AND v.id = :vendedorId " +
                        "AND (:campanaId IS NULL OR vc.campana.id = :campanaId) " +
                        "AND (:ciudad IS NULL OR :ciudad = '' OR c.nombre = :ciudad) " +
                        "AND (cast(:startDate as date) IS NULL OR venta.saleDate >= :startDate) " +
                        "AND (cast(:endDate as date) IS NULL OR venta.saleDate <= :endDate)")
        List<VentaCampana> findPendingToPay(
                        @org.springframework.data.repository.query.Param("vendedorId") Long vendedorId,
                        @org.springframework.data.repository.query.Param("campanaId") Long campanaId,
                        @org.springframework.data.repository.query.Param("ciudad") String ciudad,
                        @org.springframework.data.repository.query.Param("startDate") java.time.LocalDate startDate,
                        @org.springframework.data.repository.query.Param("endDate") java.time.LocalDate endDate);
}
