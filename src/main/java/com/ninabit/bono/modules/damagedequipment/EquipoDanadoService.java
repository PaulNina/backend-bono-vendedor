package com.ninabit.bono.modules.damagedequipment;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.ninabit.bono.modules.audit.AuditoriaService;
import com.ninabit.bono.modules.product.Producto;
import com.ninabit.bono.modules.product.ProductoRepository;
import com.ninabit.bono.modules.serial.Serial;
import com.ninabit.bono.modules.serial.SerialRepository;
import com.ninabit.bono.modules.user.Usuario;
import com.ninabit.bono.modules.user.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EquipoDanadoService {

    private final CatalogoClienteDanadoRepository catalogoClienteRepository;
    private final RegistroClienteDanadoRepository registroClienteRepository;
    private final EquipoDanadoRepository equipoDanadoRepository;
    private final CatalogoFallaRepository catalogoFallaRepository;
    private final SerialRepository serialRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @jakarta.annotation.PostConstruct
    public void initCatalogos() {
        List<String> defaultFallas = List.of(
                "Pantalla rota",
                "Manchas dispersas",
                "Manchas negras",
                "Píxeles muertos (cuadros quemados)",
                "Líneas en la pantalla"
        );
        for (String f : defaultFallas) {
            if (!catalogoFallaRepository.existsByNombreIgnoreCase(f)) {
                catalogoFallaRepository.save(CatalogoFalla.builder()
                        .nombre(f)
                        .descripcion("Falla común estándar")
                        .activo(true)
                        .build());
            }
        }
    }

    // --- Catálogo de Clientes ---
    public List<CatalogoClienteDanado> getClientesActivos(String search) {
        if (search != null && !search.trim().isEmpty()) {
            return catalogoClienteRepository.findByActivoTrueAndNombreContainingIgnoreCaseOrderByNombreAsc(search.trim());
        }
        return catalogoClienteRepository.findByActivoTrueOrderByNombreAsc();
    }

    public List<CatalogoClienteDanado> getTodosLosClientes(String search) {
        if (search != null && !search.trim().isEmpty()) {
            return catalogoClienteRepository.findByNombreContainingIgnoreCaseOrderByNombreAsc(search.trim());
        }
        return catalogoClienteRepository.findAllByOrderByNombreAsc();
    }

    @Transactional
    public CatalogoClienteDanado agregarCliente(String nombre, String ciudad) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del cliente no puede estar vacío");
        }
        String cleanName = nombre.trim().toUpperCase();
        String cleanCiudad = ciudad != null && !ciudad.trim().isEmpty() ? ciudad.trim() : null;

        return catalogoClienteRepository.findByNombreIgnoreCase(cleanName)
                .map(existing -> {
                    if (cleanCiudad != null && (existing.getCiudad() == null || existing.getCiudad().isEmpty())) {
                        existing.setCiudad(cleanCiudad);
                        return catalogoClienteRepository.save(existing);
                    }
                    if (!existing.isActivo()) {
                        existing.setActivo(true);
                        return catalogoClienteRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> catalogoClienteRepository.save(CatalogoClienteDanado.builder()
                        .nombre(cleanName)
                        .ciudad(cleanCiudad)
                        .activo(true)
                        .build()));
    }

    @Transactional
    public CatalogoClienteDanado actualizarCliente(Long id, String nombre, String ciudad, Boolean activo) {
        CatalogoClienteDanado cliente = catalogoClienteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente con ID " + id + " no encontrado"));

        if (nombre != null && !nombre.trim().isEmpty()) {
            cliente.setNombre(nombre.trim().toUpperCase());
        }
        if (ciudad != null) {
            cliente.setCiudad(ciudad.trim().isEmpty() ? null : ciudad.trim());
        }
        if (activo != null) {
            cliente.setActivo(activo);
        }
        return catalogoClienteRepository.save(cliente);
    }

    @Transactional
    public void eliminarCliente(Long id) {
        catalogoClienteRepository.findById(id).ifPresent(c -> {
            c.setActivo(false);
            catalogoClienteRepository.save(c);
        });
    }

    // --- Catálogo de Fallas ---
    public List<CatalogoFalla> getFallasActivas() {
        return catalogoFallaRepository.findByActivoTrueOrderByNombreAsc();
    }

    public List<CatalogoFalla> getTodasLasFallas() {
        return catalogoFallaRepository.findAllByOrderByNombreAsc();
    }

    @Transactional
    public CatalogoFalla agregarFalla(String nombre, String descripcion) {
        String cleanName = nombre.trim();
        return catalogoFallaRepository.findByNombreIgnoreCase(cleanName)
                .map(existing -> {
                    if (!existing.isActivo()) {
                        existing.setActivo(true);
                        if (descripcion != null && !descripcion.isBlank()) existing.setDescripcion(descripcion.trim());
                        return catalogoFallaRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> catalogoFallaRepository.save(CatalogoFalla.builder()
                        .nombre(cleanName)
                        .descripcion(descripcion != null ? descripcion.trim() : null)
                        .activo(true)
                        .build()));
    }

    @Transactional
    public CatalogoFalla actualizarFalla(Long id, String nombre, String descripcion, Boolean activo) {
        CatalogoFalla falla = catalogoFallaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Falla con ID " + id + " no encontrada"));

        if (nombre != null && !nombre.trim().isEmpty()) {
            falla.setNombre(nombre.trim());
        }
        if (descripcion != null) {
            falla.setDescripcion(descripcion.trim().isEmpty() ? null : descripcion.trim());
        }
        if (activo != null) {
            falla.setActivo(activo);
        }
        return catalogoFallaRepository.save(falla);
    }

    @Transactional
    public void eliminarFalla(Long id) {
        catalogoFallaRepository.findById(id).ifPresent(f -> {
            f.setActivo(false);
            catalogoFallaRepository.save(f);
        });
    }

    // --- Consulta y Validación de Serial ---
    public Map<String, Object> lookupSerialInfo(String serialNumber) {
        if (serialNumber == null || serialNumber.trim().isEmpty()) {
            return Map.of("encontrado", false, "mensaje", "Número de serie vacío");
        }
        String cleanSerial = serialNumber.trim();
        String normalizedSerial = cleanSerial.replaceAll("[^a-zA-Z0-9]", "");

        // 1. Direct search
        Optional<Serial> optSerial = serialRepository.findByNumeroSerieIgnoreCase(cleanSerial);

        // 2. Normalized search without hyphens/symbols
        if (optSerial.isEmpty() && !normalizedSerial.isEmpty()) {
            optSerial = serialRepository.findByNumeroSerieIgnoreCase(normalizedSerial);
        }
        if (optSerial.isEmpty() && !normalizedSerial.isEmpty()) {
            optSerial = serialRepository.findByNormalizedSerial(normalizedSerial.toLowerCase());
        }

        if (optSerial.isPresent()) {
            Serial s = optSerial.get();
            Producto p = s.getProducto();
            if (p == null && s.getProductoId() != null) {
                p = productoRepository.findById(s.getProductoId()).orElse(null);
            }

            boolean yaRegistrado = equipoDanadoRepository.existsBySerialValidadoIgnoreCase(cleanSerial);

            Map<String, Object> result = new HashMap<>();
            result.put("encontrado", true);
            result.put("serial", s.getNumeroSerie());
            result.put("modelo", s.getModelo() != null ? s.getModelo() : (p != null ? (p.getModelo() != null ? p.getModelo() : p.getModeloCodigo()) : null));
            result.put("pulgadas", p != null ? (p.getTamanoPulgadas() != null ? p.getTamanoPulgadas() : p.getPulgadas()) : null);
            result.put("productoNombre", p != null ? p.getNombre() : s.getProductoNombre());
            result.put("productoId", p != null ? p.getId() : s.getProductoId());
            result.put("bloqueado", s.isBloqueado());
            result.put("motivoBloqueo", s.getMotivoBloqueo());
            result.put("yaRegistradoDanado", yaRegistrado);
            return result;
        }

        // Si no está en tabla serial, aún verificar si fue registrado como dañado
        boolean yaRegistrado = equipoDanadoRepository.existsBySerialValidadoIgnoreCase(cleanSerial);
        return Map.of(
                "encontrado", false,
                "serial", cleanSerial,
                "mensaje", "Serial no registrado previamente en el maestro de seriales",
                "yaRegistradoDanado", yaRegistrado
        );
    }

    // --- Lectura de Código de Barras desde Imagen en Backend con Rotaciones ---
    public String decodeBarcodeFromImage(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        try (InputStream is = file.getInputStream()) {
            BufferedImage image = ImageIO.read(is);
            if (image == null) return null;

            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(
                    BarcodeFormat.CODE_128,
                    BarcodeFormat.CODE_39,
                    BarcodeFormat.CODE_93,
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.DATA_MATRIX
            ));

            // Intentar en orientación 0°, 90°, 180°, 270°
            BufferedImage currentImg = image;
            for (int r = 0; r < 4; r++) {
                // Pass 1: HybridBinarizer
                String res = tryDecode(currentImg, hints, false);
                if (res != null) return res;

                // Pass 2: GlobalHistogramBinarizer
                res = tryDecode(currentImg, hints, true);
                if (res != null) return res;

                // Pass 3: Center crop (frecuente en fotos con teléfono)
                int w = currentImg.getWidth();
                int h = currentImg.getHeight();
                if (w > 300 && h > 300) {
                    int cropW = (int) (w * 0.85);
                    int cropH = (int) (h * 0.70);
                    int cropX = (w - cropW) / 2;
                    int cropY = (h - cropH) / 2;
                    BufferedImage cropped = currentImg.getSubimage(cropX, cropY, cropW, cropH);
                    res = tryDecode(cropped, hints, false);
                    if (res != null) return res;
                    res = tryDecode(cropped, hints, true);
                    if (res != null) return res;
                }

                // Rotar 90 grados para siguiente pasada
                currentImg = rotate90(currentImg);
            }

            return null;
        } catch (Exception e) {
            log.warn("Error intentando decodificar código de barras en backend: {}", e.getMessage());
            return null;
        }
    }

    private BufferedImage rotate90(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage rotated = new BufferedImage(h, w, img.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : img.getType());
        java.awt.Graphics2D g2d = rotated.createGraphics();
        g2d.translate((h - w) / 2.0, (h - w) / 2.0);
        g2d.rotate(Math.PI / 2.0, h / 2.0, w / 2.0);
        g2d.drawRenderedImage(img, null);
        g2d.dispose();
        return rotated;
    }

    private String tryDecode(BufferedImage img, Map<DecodeHintType, Object> hints, boolean useGlobal) {
        try {
            LuminanceSource source = new BufferedImageLuminanceSource(img);
            Binarizer binarizer = useGlobal ? new com.google.zxing.common.GlobalHistogramBinarizer(source) : new HybridBinarizer(source);
            BinaryBitmap bitmap = new BinaryBitmap(binarizer);
            Result result = new MultiFormatReader().decode(bitmap, hints);
            return result != null ? result.getText().trim() : null;
        } catch (NotFoundException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // --- Registro de Equipo Dañado ---
    @Transactional
    public EquipoDanado registrarEquipoDanado(
            Long registroClienteId,
            String clienteNombre,
            String ciudad,
            String serialCaja,
            String serialTv,
            Boolean serialIngresoManual,
            String serialValidado,
            String modelo,
            Integer pulgadas,
            String tipoFalla,
            String comentarios,
            MultipartFile fotoTagCaja,
            MultipartFile fotoTagTv,
            MultipartFile fotoFalla1,
            MultipartFile fotoFalla2,
            MultipartFile fotoFalla3,
            String userEmail
    ) throws IOException {

        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + userEmail));

        // 0. Validar serial duplicado
        String serialParaValidar = (serialValidado != null && !serialValidado.isBlank()) ? serialValidado.trim() :
                (serialTv != null ? serialTv.trim() : "");
        if (!serialParaValidar.isEmpty() && equipoDanadoRepository.existsBySerialValidadoIgnoreCase(serialParaValidar)) {
            throw new IllegalArgumentException("El serial '" + serialParaValidar + "' ya fue registrado anteriormente como equipo dañado. No se permiten registros duplicados.");
        }

        // 1. Obtener o crear RegistroCliente
        RegistroClienteDanado registroCliente;
        if (registroClienteId != null && registroClienteId > 0) {
            registroCliente = registroClienteRepository.findById(registroClienteId)
                    .orElseThrow(() -> new IllegalArgumentException("Registro de cliente ID " + registroClienteId + " no encontrado"));
        } else {
            if (clienteNombre == null || clienteNombre.trim().isEmpty()) {
                throw new IllegalArgumentException("El nombre del cliente es obligatorio");
            }
            if (ciudad == null || ciudad.trim().isEmpty()) {
                throw new IllegalArgumentException("La ciudad es obligatoria");
            }
            registroCliente = RegistroClienteDanado.builder()
                    .clienteNombre(clienteNombre.trim().toUpperCase())
                    .ciudad(ciudad.trim())
                    .usuario(usuario)
                    .build();
            registroCliente = registroClienteRepository.save(registroCliente);
        }

        // 2. Validar seriales y comparación
        String finalSerialCaja = serialCaja != null ? serialCaja.trim() : "";
        String finalSerialTv = serialTv != null ? serialTv.trim() : "";
        String finalSerial = serialValidado != null && !serialValidado.isBlank() ? serialValidado.trim() : finalSerialTv;

        String normCaja = finalSerialCaja.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        String normTv = finalSerialTv.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        boolean matches = !normCaja.isEmpty() && !normTv.isEmpty() && normCaja.equals(normTv);
        boolean manual = Boolean.TRUE.equals(serialIngresoManual);

        // 3. Buscar información del producto si no vino o para asegurar
        Producto producto = null;
        String finalModelo = modelo;
        Integer finalPulgadas = pulgadas;

        Optional<Serial> optSerial = serialRepository.findByNumeroSerieIgnoreCase(finalSerial);
        if (optSerial.isPresent()) {
            Serial s = optSerial.get();
            if (s.getProducto() != null) {
                producto = s.getProducto();
            } else if (s.getProductoId() != null) {
                producto = productoRepository.findById(s.getProductoId()).orElse(null);
            }
            if (finalModelo == null || finalModelo.isBlank()) {
                finalModelo = s.getModelo() != null ? s.getModelo() : (producto != null ? producto.getModelo() : null);
            }
            if (finalPulgadas == null && producto != null) {
                finalPulgadas = producto.getTamanoPulgadas() != null ? producto.getTamanoPulgadas() : producto.getPulgadas();
            }
        }

        // 4. Guardar archivos de fotos
        String year = String.valueOf(LocalDateTime.now().getYear());
        String subDir = "equipos_danados/" + year + "/CLI_" + registroCliente.getId() + "_SERIAL_" + finalSerial.replaceAll("[^a-zA-Z0-9_-]", "_");

        String pathTagCaja = saveFile(fotoTagCaja, subDir, "tag_caja");
        String pathTagTv = saveFile(fotoTagTv, subDir, "tag_tv");
        String pathFalla1 = saveFile(fotoFalla1, subDir, "falla_1");
        String pathFalla2 = saveFile(fotoFalla2, subDir, "falla_2");
        String pathFalla3 = saveFile(fotoFalla3, subDir, "falla_3");

        // 5. Crear y guardar EquipoDanado
        EquipoDanado equipo = EquipoDanado.builder()
                .registroCliente(registroCliente)
                .serialCaja(finalSerialCaja)
                .serialTv(finalSerialTv)
                .serialesCoinciden(matches)
                .serialIngresoManual(manual)
                .serialValidado(finalSerial)
                .producto(producto)
                .modelo(finalModelo)
                .pulgadas(finalPulgadas)
                .tipoFalla(tipoFalla != null ? tipoFalla.trim() : "Falla no especificada")
                .comentarios(comentarios != null ? comentarios.trim() : null)
                .fotoTagCaja(pathTagCaja)
                .fotoTagTv(pathTagTv)
                .fotoFalla1(pathFalla1)
                .fotoFalla2(pathFalla2)
                .fotoFalla3(pathFalla3)
                .build();

        EquipoDanado savedEquipo = equipoDanadoRepository.save(equipo);

        // Si la falla no existía en el catálogo, agregarla automáticamente
        if (tipoFalla != null && !tipoFalla.isBlank()) {
            agregarFalla(tipoFalla, "Registrada automáticamente");
        }

        // Si el cliente no existía en el catálogo, agregarlo automáticamente
        if (registroCliente.getClienteNombre() != null && !registroCliente.getClienteNombre().isBlank()) {
            try {
                agregarCliente(registroCliente.getClienteNombre(), registroCliente.getCiudad());
            } catch (Exception e) {
                log.warn("No se pudo agregar cliente al catálogo automáticamente: {}", e.getMessage());
            }
        }

        auditoriaService.log(userEmail, "REGISTRO_EQUIPO_DANADO",
                "Equipo dañado registrado ID=" + savedEquipo.getId() + ", Serial=" + finalSerial + ", Cliente=" + registroCliente.getClienteNombre(),
                "EquipoDanado", savedEquipo.getId().toString());

        return savedEquipo;
    }

    // --- Consultas y Filtros ---
    @Transactional(readOnly = true)
    public Page<EquipoDanado> getEquiposPage(
            String ciudad,
            String cliente,
            String serial,
            String modelo,
            String tipoFalla,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin,
            Pageable pageable
    ) {
        org.springframework.data.jpa.domain.Specification<EquipoDanado> spec =
                EquipoDanadoSpecification.filterBy(ciudad, cliente, serial, modelo, tipoFalla, fechaInicio, fechaFin);
        return equipoDanadoRepository.findAll(spec, pageable);
    }

    public List<EquipoDanado> getEquiposListForExport(
            String ciudad,
            String cliente,
            String serial,
            String modelo,
            String tipoFalla,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin
    ) {
        org.springframework.data.jpa.domain.Specification<EquipoDanado> spec =
                EquipoDanadoSpecification.filterBy(ciudad, cliente, serial, modelo, tipoFalla, fechaInicio, fechaFin);
        return equipoDanadoRepository.findAll(spec, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
    }

    public Optional<EquipoDanado> getEquipoById(Long id) {
        return equipoDanadoRepository.findById(id);
    }

    @Transactional
    public void eliminarEquipoDanado(Long id, String userEmail) {
        EquipoDanado equipo = equipoDanadoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Equipo dañado con ID " + id + " no encontrado"));

        String serial = equipo.getSerialValidado() != null ? equipo.getSerialValidado() : equipo.getSerialTv();
        equipoDanadoRepository.delete(equipo);

        auditoriaService.log(userEmail, "ELIMINAR_EQUIPO_DANADO",
                "Registro de equipo dañado eliminado ID=" + id + ", Serial=" + serial,
                "EquipoDanado", id.toString());
    }

    private String saveFile(MultipartFile file, String subDir, String fixedFilename) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String originalName = file.getOriginalFilename();
        String extension = ".jpg";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }

        String filename = fixedFilename + "_" + System.currentTimeMillis() + extension;
        Path targetDirPath = Paths.get(uploadDir, subDir);
        Files.createDirectories(targetDirPath);

        Path filePath = targetDirPath.resolve(filename);
        Files.write(filePath, file.getBytes());

        return "uploads/" + subDir + "/" + filename;
    }
}
