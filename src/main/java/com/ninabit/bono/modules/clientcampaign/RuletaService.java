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
     * Obtiene los premios físicos de la ruleta disponibles para un impulsador específico.
     * Busca por usuarioId, vendedorId, nombreDestinatario y tiendaId para evitar discrepancias.
     * REGLA ESTRICTA: Se excluyen automáticamente todos los premios cuyo saldo sea 0 y los que no son premios físicos.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> obtenerPremiosDisponiblesParaRuleta(
            Long campanaId,
            Long usuarioId,
            Long vendedorId,
            String nombreCompleto,
            Long tiendaId
    ) {
        List<InventarioImpulsador> inventarios = inventarioImpulsadorRepository
                .findAvailableByCampanaAndUser(campanaId, usuarioId, vendedorId, nombreCompleto, tiendaId);

        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        for (InventarioImpulsador inv : inventarios) {
            PremioRuleta p = inv.getPremioRuleta();
            if (p != null && p.isActivo() && !p.isEsSinPremio() && inv.getSaldo() > 0) {
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
                    item.put("esSinPremio", false);
                    map.put(p.getId(), item);
                }
            }
        }

        return new ArrayList<>(map.values());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> obtenerPremiosDisponiblesParaRuleta(Long campanaId, Long impulsadorId) {
        return obtenerPremiosDisponiblesParaRuleta(campanaId, impulsadorId, impulsadorId, null, null);
    }

    /**
     * Ejecuta el giro de la ruleta atómicamente:
     * 1. Si la campaña tiene habilitado "Siga Participando" y el azar (o la falta de stock físico)
     *    lo determina, registra la auditoría y retorna null (NO genera Entrega física ni descuenta stock).
     * 2. Si corresponde premio físico, selecciona uno ponderado entre los que tienen saldo > 0.
     * 3. Bloquea el inventario pesimísticamente y descuenta 1 unidad física.
     * 4. Registra la EntregaRuleta física vinculada al RegistroCliente.
     */
    @Transactional
    public EntregaRuleta girarRuletaYAsignarPremio(
            Long campanaId,
            Long registroClienteId,
            Long usuarioId,
            Long vendedorId,
            String impulsadorNombre,
            Long tiendaId
    ) {
        // 1. Validar registro de cliente
        RegistroCliente registro = registroClienteRepository.findById(registroClienteId)
                .orElseThrow(() -> new IllegalArgumentException("Registro de cliente no encontrado con ID: " + registroClienteId));

        // Validar que no haya girado previamente para este registro
        if (entregaRuletaRepository.findByRegistroClienteId(registroClienteId).isPresent()) {
            throw new IllegalStateException("Ya se ejecutó el giro de ruleta para este registro de compra.");
        }

        CampanaCliente campana = campanaClienteRepository.findById(campanaId)
                .orElseThrow(() -> new IllegalArgumentException("Campaña no encontrada: " + campanaId));

        // 2. Obtener inventario físico con saldo > 0 buscando por todos los identificadores del operador
        List<InventarioImpulsador> inventariosDisponibles = inventarioImpulsadorRepository
                .findAvailableByCampanaAndUser(campanaId, usuarioId, vendedorId, impulsadorNombre, tiendaId)
                .stream()
                .filter(inv -> inv.getPremioRuleta() != null && inv.getPremioRuleta().isActivo() && !inv.getPremioRuleta().isEsSinPremio())
                .toList();

        // 3. Evaluar Siga Participando si está habilitado en la campaña (NO genera Entrega física)
        if (campana.isRuletaSigaParticipandoHabilitado()) {
            int porcentaje = campana.getRuletaSigaParticipandoPorcentaje() != null ? campana.getRuletaSigaParticipandoPorcentaje() : 20;
            int roll = random.nextInt(100);
            if (roll < porcentaje || inventariosDisponibles.isEmpty()) {
                auditoriaService.log(
                        impulsadorNombre,
                        "GIRO_RULETA",
                        "Resultado Ruleta: SIGA PARTICIPANDO para cliente " + registro.getNombreCliente() + " CI: " + registro.getCi(),
                        "RegistroCliente",
                        registro.getId().toString()
                );
                return null;
            }
        }

        if (inventariosDisponibles.isEmpty()) {
            throw new IllegalStateException("El impulsador no cuenta con stock disponible de premios físicos para girar la ruleta.");
        }

        // 4. Selección aleatoria ponderada (Weighted Random) entre premios físicos
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

        // 5. Bloqueo pesimista para actualización atómica del stock por ID de asignación
        InventarioImpulsador invLocked = inventarioImpulsadorRepository
                .findByIdForUpdate(inventarioGanador.getId())
                .orElseThrow(() -> new IllegalStateException("No se pudo obtener el bloqueo de inventario del premio."));

        if (invLocked.getSaldo() <= 0) {
            throw new IllegalStateException("El stock del premio seleccionado se agotó en este instante. Vuelva a intentar el giro.");
        }

        // Descontar stock físico
        invLocked.setCantidadEntregada(invLocked.getCantidadEntregada() + 1);
        invLocked.calculateSaldo();
        inventarioImpulsadorRepository.save(invLocked);

        // 6. Crear la entrega de ruleta con premio físico
        Long finalImpulsadorId = usuarioId != null ? usuarioId : (vendedorId != null ? vendedorId : 0L);
        EntregaRuleta entrega = EntregaRuleta.builder()
                .registroCliente(registro)
                .campanaCliente(campana)
                .premioRuleta(invLocked.getPremioRuleta())
                .impulsadorId(finalImpulsadorId)
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

    @Transactional
    public EntregaRuleta girarRuletaYAsignarPremio(
            Long campanaId,
            Long registroClienteId,
            Long impulsadorId,
            String impulsadorNombre
    ) {
        return girarRuletaYAsignarPremio(campanaId, registroClienteId, impulsadorId, impulsadorId, impulsadorNombre, null);
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
