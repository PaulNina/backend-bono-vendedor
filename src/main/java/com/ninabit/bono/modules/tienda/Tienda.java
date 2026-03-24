package com.ninabit.bono.modules.tienda;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tiendas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "nombre_propietario")
    private String nombrePropietario;

    private String direccion;

    private String telefono;

    @ManyToOne
    @JoinColumn(name = "ciudad_id", nullable = false)
    private com.ninabit.bono.modules.city.Ciudad ciudad;

    @Column(name = "nit")
    private String nit;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean activo = true;
}
