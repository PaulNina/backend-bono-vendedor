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
        log.error("Exceso de tamaño en subida de archivos: {}", exc.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Los archivos adjuntos son demasiado grandes. El límite total por venta es de 30MB."));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<?> handleBadCredentialsException(BadCredentialsException exc) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Correo electrónico o contraseña incorrectos."));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<?> handleDisabledException(DisabledException exc) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Su cuenta está desactivada. Por favor, contacte con el administrador."));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException exc) {
        log.error("Error inesperado (Runtime):", exc);
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "Error interno del servidor: " + exc.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneralException(Exception exc) {
        log.error("Error crítico general:", exc);
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "Ocurrió un error inesperado al procesar su solicitud."));
    }
}
