package com.ninabit.bono.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxSizeException(MaxUploadSizeExceededException exc) {
        log.warn("Exceso de tamaño en subida de archivos: {}", exc.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error",
                        "Las fotos adjuntas son demasiado pesadas. Intenta subir fotos de menor tamaño o reduce la calidad en la cámara. (Límite: 30MB)"));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<?> handleBadCredentialsException(BadCredentialsException exc) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Correo electrónico o contraseña incorrectos. Verifica tus datos de acceso."));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<?> handleDisabledException(DisabledException exc) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Tu cuenta se encuentra desactivada. Por favor, comunícate con tu supervisor o administrador."));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException exc) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "No tienes permisos para realizar esta acción."));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrityException(org.springframework.dao.DataIntegrityViolationException exc) {
        log.warn("Violación de integridad de datos: {}", exc.getMessage());
        String msg = exc.getMessage() != null ? exc.getMessage().toLowerCase() : "";
        if (msg.contains("unique") || msg.contains("duplicate") || msg.contains("uk") || msg.contains("ya existe")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Ya existe un registro con estos datos en el sistema (posible duplicado de serial, código o correo)."));
        }
        if (msg.contains("check constraint") || msg.contains("check")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El valor o estado enviado no cumple con las restricciones permitidas por el sistema."));
        }
        if (msg.contains("foreign key") || msg.contains("violates foreign key")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No se puede completar la operación porque el registro tiene dependencias activas (tickets, pagos o movimientos vinculados)."));
        }
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Error de integridad de datos al procesar la solicitud."));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException exc) {
        log.error("Excepción Runtime capturada:", exc);
        String msg = exc.getMessage();

        // Si el mensaje es nulo o contiene jerga técnica de frameworks / bases de datos, humanizarlo
        if (msg == null || msg.isBlank()
                || msg.contains("could not initialize proxy")
                || msg.contains("LazyInitializationException")
                || msg.contains("NullPointerException")
                || msg.contains("could not execute statement")
                || msg.contains("JDBC")
                || msg.contains("SQLException")
                || msg.contains("Hibernate")
                || msg.contains("TransactionSystemException")
                || msg.contains("org.hibernate")
                || msg.contains("ConstraintViolationException")) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error",
                            "Ocurrió un inconveniente momentáneo en el servidor al procesar la solicitud. Por favor, intenta nuevamente en unos momentos o consulta con tu supervisor."));
        }

        // Si es un mensaje de negocio claro y legible, retornarlo directamente en 400 Bad Request
        return ResponseEntity.badRequest()
                .body(Map.of("error", msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneralException(Exception exc) {
        log.error("Error no controlado:", exc);
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "Ocurrió un problema al procesar tu solicitud. Por favor, intenta de nuevo en unos momentos."));
    }
}
