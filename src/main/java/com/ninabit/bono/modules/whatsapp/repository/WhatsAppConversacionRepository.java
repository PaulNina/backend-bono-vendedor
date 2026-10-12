package com.ninabit.bono.modules.whatsapp.repository;

import com.ninabit.bono.modules.whatsapp.entity.WhatsAppConversacion;
import com.ninabit.bono.modules.whatsapp.entity.WhatsAppConversacion.EstadoConversacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WhatsAppConversacionRepository extends JpaRepository<WhatsAppConversacion, String> {
    List<WhatsAppConversacion> findByEstado(EstadoConversacion estado);
}
