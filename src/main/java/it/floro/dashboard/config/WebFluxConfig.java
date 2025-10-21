package it.floro.dashboard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Configurazione WebFlux per ottimizzare la gestione degli stream SSE
 */
@Configuration
public class WebFluxConfig implements WebFluxConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebFluxConfig.class);

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        // Configurazione codec per SSE
        configurer.defaultCodecs().enableLoggingRequestDetails(true);

        logger.info("WebFlux configurato per gestione SSE ottimizzata");
    }
}