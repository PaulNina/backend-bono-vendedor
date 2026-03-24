package com.ninabit.bono.modules.configuracion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "configuracion_global")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracionGlobal {

    @Id
    @Column(nullable = false, unique = true, length = 100)
    private String clave;

    @Column(nullable = false, length = 1000)
    private String valor;

    @Column(length = 255)
    private String descripcion;
}
