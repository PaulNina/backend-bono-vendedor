package com.ninabit.bono.modules.ranking;

import com.ninabit.bono.modules.sale.Venta;
import com.ninabit.bono.modules.sale.VentaRepository;
import com.ninabit.bono.modules.vendor.Vendedor;
import com.ninabit.bono.modules.vendor.VendedorRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/ranking")
@RequiredArgsConstructor
@Tag(name = "Ranking")
public class RankingController {

    private final VentaRepository ventaRepository;
    private final VendedorRepository vendedorRepository;

    @GetMapping
    @Operation(summary = "Ranking de vendedores por puntos")
    public List<RankEntry> getRanking(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {

        List<Vendedor> vendedores = (city != null && !city.isBlank())
                ? vendedorRepository.findByTiendaCiudadNombre(city)
                : vendedorRepository.findByActivoTrue();

        List<RankEntry> ranking = new ArrayList<>();

        for (Vendedor v : vendedores) {
            List<Venta> ventas = ventaRepository.findByVendedorId(v.getId())
                    .stream()
                    .filter(vt -> vt.getEstado() == Venta.Estado.APROBADA)
                    .filter(vt -> startDate == null || !vt.getSaleDate().isBefore(startDate))
                    .filter(vt -> endDate == null || !vt.getSaleDate().isAfter(endDate))
                    .toList();

            int points = ventas.stream().mapToInt(Venta::getPuntos).sum();
            int units = ventas.size();
            int bonoBs = ventas.stream().mapToInt(Venta::getBonoBs).sum();

            if (units > 0 || points > 0) {
                String tiendaNombre = v.getTienda() != null ? v.getTienda().getNombre() : null;
                String cityName = (v.getTienda() != null && v.getTienda().getCiudad() != null)
                        ? v.getTienda().getCiudad().getNombre()
                        : null;
                ranking.add(new RankEntry(0, v.getId(), v.getNombreCompleto(),
                        tiendaNombre, cityName, points, units, bonoBs));
            }
        }

        ranking.sort(Comparator.comparingInt(RankEntry::getTotalPoints).reversed());
        for (int i = 0; i < ranking.size(); i++) {
            ranking.get(i).setRank(i + 1);
        }

        return ranking;
    }

    @Data
    @AllArgsConstructor
    public static class RankEntry {
        private int rank;
        private Long vendorId;
        private String fullName;
        private String storeName;
        private String city;
        private int totalPoints;
        private int totalUnits;
        private int totalBonusBs;
    }
}
