package com.ninabit.bono.modules.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
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
	List<Venta> findByEstadoAndSaleDateBetween(Venta.Estado estado, LocalDate startDate, LocalDate endDate);

	@Query("SELECT DISTINCT v FROM Venta v JOIN v.detallesCampanas vc WHERE v.vendedorId = :vendedorId AND vc.campana.id = :campanaId ORDER BY v.saleDate DESC")
	List<Venta> findByVendedorIdAndCampanaId(
		@Param("vendedorId") Long vendedorId,
		@Param("campanaId") Long campanaId
	);

	@Query("SELECT v FROM Venta v WHERE v.estado = :estado AND v.saleDate >= :startDate AND v.saleDate <= :endDate AND v.ciudad IN :ciudades")
	List<Venta> findByEstadoAndSaleDateBetweenAndCiudadIn(Venta.Estado estado, LocalDate startDate,
			LocalDate endDate, List<String> ciudades);

	// ── DASHBOARD AGGREGATES : ALL CAMPAIGNS ─────────────────────────────────────
	@Query("""
		SELECT v.estado, COUNT(v) FROM Venta v
		WHERE (:ciudad = '' OR v.ciudad = :ciudad)
		  AND v.createdAt >= :startDate AND v.createdAt <= :endDate
		GROUP BY v.estado
		""")
	List<Object[]> countByEstadoAll(
		@Param("ciudad") String ciudad,
		@Param("startDate") java.time.LocalDateTime startDate,
		@Param("endDate") java.time.LocalDateTime endDate
	);

	@Query("""
		SELECT SUM(v.bonoBs), SUM(v.puntos) FROM Venta v
		WHERE v.estado = 'APROBADA'
		  AND (:ciudad = '' OR v.ciudad = :ciudad)
		  AND v.createdAt >= :startDate AND v.createdAt <= :endDate
		""")
	List<Object[]> sumBonusAll(
		@Param("ciudad") String ciudad,
		@Param("startDate") java.time.LocalDateTime startDate,
		@Param("endDate") java.time.LocalDateTime endDate
	);

	@Query("""
		SELECT v.ciudad, v.estado, COUNT(v), SUM(v.bonoBs) FROM Venta v
		WHERE (:ciudad = '' OR v.ciudad = :ciudad)
		  AND v.createdAt >= :startDate AND v.createdAt <= :endDate
		GROUP BY v.ciudad, v.estado ORDER BY v.ciudad
		""")
	List<Object[]> groupByCiudadEstadoAll(
		@Param("ciudad") String ciudad,
		@Param("startDate") java.time.LocalDateTime startDate,
		@Param("endDate") java.time.LocalDateTime endDate
	);

	@Query("""
		SELECT v.productModel, COUNT(v) FROM Venta v
		WHERE v.productModel IS NOT NULL
		  AND (:ciudad = '' OR v.ciudad = :ciudad)
		  AND v.createdAt >= :startDate AND v.createdAt <= :endDate
		GROUP BY v.productModel ORDER BY COUNT(v) DESC
		""")
	List<Object[]> topProductosAll(
		@Param("ciudad") String ciudad,
		@Param("startDate") java.time.LocalDateTime startDate,
		@Param("endDate") java.time.LocalDateTime endDate
	);

	@Query("""
		SELECT v.productType, v.estado, COUNT(v), SUM(v.bonoBs) FROM Venta v
		WHERE (:ciudad = '' OR v.ciudad = :ciudad)
		  AND v.createdAt >= :startDate AND v.createdAt <= :endDate
		GROUP BY v.productType, v.estado
		""")
	List<Object[]> groupByTipoEstadoAll(
		@Param("ciudad") String ciudad,
		@Param("startDate") java.time.LocalDateTime startDate,
		@Param("endDate") java.time.LocalDateTime endDate
	);

	@Query("""
		SELECT CAST(v.createdAt AS date), v.estado, COUNT(v) FROM Venta v
		WHERE (:ciudad = '' OR v.ciudad = :ciudad)
		  AND v.createdAt >= :startDate AND v.createdAt <= :endDate
		GROUP BY CAST(v.createdAt AS date), v.estado ORDER BY CAST(v.createdAt AS date)
		""")
	List<Object[]> groupByDayEstadoAll(
		@Param("ciudad") String ciudad,
		@Param("startDate") java.time.LocalDateTime startDate,
		@Param("endDate") java.time.LocalDateTime endDate
	);

	// ── DASHBOARD AGGREGATES : SPECIFIC CAMPAIGN ──────────────────────────────────
	@Query("""
		SELECT v.estado, COUNT(DISTINCT v) FROM VentaCampana vc JOIN vc.venta v
		WHERE vc.campana.id = :campanaId
		  AND (:ciudad = '' OR v.ciudad = :ciudad)
		  AND v.createdAt >= :startDate AND v.createdAt <= :endDate
		GROUP BY v.estado
		""")
	List<Object[]> countByEstadoByCampana(
		@Param("campanaId") Long campanaId,
		@Param("ciudad") String ciudad,
		@Param("startDate") java.time.LocalDateTime startDate,
		@Param("endDate") java.time.LocalDateTime endDate
	);

	@Query("""
		SELECT SUM(vc.bonoGanado), SUM(vc.puntosGanados)
		FROM VentaCampana vc JOIN vc.venta v
		WHERE v.estado = 'APROBADA' AND vc.campana.id = :campanaId
		  AND (:ciudad = '' OR v.ciudad = :ciudad)
		  AND v.createdAt >= :startDate AND v.createdAt <= :endDate
		""")
	List<Object[]> sumBonusByCampana(
		@Param("campanaId") Long campanaId,
		@Param("ciudad") String ciudad,
		@Param("startDate") java.time.LocalDateTime startDate,
		@Param("endDate") java.time.LocalDateTime endDate
	);

	@Query("""
		SELECT v.ciudad, v.estado, COUNT(DISTINCT v), SUM(vc.bonoGanado)
		FROM VentaCampana vc JOIN vc.venta v
		WHERE vc.campana.id = :campanaId
		  AND (:ciudad = '' OR v.ciudad = :ciudad)
		  AND v.createdAt >= :startDate AND v.createdAt <= :endDate
		GROUP BY v.ciudad, v.estado ORDER BY v.ciudad
		""")
	List<Object[]> groupByCiudadEstadoByCampana(
		@Param("campanaId") Long campanaId,
		@Param("ciudad") String ciudad,
		@Param("startDate") java.time.LocalDateTime startDate,
		@Param("endDate") java.time.LocalDateTime endDate
	);

	@Query("""
		SELECT v.productModel, COUNT(DISTINCT v) FROM VentaCampana vc JOIN vc.venta v
		WHERE v.productModel IS NOT NULL AND vc.campana.id = :campanaId
		  AND (:ciudad = '' OR v.ciudad = :ciudad)
		  AND v.createdAt >= :startDate AND v.createdAt <= :endDate
		GROUP BY v.productModel ORDER BY COUNT(DISTINCT v) DESC
		""")
	List<Object[]> topProductosByCampana(
		@Param("campanaId") Long campanaId,
		@Param("ciudad") String ciudad,
		@Param("startDate") java.time.LocalDateTime startDate,
		@Param("endDate") java.time.LocalDateTime endDate
	);

	@Query("""
		SELECT v.productType, v.estado, COUNT(DISTINCT v), SUM(vc.bonoGanado)
		FROM VentaCampana vc JOIN vc.venta v
		WHERE vc.campana.id = :campanaId
		  AND (:ciudad = '' OR v.ciudad = :ciudad)
		  AND v.createdAt >= :startDate AND v.createdAt <= :endDate
		GROUP BY v.productType, v.estado
		""")
	List<Object[]> groupByTipoEstadoByCampana(
		@Param("campanaId") Long campanaId,
		@Param("ciudad") String ciudad,
		@Param("startDate") java.time.LocalDateTime startDate,
		@Param("endDate") java.time.LocalDateTime endDate
	);

	@Query("""
		SELECT CAST(v.createdAt AS date), v.estado, COUNT(DISTINCT v) 
		FROM VentaCampana vc JOIN vc.venta v
		WHERE vc.campana.id = :campanaId
		  AND (:ciudad = '' OR v.ciudad = :ciudad)
		  AND v.createdAt >= :startDate AND v.createdAt <= :endDate
		GROUP BY CAST(v.createdAt AS date), v.estado ORDER BY CAST(v.createdAt AS date)
		""")
	List<Object[]> groupByDayEstadoByCampana(
		@Param("campanaId") Long campanaId,
		@Param("ciudad") String ciudad,
		@Param("startDate") java.time.LocalDateTime startDate,
		@Param("endDate") java.time.LocalDateTime endDate
	);

	// ── RANKING AGGREGATE ────────────────────────────────────────────────────────

	@Query("""
		SELECT v.id, v.nombreCompleto, v.tienda, c.nombre,
		       COALESCE(SUM(vt.puntos), 0), COUNT(DISTINCT vt.id), COALESCE(SUM(vt.bonoBs), 0)
		FROM com.ninabit.bono.modules.vendor.Vendedor v
		LEFT JOIN v.ciudad c
		LEFT JOIN Venta vt ON vt.vendedorId = v.id
		    AND vt.estado = 'APROBADA'
		    AND vt.saleDate >= :startDate AND vt.saleDate <= :endDate
		WHERE v.activo = true AND (:ciudad = '' OR c.nombre = :ciudad)
		GROUP BY v.id, v.nombreCompleto, v.tienda, c.nombre
		HAVING COALESCE(SUM(vt.puntos), 0) > 0 OR COUNT(DISTINCT vt.id) > 0
		ORDER BY COALESCE(SUM(vt.puntos), 0) DESC
		""")
	List<Object[]> rankingAllCampanas(
		@Param("ciudad") String ciudad,
		@Param("startDate") LocalDate startDate,
		@Param("endDate") LocalDate endDate
	);

	@Query("""
		SELECT v.id, v.nombreCompleto, v.tienda, c.nombre,
		       COALESCE(SUM(vc.puntosGanados), 0), COUNT(DISTINCT vt.id), COALESCE(SUM(vc.bonoGanado), 0)
		FROM com.ninabit.bono.modules.vendor.Vendedor v
		LEFT JOIN v.ciudad c
		JOIN Venta vt ON vt.vendedorId = v.id
		    AND vt.estado = 'APROBADA'
		    AND vt.saleDate >= :startDate AND vt.saleDate <= :endDate
		JOIN VentaCampana vc ON vc.venta = vt AND vc.campana.id = :campanaId
		WHERE v.activo = true AND (:ciudad = '' OR c.nombre = :ciudad)
		GROUP BY v.id, v.nombreCompleto, v.tienda, c.nombre
		HAVING COALESCE(SUM(vc.puntosGanados), 0) > 0 OR COUNT(DISTINCT vt.id) > 0
		ORDER BY COALESCE(SUM(vc.puntosGanados), 0) DESC
		""")
	List<Object[]> rankingByCampana(
		@Param("campanaId") Long campanaId,
		@Param("ciudad") String ciudad,
		@Param("startDate") LocalDate startDate,
		@Param("endDate") LocalDate endDate
	);

	@Query("""
		SELECT COUNT(DISTINCT v.id), COUNT(DISTINCT vt.id), COALESCE(SUM(vt.bonoBs), 0)
		FROM com.ninabit.bono.modules.vendor.Vendedor v
		LEFT JOIN Venta vt ON vt.vendedorId = v.id AND vt.estado = 'APROBADA'
		WHERE v.tiendaId = :tiendaId
		""")
	List<Object[]> getStatsByTiendaId(@Param("tiendaId") Long tiendaId);

	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.transaction.annotation.Transactional
	@Query("UPDATE Venta v SET v.ciudad = :ciudad WHERE v.vendedorId = :vendedorId AND (v.ciudad = 'Desconocida' OR v.ciudad IS NULL OR v.ciudad = '')")
	int backfillCiudadVendedor(@Param("vendedorId") Long vendedorId, @Param("ciudad") String ciudad);
}
