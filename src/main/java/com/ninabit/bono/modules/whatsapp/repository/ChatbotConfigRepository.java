package com.ninabit.bono.modules.whatsapp.repository;

import com.ninabit.bono.modules.whatsapp.entity.ChatbotConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatbotConfigRepository extends JpaRepository<ChatbotConfig, Long> {
    Optional<ChatbotConfig> findFirstByOrderByIdAsc();
}
