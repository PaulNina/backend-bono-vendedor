package com.ninabit.bono.modules.restrictedserial;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "seriales_restringidos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SerialRestringido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String serial;

    private String motivo;

    @Column(name = "campana_nombre")
    private String campanaNombre;

    @Column(name = "importado_en")
    @Builder.Default
    private LocalDateTime importadoEn = LocalDateTime.now();
}
