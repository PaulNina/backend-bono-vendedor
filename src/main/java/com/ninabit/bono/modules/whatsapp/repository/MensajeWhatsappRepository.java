package com.ninabit.bono.modules.whatsapp.repository;

import com.ninabit.bono.modules.whatsapp.entity.MensajeWhatsapp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MensajeWhatsappRepository extends JpaRepository<MensajeWhatsapp, Long> {

    /** Full conversation with a contact number (sent or received) */
    @Query("SELECT m FROM MensajeWhatsapp m WHERE m.fromNumber = :numero OR m.toNumber = :numero ORDER BY m.createdAt ASC")
    List<MensajeWhatsapp> findConversacion(@Param("numero") String numero);

    /** All unique contact numbers that have at least one message */
    @Query(value = """
            SELECT DISTINCT CASE
                WHEN m.direccion = 'SALIDA' THEN m.to_number
                ELSE m.from_number
            END AS contacto
            FROM mensajes_whatsapp m
            WHERE (m.direccion = 'SALIDA' AND m.to_number IS NOT NULL AND m.to_number != '')
               OR (m.direccion = 'ENTRADA' AND m.from_number IS NOT NULL AND m.from_number != '')
            ORDER BY contacto
            """, nativeQuery = true)
    List<String> findContactNumbers();

    /** Last message in a conversation with a specific contact */
    @Query("SELECT m FROM MensajeWhatsapp m WHERE (m.fromNumber = :numero OR m.toNumber = :numero) ORDER BY m.createdAt DESC LIMIT 1")
    Optional<MensajeWhatsapp> findLastMessageWithContact(@Param("numero") String numero);

    /** Count of unread ENTRADA messages from a contact */
    @Query("SELECT COUNT(m) FROM MensajeWhatsapp m WHERE m.fromNumber = :numero AND m.direccion = com.ninabit.bono.modules.whatsapp.entity.MensajeWhatsapp.DireccionMensaje.ENTRADA AND m.estado = com.ninabit.bono.modules.whatsapp.entity.MensajeWhatsapp.EstadoMensaje.RECIBIDO")
    long countUnread(@Param("numero") String numero);

    /** Check for duplicate by Meta message ID */
    boolean existsByWaMessageId(String waMessageId);

    /** Find single message by Meta message ID */
    Optional<MensajeWhatsapp> findByWaMessageId(String waMessageId);

    /**
     * Verifica si el número envió al menos un mensaje ENTRADA después de la fecha indicada.
     * Se usa para saber si la ventana de sesión de 24h de WhatsApp está activa
     * (el cliente escribió primero y sigue dentro de la ventana de conversación abierta).
     */
    @Query("SELECT COUNT(m) > 0 FROM MensajeWhatsapp m WHERE m.fromNumber = :numero AND m.direccion = com.ninabit.bono.modules.whatsapp.entity.MensajeWhatsapp.DireccionMensaje.ENTRADA AND m.createdAt >= :desde")
    boolean existsMensajeEntradaDespuesDe(@Param("numero") String numero, @Param("desde") LocalDateTime desde);

    /** Mensajes entrantes nuevos posteriores a un ID específico (para polling y notificaciones en vivo) */
    @Query("SELECT m FROM MensajeWhatsapp m WHERE m.direccion = com.ninabit.bono.modules.whatsapp.entity.MensajeWhatsapp.DireccionMensaje.ENTRADA AND m.id > :desdeId ORDER BY m.id ASC")
    List<MensajeWhatsapp> findNuevosEntrantes(@Param("desdeId") Long desdeId);

    /** ID máximo actual en la tabla */
    @Query("SELECT COALESCE(MAX(m.id), 0L) FROM MensajeWhatsapp m")
    Long findMaxId();

    /** Conteo total de mensajes entrantes no leídos en todo el sistema */
    @Query("SELECT COUNT(m) FROM MensajeWhatsapp m WHERE m.direccion = com.ninabit.bono.modules.whatsapp.entity.MensajeWhatsapp.DireccionMensaje.ENTRADA AND m.estado = com.ninabit.bono.modules.whatsapp.entity.MensajeWhatsapp.EstadoMensaje.RECIBIDO")
    long countTotalUnread();

    /** Marcar todos los mensajes entrantes no leídos de un número como LEIDO */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE MensajeWhatsapp m SET m.estado = com.ninabit.bono.modules.whatsapp.entity.MensajeWhatsapp.EstadoMensaje.LEIDO WHERE m.fromNumber = :numero AND m.direccion = com.ninabit.bono.modules.whatsapp.entity.MensajeWhatsapp.DireccionMensaje.ENTRADA AND m.estado = com.ninabit.bono.modules.whatsapp.entity.MensajeWhatsapp.EstadoMensaje.RECIBIDO")
    int marcarMensajesComoLeidos(@Param("numero") String numero);

    /** Busca el último nombre de contacto válido que se haya registrado en cualquier mensaje */
    @Query("SELECT m.nombreContacto FROM MensajeWhatsapp m WHERE (m.fromNumber = :numero OR m.toNumber = :numero) AND m.nombreContacto IS NOT NULL AND m.nombreContacto != '' ORDER BY m.id DESC LIMIT 1")
    Optional<String> findUltimoNombreContacto(@Param("numero") String numero);

    /** Actualiza el nombre de contacto en todos los mensajes asociados al número */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE MensajeWhatsapp m SET m.nombreContacto = :nuevoNombre WHERE m.fromNumber = :numero OR m.toNumber = :numero")
    int actualizarNombreContacto(@Param("numero") String numero, @Param("nuevoNombre") String nuevoNombre);
}
