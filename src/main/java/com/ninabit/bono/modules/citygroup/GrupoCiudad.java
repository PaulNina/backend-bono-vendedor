package com.ninabit.bono.modules.citygroup;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "grupos_ciudad")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrupoCiudad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "display_order")
    @Builder.Default
    private int orden = 0;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "grupos_ciudad_ciudades", joinColumns = @JoinColumn(name = "grupo_id"))
    @Column(name = "ciudad")
    @Builder.Default
    private List<String> ciudades = new ArrayList<>();
}
