package com.ninabit.bono.modules.whatsapp.repository;

import com.ninabit.bono.modules.whatsapp.entity.WhatsAppRegistroSesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WhatsAppRegistroSesionRepository extends JpaRepository<WhatsAppRegistroSesion, Long> {
    Optional<WhatsAppRegistroSesion> findByTelefono(String telefono);
    void deleteByTelefono(String telefono);
}
