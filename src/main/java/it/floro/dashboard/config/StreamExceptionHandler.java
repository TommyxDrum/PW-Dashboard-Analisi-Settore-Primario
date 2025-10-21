package it.floro.dashboard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

/**
 * Global exception handler per catturare errori di streaming SSE
 * e prevenire spam di log con IllegalStateException
 */
@Component
@RestControllerAdvice
@Order(-1) // Alta priorità
public class StreamExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(StreamExceptionHandler.class);

    /**
     * Gestisce AsyncRequestNotUsableException quando il client si disconnette
     * Questo previene gli IllegalStateException nei log
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    @ResponseStatus(HttpStatus.OK)
    public void handleAsyncNotUsable(AsyncRequestNotUsableException ex) {
        // Log a livello DEBUG invece di WARN per ridurre il rumore nei log
        logger.debug("Client disconnesso durante stream SSE: {}", ex.getMessage());
        // Non propagare l'eccezione
    }

    /**
     * Gestisce IOException quando la connessione viene interrotta
     */
    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.OK)
    public void handleIOException(IOException ex) {
        if (ex.getMessage() != null &&
                (ex.getMessage().contains("Connessione interrotta") ||
                        ex.getMessage().contains("Connection reset") ||
                        ex.getMessage().contains("Broken pipe"))) {
            // Client disconnesso - log silenzioso
            logger.debug("Connessione client interrotta: {}", ex.getMessage());
        } else {
            // Altro tipo di IOException - log normale
            logger.warn("IOException durante streaming: {}", ex.getMessage());
        }
    }

    /**
     * Gestisce IllegalStateException specifico per AsyncContext
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.OK)
    public void handleIllegalState(IllegalStateException ex) {
        if (ex.getMessage() != null &&
                ex.getMessage().contains("AsyncContext")) {
            // Errore AsyncContext - log debug
            logger.debug("AsyncContext non più utilizzabile (client disconnesso): {}", ex.getMessage());
        } else {
            // Altri IllegalStateException - log normale
            logger.error("IllegalStateException: {}", ex.getMessage(), ex);
        }
    }
}