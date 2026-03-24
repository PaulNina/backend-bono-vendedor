package com.ninabit.bono.modules.city;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ciudades")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ciudad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    @Column
    private String departamento;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "display_order")
    @Builder.Default
    private int orden = 0;
}
