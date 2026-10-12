package com.ninabit.bono.modules.vendor;

import com.ninabit.bono.modules.sale.Venta;
import com.ninabit.bono.modules.sale.VentaRepository;
import com.ninabit.bono.modules.sale.VentaCampana;
import com.ninabit.bono.modules.sale.VentaCampanaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/vendor")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('VENDOR', 'ADMIN', 'SUPERVISOR')")
@Tag(name = "Vendor - Mi Panel")
public class VendorStatsController {

    private final VendedorRepository vendedorRepository;
    private final VentaRepository ventaRepository;
    private final VentaCampanaRepository ventaCampanaRepository;

    @GetMapping("/stats")
    @Operation(summary = "Estadísticas del vendedor logueado con filtro por campaña opcional")
    public Map<String, Object> getStats(
            Authentication auth,
            @RequestParam(required = false) Long campanaId) {
        Vendedor vendedor = vendedorRepository.findByEmail(auth.getName()).orElse(null);
        if (vendedor == null) {
            return Map.of(
                    "approved", 0,
                    "pending", 0,
                    "rejected", 0,
                    "bonusBs", 0,
                    "points", 0);
        }

        long aprobadas, pendientes, rechazadas;
        int bonoBs, puntos;

        if (campanaId != null && campanaId != -1) {
            List<VentaCampana> details = ventaCampanaRepository.findByVendedorIdAndCampanaId(vendedor.getId(), campanaId);
            aprobadas = details.stream().filter(vc -> vc.getVenta() != null && vc.getVenta().getEstado() == Venta.Estado.APROBADA).count();
            pendientes = details.stream().filter(vc -> vc.getVenta() != null && vc.getVenta().getEstado() == Venta.Estado.PENDIENTE).count();
            rechazadas = details.stream().filter(vc -> vc.getVenta() != null && vc.getVenta().getEstado() == Venta.Estado.RECHAZADA).count();
            bonoBs = details.stream().filter(vc -> vc.getVenta() != null && vc.getVenta().getEstado() == Venta.Estado.APROBADA).mapToInt(VentaCampana::getBonoGanado).sum();
            puntos = details.stream().filter(vc -> vc.getVenta() != null && vc.getVenta().getEstado() == Venta.Estado.APROBADA).mapToInt(VentaCampana::getPuntosGanados).sum();
        } else {
            List<Venta> ventas = ventaRepository.findByVendedorId(vendedor.getId());
            aprobadas = ventas.stream().filter(v -> v.getEstado() == Venta.Estado.APROBADA).count();
            pendientes = ventas.stream().filter(v -> v.getEstado() == Venta.Estado.PENDIENTE).count();
            rechazadas = ventas.stream().filter(v -> v.getEstado() == Venta.Estado.RECHAZADA).count();
            bonoBs = ventas.stream().filter(v -> v.getEstado() == Venta.Estado.APROBADA).mapToInt(Venta::getBonoBs).sum();
            puntos = ventas.stream().filter(v -> v.getEstado() == Venta.Estado.APROBADA).mapToInt(Venta::getPuntos).sum();
        }

        return Map.of(
                "approved", aprobadas,
                "pending", pendientes,
                "rejected", rechazadas,
                "bonusBs", bonoBs,
                "points", puntos,
                "vendedor", vendedor);
    }

    @GetMapping("/sales")
    @Operation(summary = "Ventas del vendedor logueado con filtro por campaña opcional")
    public List<Venta> getMySales(
            Authentication auth,
            @RequestParam(required = false) Long campanaId) {
        Vendedor vendedor = vendedorRepository.findByEmail(auth.getName()).orElse(null);
        if (vendedor == null) {
            return List.of();
        }
        if (campanaId != null && campanaId != -1) {
            return ventaRepository.findByVendedorIdAndCampanaId(vendedor.getId(), campanaId);
        } else {
            return ventaRepository.findByVendedorId(vendedor.getId());
        }
    }
}
