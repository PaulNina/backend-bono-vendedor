package com.ninabit.bono.modules.vendorprize;

import com.ninabit.bono.modules.audit.AuditoriaService;
import com.ninabit.bono.modules.campaign.Campana;
import com.ninabit.bono.modules.campaign.CampanaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/premios-vendedores")
@RequiredArgsConstructor
@Tag(name = "Premios Vendedor")
@Slf4j
public class PremioVendedorController {

    private final PremioVendedorRepository premioRepository;
    private final CampanaRepository campanaRepository;
    private final AuditoriaService auditoriaService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @GetMapping
    @Operation(summary = "Listar premios activos disponibles para canje de vendedores")
    public List<PremioVendedor> getActivePrizes(@RequestParam(required = false) Long campanaId) {
        if (campanaId != null && campanaId > 0) {
            return premioRepository.findByCampanaIdAndActivoTrue(campanaId);
        }
        return premioRepository.findByActivoTrueOrderByIdDesc();
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    @Operation(summary = "Listar todos los premios (activos e inactivos) para administración")
    public List<PremioVendedor> getAllAdmin() {
        return premioRepository.findAllByOrderByIdDesc();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener detalle de un premio")
    public ResponseEntity<PremioVendedor> getById(@PathVariable Long id) {
        return premioRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear nuevo premio en el catálogo")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> req, Authentication auth) {
        try {
            String nombre = (String) req.get("nombre");
            if (nombre == null || nombre.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El nombre del premio es obligatorio"));
            }

            Integer puntos = req.get("puntosRequeridos") != null ? ((Number) req.get("puntosRequeridos")).intValue() : 0;
            Integer stock = req.get("stock") != null ? ((Number) req.get("stock")).intValue() : 0;
            String descripcion = (String) req.get("descripcion");
            String foto = (String) req.get("foto");
            Boolean activo = req.get("activo") != null ? (Boolean) req.get("activo") : true;

            Campana campana = null;
            if (req.get("campanaId") != null) {
                Long campanaId = ((Number) req.get("campanaId")).longValue();
                campana = campanaRepository.findById(campanaId).orElse(null);
            }

            PremioVendedor premio = PremioVendedor.builder()
                    .nombre(nombre.trim())
                    .descripcion(descripcion)
                    .puntosRequeridos(puntos)
                    .stock(stock)
                    .foto(foto)
                    .activo(activo)
                    .campana(campana)
                    .build();

            PremioVendedor saved = premioRepository.save(premio);

            String user = auth != null ? auth.getName() : "ADMIN";
            auditoriaService.log(user, "PREMIO_CREADO",
                    "Premio creado: " + saved.getNombre() + " (" + saved.getPuntosRequeridos() + " pts, stock=" + saved.getStock() + ")",
                    "PremioVendedor", saved.getId().toString());

            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("Error al crear premio", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar premio")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> req, Authentication auth) {
        return premioRepository.findById(id).map(premio -> {
            if (req.containsKey("nombre")) premio.setNombre((String) req.get("nombre"));
            if (req.containsKey("descripcion")) premio.setDescripcion((String) req.get("descripcion"));
            if (req.containsKey("puntosRequeridos")) premio.setPuntosRequeridos(((Number) req.get("puntosRequeridos")).intValue());
            if (req.containsKey("stock")) premio.setStock(((Number) req.get("stock")).intValue());
            if (req.containsKey("foto")) premio.setFoto((String) req.get("foto"));
            if (req.containsKey("activo")) premio.setActivo((Boolean) req.get("activo"));

            if (req.containsKey("campanaId")) {
                if (req.get("campanaId") == null) {
                    premio.setCampana(null);
                } else {
                    Long cId = ((Number) req.get("campanaId")).longValue();
                    premio.setCampana(campanaRepository.findById(cId).orElse(null));
                }
            }

            PremioVendedor saved = premioRepository.save(premio);
            String user = auth != null ? auth.getName() : "ADMIN";
            auditoriaService.log(user, "PREMIO_ACTUALIZADO",
                    "Premio actualizado: " + saved.getNombre() + " (ID: " + id + ")",
                    "PremioVendedor", id.toString());

            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar o eliminar premio")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        return premioRepository.findById(id).map(premio -> {
            premio.setActivo(false);
            premioRepository.save(premio);

            String user = auth != null ? auth.getName() : "ADMIN";
            auditoriaService.log(user, "PREMIO_DESACTIVADO",
                    "Premio desactivado: " + premio.getNombre() + " (ID: " + id + ")",
                    "PremioVendedor", id.toString());

            return ResponseEntity.ok(Map.of("message", "Premio desactivado correctamente"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/upload-foto")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Subir fotografía para catálogo de premios")
    public ResponseEntity<?> uploadFoto(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo está vacío"));
        }
        try {
            File dir = new File(uploadDir + "/premios");
            if (!dir.exists()) dir.mkdirs();

            String ext = "";
            String orig = file.getOriginalFilename();
            if (orig != null && orig.contains(".")) {
                ext = orig.substring(orig.lastIndexOf("."));
            }
            String filename = "premio_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            Path path = Paths.get(uploadDir, "premios", filename);
            Files.write(path, file.getBytes());

            String relativeUrl = "uploads/premios/" + filename;
            return ResponseEntity.ok(Map.of("url", relativeUrl));
        } catch (IOException e) {
            log.error("Error subiendo foto de premio", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al guardar el archivo"));
        }
    }
}
