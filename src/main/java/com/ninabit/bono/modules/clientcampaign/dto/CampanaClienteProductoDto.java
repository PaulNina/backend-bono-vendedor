package com.ninabit.bono.modules.clientcampaign.dto;

import com.ninabit.bono.modules.clientcampaign.CampanaClienteProducto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampanaClienteProductoDto {
    private Long id;
    private Long campanaClienteId;
    private ProductoSimpleDto producto;
    private Integer tickets;
    private boolean activo;
    private Long productoId;
    private String productoNombre;
    private String productoModelo;
    private Integer productoPulgadas;
    private Integer multiplicadorTickets;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductoSimpleDto {
        private Long id;
        private String nombre;
        private String modelo;
        private Integer tamanoPulgadas;
        private Integer pulgadas;
    }

    public static CampanaClienteProductoDto fromEntity(CampanaClienteProducto cp) {
        if (cp == null) return null;
        ProductoSimpleDto prodDto = null;
        if (cp.getProducto() != null) {
            prodDto = ProductoSimpleDto.builder()
                    .id(cp.getProducto().getId())
                    .nombre(cp.getProducto().getNombre())
                    .modelo(cp.getProducto().getModelo())
                    .tamanoPulgadas(cp.getProducto().getPulgadas())
                    .pulgadas(cp.getProducto().getPulgadas())
                    .build();
        }

        return CampanaClienteProductoDto.builder()
                .id(cp.getId())
                .campanaClienteId(cp.getCampanaCliente() != null ? cp.getCampanaCliente().getId() : null)
                .producto(prodDto)
                .tickets(cp.getTickets() != null ? cp.getTickets() : 1)
                .activo(true)
                .productoId(cp.getProducto() != null ? cp.getProducto().getId() : null)
                .productoNombre(cp.getProducto() != null ? cp.getProducto().getNombre() : null)
                .productoModelo(cp.getProducto() != null ? cp.getProducto().getModelo() : null)
                .productoPulgadas(cp.getProducto() != null ? cp.getProducto().getPulgadas() : null)
                .multiplicadorTickets(cp.getMultiplicadorTickets())
                .build();
    }
}
