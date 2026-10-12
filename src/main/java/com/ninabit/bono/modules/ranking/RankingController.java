package com.ninabit.bono.modules.ranking;

import com.ninabit.bono.modules.sale.VentaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Ranking endpoint — uses a single SQL GROUP BY aggregate query.
 * Sentinel values used instead of null to avoid PostgreSQL type inference errors:
 *   campanaId = -1L  → all campaigns
 *   ciudad    = ""   → all cities
 *   startDate / endDate = real LocalDate values (never null)
 */
@RestController
@RequestMapping("/ranking")
@RequiredArgsConstructor
@Tag(name = "Ranking")
public class RankingController {

    private static final LocalDate DATE_MIN = LocalDate.of(2000, 1, 1);
    private static final LocalDate DATE_MAX = LocalDate.of(2099, 12, 31);

    private final VentaRepository ventaRepository;

    @GetMapping
    @Operation(summary = "Ranking de vendedores por puntos (paginado)")
    public Page<RankEntry> getRanking(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Long campanaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        size = Math.min(size, 200);

        // Sentinel values — never pass null to the query
        Long   campanaParam = (campanaId != null) ? campanaId : -1L;
        String ciudadParam  = (city == null || city.isBlank() || city.equalsIgnoreCase("all")) ? "" : city.trim();
        LocalDate startParam = (startDate != null) ? startDate : DATE_MIN;
        LocalDate endParam   = (endDate   != null) ? endDate   : DATE_MAX;

        // Single aggregate query — no entity loading, no N+1
        List<Object[]> rows = (campanaParam == -1)
            ? ventaRepository.rankingAllCampanas(ciudadParam, startParam, endParam)
            : ventaRepository.rankingByCampana(campanaParam, ciudadParam, startParam, endParam);

        // Map raw Object[] → DTOs, assign rank
        List<RankEntry> all = new ArrayList<>(rows.size());
        int rank = 1;
        for (Object[] row : rows) {
            Long   vendorId  = ((Number) row[0]).longValue();
            String fullName  = (String)  row[1];
            String storeName = (String)  row[2];
            String cityName  = (String)  row[3];
            int    points    = ((Number) row[4]).intValue();
            int    units     = ((Number) row[5]).intValue();
            int    bonoBs    = ((Number) row[6]).intValue();
            all.add(new RankEntry(rank++, vendorId, fullName, storeName, cityName, points, units, bonoBs));
        }

        // Manual pagination (DB already sorted by puntos DESC)
        int total     = all.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex   = Math.min(fromIndex + size, total);

        return new PageImpl<>(all.subList(fromIndex, toIndex), PageRequest.of(page, size, Sort.unsorted()), total);
    }

    @Data
    @AllArgsConstructor
    public static class RankEntry {
        private int    rank;
        private Long   vendorId;
        private String fullName;
        private String storeName;
        private String city;
        private int    totalPoints;
        private int    totalUnits;
        private int    totalBonusBs;
    }
}
