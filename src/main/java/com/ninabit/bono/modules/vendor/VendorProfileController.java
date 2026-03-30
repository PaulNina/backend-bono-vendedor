package com.ninabit.bono.modules.vendor;

import com.ninabit.bono.modules.city.Ciudad;
import com.ninabit.bono.modules.city.CiudadRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/vendor/me")
@RequiredArgsConstructor
@Tag(name = "Vendor - Perfil")
public class VendorProfileController {

    private final VendedorRepository vendedorRepository;
    private final CiudadRepository ciudadRepository;

    @GetMapping
    @Operation(summary = "Obtener mi perfil de vendedor")
    public ResponseEntity<Vendedor> getMyProfile(Authentication auth) {
        return vendedorRepository.findByEmail(auth.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @PutMapping(consumes = { "multipart/form-data" })
    @Operation(summary = "Actualizar mi perfil de vendedor y subir QR")
    public ResponseEntity<Vendedor> updateMyProfile(Authentication auth,
            @RequestParam(required = false) String nombreCompleto,
            @RequestParam(required = false) String telefono,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false) String tienda,
            @RequestParam(required = false) String tallaPolera,
            @RequestParam(required = false) org.springframework.web.multipart.MultipartFile fotoQr) {

        return vendedorRepository.findByEmail(auth.getName()).map(v -> {
            if (nombreCompleto != null && !nombreCompleto.isBlank()) {
                v.setNombreCompleto(nombreCompleto.trim());
            }
            if (telefono != null) {
                v.setTelefono(telefono.trim());
            }
            if (ciudad != null && !ciudad.isBlank()) {
                Ciudad c = ciudadRepository.findByNombreIgnoreCase(ciudad.trim()).orElse(null);
                v.setCiudad(c);
            }
            if (tienda != null) {
                v.setTienda(tienda.trim());
            }
            if (tallaPolera != null && !tallaPolera.isBlank()) {
                v.setTallaPolera(tallaPolera);
            }
            if (fotoQr != null && !fotoQr.isEmpty()) {
                try {
                    java.io.File dir = new java.io.File(uploadDir);
                    if (!dir.exists())
                        dir.mkdirs();
                    String filename = java.util.UUID.randomUUID() + "_" + fotoQr.getOriginalFilename();
                    java.nio.file.Path path = java.nio.file.Paths.get(uploadDir, filename);
                    java.nio.file.Files.write(path, fotoQr.getBytes());
                    v.setFotoQr("uploads/" + filename);
                } catch (java.io.IOException e) {
                    throw new RuntimeException("Error al guardar la foto QR", e);
                }
            }
            return ResponseEntity.ok(vendedorRepository.save(v));
        }).orElse(ResponseEntity.notFound().build());
    }
}
