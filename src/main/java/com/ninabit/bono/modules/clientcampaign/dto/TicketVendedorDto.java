package com.ninabit.bono.modules.clientcampaign.dto;

import com.ninabit.bono.modules.vendorprize.TicketVendedor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketVendedorDto {
    private Long id;
    private String numeroTicket;
    private String estado;
    private LocalDateTime createdAt;

    private VendedorSimpleDto vendedor;
    private VentaSimpleDto venta;
    private CampanaSimpleDto campana;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VendedorSimpleDto {
        private Long id;
        private String nombreCompleto;
        private String ci;
        private String telefono;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VentaSimpleDto {
        private Long id;
        private String serial;
        private String productModel;
        private String storeName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampanaSimpleDto {
        private Long id;
        private String nombre;
    }

    public static TicketVendedorDto fromEntity(TicketVendedor tv) {
        if (tv == null) return null;
        VendedorSimpleDto vendedorDto = null;
        if (tv.getVendedor() != null) {
            vendedorDto = VendedorSimpleDto.builder()
                    .id(tv.getVendedor().getId())
                    .nombreCompleto(tv.getVendedor().getNombreCompleto())
                    .ci(tv.getVendedor().getCi())
                    .telefono(tv.getVendedor().getTelefono())
                    .build();
        }

        VentaSimpleDto ventaDto = null;
        if (tv.getVenta() != null) {
            ventaDto = VentaSimpleDto.builder()
                    .id(tv.getVenta().getId())
                    .serial(tv.getVenta().getSerial())
                    .productModel(tv.getVenta().getProductModel())
                    .storeName(tv.getVenta().getStoreName())
                    .build();
        }

        CampanaSimpleDto campanaDto = null;
        if (tv.getCampana() != null) {
            campanaDto = CampanaSimpleDto.builder()
                    .id(tv.getCampana().getId())
                    .nombre(tv.getCampana().getNombre())
                    .build();
        }

        return TicketVendedorDto.builder()
                .id(tv.getId())
                .numeroTicket(tv.getNumeroTicket())
                .estado(tv.getEstado() != null ? tv.getEstado().name() : null)
                .createdAt(tv.getCreatedAt())
                .vendedor(vendedorDto)
                .venta(ventaDto)
                .campana(campanaDto)
                .build();
    }
}
