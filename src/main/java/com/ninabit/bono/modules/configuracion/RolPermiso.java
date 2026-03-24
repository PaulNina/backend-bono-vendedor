package com.ninabit.bono.modules.configuracion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "rol_permisos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolPermiso {

    @Id
    @Column(nullable = false, unique = true)
    private String rol;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String modulosPermitidos; // JSON string of allowed modules/paths

    private LocalDateTime updatedAt;
}
