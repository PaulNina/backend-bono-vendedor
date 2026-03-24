package com.ninabit.bono.modules.vendor;

import com.ninabit.bono.modules.sale.Venta;
import com.ninabit.bono.modules.sale.VentaRepository;
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
@PreAuthorize("hasRole('VENDOR')")
@Tag(name = "Vendor - Mi Panel")
public class VendorStatsController {

    private final VendedorRepository vendedorRepository;
    private final VentaRepository ventaRepository;

    @GetMapping("/stats")
    @Operation(summary = "Estadísticas del vendedor logueado")
    public Map<String, Object> getStats(Authentication auth) {
        Vendedor vendedor = vendedorRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Vendedor no encontrado"));

        List<Venta> ventas = ventaRepository.findByVendedorId(vendedor.getId());
        long aprobadas = ventas.stream().filter(v -> v.getEstado() == Venta.Estado.APROBADA).count();
        long pendientes = ventas.stream().filter(v -> v.getEstado() == Venta.Estado.PENDIENTE).count();
        long rechazadas = ventas.stream().filter(v -> v.getEstado() == Venta.Estado.RECHAZADA).count();
        int bonoBs = ventas.stream().filter(v -> v.getEstado() == Venta.Estado.APROBADA).mapToInt(Venta::getBonoBs)
                .sum();
        int puntos = ventas.stream().filter(v -> v.getEstado() == Venta.Estado.APROBADA).mapToInt(Venta::getPuntos)
                .sum();

        return Map.of(
                "approved", aprobadas,
                "pending", pendientes,
                "rejected", rechazadas,
                "bonusBs", bonoBs,
                "points", puntos,
                "vendedor", vendedor);
    }

    @GetMapping("/sales")
    @Operation(summary = "Ventas del vendedor logueado")
    public List<Venta> getMySales(Authentication auth) {
        Vendedor vendedor = vendedorRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Vendedor no encontrado"));
        return ventaRepository.findByVendedorId(vendedor.getId());
    }
}
