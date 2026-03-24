package com.ninabit.bono.modules.auth.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String nombreCompleto;
    private String email;
    private String password;
    private String telefono;
    private String ciudad;
    private Long tiendaId;
}
