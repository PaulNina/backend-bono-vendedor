package com.ninabit.bono.modules.damagedequipment;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "catalogo_cliente_danado")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CatalogoClienteDanado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String nombre;

    @Column(length = 100)
    private String ciudad;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (nombre != null) {
            nombre = nombre.trim().toUpperCase();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        if (nombre != null) {
            nombre = nombre.trim().toUpperCase();
        }
        updatedAt = LocalDateTime.now();
    }
}
