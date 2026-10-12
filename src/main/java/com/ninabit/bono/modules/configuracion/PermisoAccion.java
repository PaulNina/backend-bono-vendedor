package com.ninabit.bono.modules.configuracion;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "permisos_acciones")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermisoAccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String rol;

    @Column(nullable = false, length = 50)
    private String modulo; // VENTAS, TIENDAS, VENDEDORES, CAMPANAS, PAGOS, REPORTES, USUARIOS, EQUIPOS_DANADOS, BANDEO

    @Column(nullable = false, length = 30)
    private String accion; // VER, CREAR, EDITAR, APROBAR, PAGAR, CANJEAR, EXPORTAR, ELIMINAR

    @Column(nullable = false)
    @Builder.Default
    private boolean permitido = true;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void prePersist() {
        this.updatedAt = LocalDateTime.now();
    }
}
