package com.ninabit.bono.modules.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final AuditoriaRepository auditoriaRepository;

    public void log(String email, String accion, String descripcion, String entidad, String entidadId) {
        Auditoria auditoria = Auditoria.builder()
                .usuarioEmail(email)
                .accion(accion)
                .descripcion(descripcion)
                .entidad(entidad)
                .entidadId(entidadId)
                .build();
        auditoriaRepository.save(auditoria);
    }
}
