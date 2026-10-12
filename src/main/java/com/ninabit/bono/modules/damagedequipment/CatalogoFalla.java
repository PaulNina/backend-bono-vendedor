package com.ninabit.bono.modules.damagedequipment;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "catalogo_falla")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogoFalla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String nombre;

    @Column(length = 255)
    private String descripcion;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
