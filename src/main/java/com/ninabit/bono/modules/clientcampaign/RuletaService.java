package com.ninabit.bono.modules.clientcampaign;

import com.ninabit.bono.modules.audit.AuditoriaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RuletaService {

    private final CampanaClienteRepository campanaClienteRepository;
    private final PremioRuletaRepository premioRuletaRepository;
    private final InventarioImpulsadorRepository inventarioImpulsadorRepository;
    private final RegistroClienteRepository registroClienteRepository;
    private final EntregaRuletaRepository entregaRuletaRepository;
    private final AuditoriaService auditoriaService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private final SecureRandom random = new SecureRandom();

    /**
     * Obtiene los premios de la ruleta disponibles para un impulsador específico.
     * REGLA ESTRICTA: Se excluyen automáticamente todos los premios cuyo saldo sea 0.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> obtenerPremiosDisponiblesParaRuleta(Long campanaId, Long impulsadorId) {
        List<InventarioImpulsador> inventarios = inventarioImpulsadorRepository
                .findAvailableByCampanaAndVendedor(campanaId, impulsadorId);

        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        for (InventarioImpulsador inv : inventarios) {
            PremioRuleta p = inv.getPremioRuleta();
            if (p.isActivo() && inv.getSaldo() > 0) {
                if (map.containsKey(p.getId())) {
                    Map<String, Object> existing = map.get(p.getId());
                    int prevSaldo = (int) existing.get("saldoDisponible");
                    existing.put("saldoDisponible", prevSaldo + inv.getSaldo());
                } else {
                    Map<String, Object> item = new HashMap<>();
                    item.put("premioId", p.getId());
                    item.put("nombre", p.getNombre());
                    item.put("descripcion", p.getDescripcion());
                    item.put("imagenUrl", p.getImagenUrl());
                    item.put("colorHex", p.getColorHex());
                    item.put("probabilidadPeso", p.getProbabilidadPeso());
                    item.put("saldoDisponible", inv.getSaldo());
                    map.put(p.getId(), item);
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    /**
     * Ejecuta el giro de la ruleta atómicamente:
     * 1. Selecciona un premio ponderado entre los que tienen saldo > 0.
     * 2. Bloquea el inventario pesimísticamente y descuenta 1 unidad.
     * 3. Registra la entrega vinculada al RegistroCliente.
     */
    @Transactional
    public EntregaRuleta girarRuletaYAsignarPremio(
            Long campanaId,
            Long registroClienteId,
            Long impulsadorId,
            String impulsadorNombre
    ) {
        // 1. Validar registro de cliente
        RegistroCliente registro = registroClienteRepository.findById(registroClienteId)
                .orElseThrow(() -> new IllegalArgumentException("Registro de cliente no encontrado con ID: " + registroClienteId));

        // Validar que no haya girado previamente para este registro
        if (entregaRuletaRepository.findByRegistroClienteId(registroClienteId).isPresent()) {
            throw new IllegalStateException("Ya se ejecutó el giro de ruleta para este registro de compra.");
        }

        // 2. Obtener premios con saldo > 0
        List<InventarioImpulsador> inventariosDisponibles = inventarioImpulsadorRepository
                .findAvailableByCampanaAndVendedor(campanaId, impulsadorId);

        if (inventariosDisponibles.isEmpty()) {
            throw new IllegalStateException("El impulsador no cuenta con stock disponible de ningún premio para girar la ruleta.");
        }

        // 3. Selección aleatoria ponderada (Weighted Random)
        int totalPeso = inventariosDisponibles.stream()
                .mapToInt(inv -> Math.max(1, inv.getPremioRuleta().getProbabilidadPeso()))
                .sum();

        int target = random.nextInt(totalPeso);
        int acumulado = 0;
        InventarioImpulsador inventarioGanador = null;

        for (InventarioImpulsador inv : inventariosDisponibles) {
            acumulado += Math.max(1, inv.getPremioRuleta().getProbabilidadPeso());
            if (target < acumulado) {
                inventarioGanador = inv;
                break;
            }
        }

        if (inventarioGanador == null) {
            inventarioGanador = inventariosDisponibles.get(0);
        }

        // 4. Bloqueo pesimista para actualización atómica del stock por ID de asignación
        InventarioImpulsador invLocked = inventarioImpulsadorRepository
                .findByIdForUpdate(inventarioGanador.getId())
                .orElseThrow(() -> new IllegalStateException("No se pudo obtener el bloqueo de inventario del premio."));

        if (invLocked.getSaldo() <= 0) {
            throw new IllegalStateException("El stock del premio seleccionado se agotó en este instante. Vuelva a intentar el giro.");
        }

        // Descontar stock
        invLocked.setCantidadEntregada(invLocked.getCantidadEntregada() + 1);
        invLocked.calculateSaldo();
        inventarioImpulsadorRepository.save(invLocked);

        // 5. Crear la entrega de ruleta
        CampanaCliente campana = campanaClienteRepository.findById(campanaId)
                .orElseThrow(() -> new IllegalArgumentException("Campaña no encontrada: " + campanaId));

        EntregaRuleta entrega = EntregaRuleta.builder()
                .registroCliente(registro)
                .campanaCliente(campana)
                .premioRuleta(invLocked.getPremioRuleta())
                .impulsadorId(impulsadorId)
                .impulsadorNombre(impulsadorNombre)
                .fechaGiro(LocalDateTime.now())
                .revertido(false)
                .build();

        entrega = entregaRuletaRepository.save(entrega);

        auditoriaService.log(
                impulsadorNombre,
                "GIRO_RULETA",
                "Premio asignado: " + invLocked.getPremioRuleta().getNombre() + " a cliente " + registro.getNombreCliente() + " CI: " + registro.getCi() + " (Saldo restante: " + invLocked.getSaldo() + ")",
                "EntregaRuleta",
                entrega.getId().toString()
        );

        return entrega;
    }

    /**
     * Confirma la entrega física con fotografía del premio entregado al cliente.
     */
    @Transactional
    public EntregaRuleta confirmarEntregaConFoto(Long entregaId, MultipartFile fotoEntrega, String observacion) throws IOException {
        EntregaRuleta entrega = entregaRuletaRepository.findById(entregaId)
                .orElseThrow(() -> new IllegalArgumentException("Entrega de ruleta no encontrada con ID: " + entregaId));

        if (fotoEntrega != null && !fotoEntrega.isEmpty()) {
            String subDir = "entregas_ruleta/" + LocalDate.now().getYear() + "/" + LocalDate.now().getMonthValue();
            Path dirPath = Paths.get(uploadDir, subDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }
            String filename = "entrega_" + UUID.randomUUID().toString().substring(0, 10) + ".jpg";
            Path targetPath = dirPath.resolve(filename);
            Files.copy(fotoEntrega.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            entrega.setFotoEntrega("uploads/" + subDir + "/" + filename);
        }

        entregaRuletaRepository.save(entrega);
        auditoriaService.log(
                entrega.getImpulsadorNombre(),
                "CONFIRMAR_ENTREGA_RULETA",
                "Foto de entrega cargada para el premio " + entrega.getPremioRuleta().getNombre(),
                "EntregaRuleta",
                entrega.getId().toString()
        );
        return entrega;
    }
}
