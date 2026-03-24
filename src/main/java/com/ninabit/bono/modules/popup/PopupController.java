package com.ninabit.bono.modules.popup;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Popups")
public class PopupController {

    private final PopupRepository popupRepository;
    private final com.ninabit.bono.modules.audit.AuditoriaService auditoriaService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    // ─── PUBLIC ────────────────────────────────────────────────────────────────

    @GetMapping("/public/popups")
    @Operation(summary = "Listar popups activos (público)")
    public List<Popup> getPublicPopups() {
        return popupRepository.findByActivoTrueOrderByOrdenAsc();
    }

    // ─── ADMIN ─────────────────────────────────────────────────────────────────

    @GetMapping("/admin/popups")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar todos los popups")
    public List<Popup> getAllPopups() {
        return popupRepository.findAllByOrderByOrdenAsc();
    }

    @PostMapping(value = "/admin/popups", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear popup con imagen")
    public Popup createPopup(
            @RequestParam(value = "titulo", required = false) String titulo,
            @RequestParam("imagen") MultipartFile imagen) {

        String imagenUrl;
        try {
            imagenUrl = saveImage(imagen);
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar imagen de popup", e);
        }

        // Calcular orden: último + 1
        int maxOrden = popupRepository.findAllByOrderByOrdenAsc().stream()
                .mapToInt(Popup::getOrden)
                .max()
                .orElse(-1);

        Popup popup = Popup.builder()
                .titulo(titulo)
                .imagenUrl(imagenUrl)
                .activo(true)
                .orden(maxOrden + 1)
                .build();

        Popup saved = popupRepository.save(popup);
        auditoriaService.log(getUser(), "POPUP_CREADO",
            "Popup creado: " + saved.getTitulo(), "Popup", saved.getId().toString());
        return saved;
    }

    @PutMapping("/admin/popups/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar campos del popup (titulo, activo, orden)")
    public Popup updatePopup(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Popup popup = popupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Popup no encontrado"));

        if (payload.containsKey("titulo")) {
            popup.setTitulo((String) payload.get("titulo"));
        }
        if (payload.containsKey("activo")) {
            popup.setActivo(Boolean.parseBoolean(payload.get("activo").toString()));
        }
        if (payload.containsKey("orden")) {
            popup.setOrden(Integer.parseInt(payload.get("orden").toString()));
        }

        Popup saved = popupRepository.save(popup);
        auditoriaService.log(getUser(), "POPUP_ACTUALIZADO",
            "Popup actualizado: " + saved.getTitulo(), "Popup", saved.getId().toString());
        return saved;
    }

    @PutMapping("/admin/popups/{id}/toggle-activo")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activar/Desactivar popup")
    public ResponseEntity<Popup> togglePopupActivo(@PathVariable Long id) {
        return popupRepository.findById(id).map(p -> {
            p.setActivo(!p.isActivo());
            Popup saved = popupRepository.save(p);
            auditoriaService.log(getUser(), "POPUP_TOGGLE_ACTIVO",
                "Popup " + (saved.isActivo() ? "activado" : "desactivado") + ": " + saved.getTitulo(),
                "Popup", id.toString());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/admin/popups/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar popup")
    public ResponseEntity<Void> deletePopup(@PathVariable Long id) {
        return popupRepository.findById(id).map(p -> {
            // Eliminar archivo físico
            try {
                Path filePath = Paths.get(uploadDir).resolve(
                        p.getImagenUrl().replaceFirst("^uploads/", ""));
                Files.deleteIfExists(filePath);
            } catch (IOException ignored) {
            }

            popupRepository.delete(p);
            auditoriaService.log(getUser(), "POPUP_ELIMINADO", 
                "Popup eliminado: " + p.getTitulo(), "Popup", id.toString());
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    private String getUser() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
    }

    // ─── HELPER ────────────────────────────────────────────────────────────────

    private String saveImage(MultipartFile file) throws IOException {
        String ext = "";
        String originalName = file.getOriginalFilename();
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String filename = UUID.randomUUID() + ext;
        Path dir = Paths.get(uploadDir, "popups");
        Files.createDirectories(dir);
        Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        return "uploads/popups/" + filename;
    }
}
