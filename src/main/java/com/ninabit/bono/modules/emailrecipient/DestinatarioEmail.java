package com.ninabit.bono.modules.emailrecipient;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "destinatarios_email")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DestinatarioEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = true)
    private String ciudad;

    @Column(name = "grupo_id")
    private Long grupoId;

    @Column(name = "campana_id")
    private Long campanaId;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
