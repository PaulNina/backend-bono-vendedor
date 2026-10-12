package com.ninabit.bono.modules.damagedequipment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ninabit.bono.modules.user.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "registro_cliente_danado")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistroClienteDanado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_nombre", nullable = false, length = 200)
    private String clienteNombre;

    @Column(nullable = false, length = 100)
    private String ciudad;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "usuario_id", nullable = false)
    @JsonIgnoreProperties({"password", "resetPasswordToken", "resetPasswordTokenExpiry", "hibernateLazyInitializer"})
    private Usuario usuario;

    @OneToMany(mappedBy = "registroCliente", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private List<EquipoDanado> equipos = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (clienteNombre != null) {
            clienteNombre = clienteNombre.trim().toUpperCase();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        if (clienteNombre != null) {
            clienteNombre = clienteNombre.trim().toUpperCase();
        }
        updatedAt = LocalDateTime.now();
    }
}
