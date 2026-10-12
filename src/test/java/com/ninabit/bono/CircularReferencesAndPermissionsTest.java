package com.ninabit.bono;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ninabit.bono.modules.campaign.Campana;
import com.ninabit.bono.modules.damagedequipment.EquipoDanado;
import com.ninabit.bono.modules.damagedequipment.RegistroClienteDanado;
import com.ninabit.bono.modules.liquidation.LoteLiquidacion;
import com.ninabit.bono.modules.liquidation.LoteLiquidacionDetalle;
import com.ninabit.bono.modules.sale.Venta;
import com.ninabit.bono.modules.sale.VentaCampana;
import com.ninabit.bono.modules.serial.Serial;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CircularReferencesAndPermissionsTest {

    @Test
    @DisplayName("CP-01: Venta y VentaCampana no generan bucle infinito en toString() y hashCode()")
    void testVentaAndVentaCampanaNoCircularLoop() {
        Venta venta = Venta.builder()
                .id(100L)
                .serial("SN-TEST-001")
                .estado(Venta.Estado.PENDIENTE)
                .build();

        VentaCampana vc = VentaCampana.builder()
                .id(200L)
                .venta(venta)
                .puntosGanados(10)
                .bonoGanado(50)
                .build();

        venta.setDetallesCampanas(new ArrayList<>(List.of(vc)));

        // Probamos toString y hashCode en ambos sentidos sin provocar StackOverflowError
        assertDoesNotThrow(() -> {
            String vStr = venta.toString();
            String vcStr = vc.toString();
            int vHash = venta.hashCode();
            int vcHash = vc.hashCode();
            assertNotNull(vStr);
            assertNotNull(vcStr);
            assertTrue(vHash != 0 || vHash == 0);
            assertTrue(vcHash != 0 || vcHash == 0);
        }, "No debe ocurrir recursión infinita en Venta y VentaCampana");
    }

    @Test
    @DisplayName("CP-02: LoteLiquidacion y Detalle no generan bucle infinito")
    void testLoteLiquidacionNoCircularLoop() {
        LoteLiquidacion lote = LoteLiquidacion.builder()
                .id(300L)
                .codigoLote("LOT-2026-001")
                .build();

        LoteLiquidacionDetalle det = LoteLiquidacionDetalle.builder()
                .id(400L)
                .lote(lote)
                .serial("SN-LOTE-001")
                .build();

        lote.setDetalles(new ArrayList<>(List.of(det)));

        assertDoesNotThrow(() -> {
            String lStr = lote.toString();
            String dStr = det.toString();
            int lHash = lote.hashCode();
            int dHash = det.hashCode();
            assertNotNull(lStr);
            assertNotNull(dStr);
            assertNotNull(lHash);
            assertNotNull(dHash);
        });
    }

    @Test
    @DisplayName("CP-03: RegistroClienteDanado y EquipoDanado no generan bucle infinito")
    void testEquiposDanadosNoCircularLoop() {
        RegistroClienteDanado reg = RegistroClienteDanado.builder()
                .id(500L)
                .clienteNombre("JUAN PEREZ")
                .ciudad("LA PAZ")
                .build();

        EquipoDanado eq = EquipoDanado.builder()
                .id(600L)
                .registroCliente(reg)
                .serialTv("TV-001")
                .tipoFalla("Pantalla quebrada")
                .build();

        reg.setEquipos(new ArrayList<>(List.of(eq)));

        assertDoesNotThrow(() -> {
            String rStr = reg.toString();
            String eStr = eq.toString();
            int rHash = reg.hashCode();
            int eHash = eq.hashCode();
            assertNotNull(rStr);
            assertNotNull(eStr);
            assertNotNull(rHash);
            assertNotNull(eHash);
        });
    }

    @Test
    @DisplayName("CP-04: Serial y Venta no generan bucle infinito")
    void testSerialAndVentaNoCircularLoop() {
        Venta venta = Venta.builder()
                .id(700L)
                .serial("SN-SER-001")
                .build();

        Serial serial = Serial.builder()
                .id(800L)
                .numeroSerie("SN-SER-001")
                .registroVendedor(venta)
                .build();

        assertDoesNotThrow(() -> {
            String sStr = serial.toString();
            int sHash = serial.hashCode();
            assertNotNull(sStr);
            assertNotNull(sHash);
        });
    }

    @Test
    @DisplayName("CP-05: Campana serializa a JSON ignorando tiendasExcluidas (evita LazyInitializationException)")
    void testCampanaJsonSerializationIgnoresLazyCollection() throws Exception {
        Campana campana = Campana.builder()
                .id(1L)
                .nombre("Campaña de Prueba")
                .activo(true)
                .registroHabilitado(true)
                .build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        String json = mapper.writeValueAsString(campana);
        assertNotNull(json);
        assertFalse(json.contains("tiendasExcluidas"), "El JSON no debe incluir el campo lazy tiendasExcluidas");
        assertTrue(json.contains("Campaña de Prueba"));
    }
}
