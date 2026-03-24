package com.ninabit.bono.modules.vendor;

import com.ninabit.bono.modules.user.UsuarioRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/vendors")
@RequiredArgsConstructor
@Tag(name = "Vendedores")
public class VendedorController {

    private final VendedorRepository vendedorRepository;
    private final UsuarioRepository usuarioRepository;
    private final com.ninabit.bono.modules.audit.AuditoriaService auditoriaService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @GetMapping
    @Operation(summary = "Listar todos los vendedores")
    public List<Vendedor> getAll(@RequestParam(required = false) String city,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean pending) {
        if (Boolean.TRUE.equals(pending)) {
            return vendedorRepository.findByPendingApprovalTrue();
        }

        boolean hasSearch = search != null && !search.isBlank();
        boolean hasCity = city != null && !city.isBlank();

        if (hasSearch && hasCity) {
            return vendedorRepository.searchByCity(search, city);
        } else if (hasSearch) {
            return vendedorRepository.search(search);
        } else if (hasCity) {
            return vendedorRepository.findByTiendaCiudadNombre(city);
        }

        return vendedorRepository.findAll();
    }

    @GetMapping("/city-stats")
    @Operation(summary = "Estadísticas de vendedores por ciudad")
    public Map<String, Long> getCityStats() {
        List<Vendedor> all = vendedorRepository.findByActivoTrue();
        Map<String, Long> stats = all.stream()
                .filter(v -> v.getTienda() != null && v.getTienda().getCiudad() != null)
                .collect(Collectors.groupingBy(v -> v.getTienda().getCiudad().getNombre(), Collectors.counting()));
        stats.put("Total", (long) all.size());
        return stats;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Vendedor> getById(@PathVariable Long id) {
        return vendedorRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registrar vendedor")
    public Vendedor create(@RequestBody Vendedor vendedor) {
        Vendedor saved = vendedorRepository.save(vendedor);
        auditoriaService.log(getUser(), "VENDEDOR_CREADO", 
            "Vendedor registrado: " + saved.getNombreCompleto(), "Vendedor", saved.getId().toString());
        return saved;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar vendedor")
    public ResponseEntity<Vendedor> update(@PathVariable Long id, @RequestBody Vendedor vendedor) {
        if (!vendedorRepository.existsById(id))
            return ResponseEntity.notFound().build();
        vendedor.setId(id);
        Vendedor saved = vendedorRepository.save(vendedor);
        auditoriaService.log(getUser(), "VENDEDOR_ACTUALIZADO", 
            "Vendedor actualizado: " + saved.getNombreCompleto(), "Vendedor", id.toString());
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activar/Desactivar vendedor")
    public ResponseEntity<Vendedor> toggle(@PathVariable Long id) {
        return vendedorRepository.findById(id).map(v -> {
            v.setActivo(!v.isActivo());
            Vendedor saved = vendedorRepository.save(v);
            usuarioRepository.findByVendedorId(id).ifPresent(u -> {
                u.setActivo(saved.isActivo());
                usuarioRepository.save(u);
            });
            auditoriaService.log(getUser(), "VENDEDOR_TOGGLE_ACTIVO", 
                "Vendedor " + (saved.isActivo() ? "activado" : "desactivado") + ": " + saved.getNombreCompleto(), 
                "Vendedor", id.toString());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @Operation(summary = "Aprobar solicitud de registro de vendedor")
    public ResponseEntity<Vendedor> approve(@PathVariable Long id) {
        return vendedorRepository.findById(id).map(v -> {
            v.setPendingApproval(false);
            v.setActivo(true);
            Vendedor saved = vendedorRepository.save(v);
            usuarioRepository.findByVendedorId(id).ifPresent(u -> {
                u.setActivo(true);
                usuarioRepository.save(u);
            });
            auditoriaService.log(getUser(), "VENDEDOR_REGISTRO_APROBADO", 
                "Registro de vendedor aprobado: " + saved.getNombreCompleto(), "Vendedor", id.toString());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/reject-registration")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    @Operation(summary = "Rechazar solicitud de registro de vendedor")
    public ResponseEntity<Vendedor> rejectRegistration(@PathVariable Long id) {
        return vendedorRepository.findById(id).map(v -> {
            v.setPendingApproval(false);
            v.setActivo(false);
            Vendedor saved = vendedorRepository.save(v);
            usuarioRepository.findByVendedorId(id).ifPresent(u -> {
                u.setActivo(false);
                usuarioRepository.save(u);
            });
            auditoriaService.log(getUser(), "VENDEDOR_REGISTRO_RECHAZADO", 
                "Registro de vendedor rechazado: " + saved.getNombreCompleto(), "Vendedor", id.toString());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/profile")
    @PreAuthorize("hasAnyRole('VENDEDOR', 'ADMIN')")
    @Operation(summary = "Actualizar perfil de vendedor (talla y QR)")
    public ResponseEntity<Vendedor> updateProfile(@PathVariable Long id,
            @RequestParam(required = false) String tallaPolera,
            @RequestParam(required = false) MultipartFile fotoQr) {
        return vendedorRepository.findById(id).map(v -> {
            if (tallaPolera != null && !tallaPolera.isBlank()) {
                v.setTallaPolera(tallaPolera);
            }
            if (fotoQr != null && !fotoQr.isEmpty()) {
                try {
                    String qrPath = saveFile(fotoQr);
                    v.setFotoQr(qrPath);
                } catch (IOException e) {
                    throw new RuntimeException("Error al guardar QR", e);
                }
            }
            Vendedor saved = vendedorRepository.save(v);
            auditoriaService.log(getUser(), "VENDEDOR_PERFIL_ACTUALIZADO", 
                "Perfil de vendedor actualizado: " + saved.getNombreCompleto(), "Vendedor", id.toString());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    private String getUser() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private String saveFile(MultipartFile file) throws IOException {
        File dir = new File(uploadDir);
        if (!dir.exists())
            dir.mkdirs();
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path path = Paths.get(uploadDir, filename);
        Files.write(path, file.getBytes());
        return "uploads/" + filename;
    }
}
