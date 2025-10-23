package it.floro.dashboard.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller che espone il dashboard meteo in tempo reale.
 *
 * Responsabilità:
 * - Mappare la richiesta HTTP GET /meteo alla view Thymeleaf
 * - Rendere la pagina HTML della dashboard meteorologica
 *
 * Pattern: Simple View Controller
 * - Non calcola KPI né applica filtri (non estende BaseKpiController)
 * - Semplice mapping request → view
 * - I dati meteo vengono caricati dinamicamente via API REST (JavaScript)
 *
 * Architettura:
 * - View stateless: template HTML puro
 * - Data loading: delegato a API REST (es. /api/weather via fetch/AJAX)
 * - Refresh automatico: JavaScript aggiorna i dati ogni 10 secondi
 *
 * Stack tecnologico per la view:
 * - Template Thymeleaf: templates/meteo.html
 * - JavaScript: fetch API per chiamate asincrone a WeatherService
 * - Chart.js: per visualizzazione dati meteo (se presenti grafici)
 * - CSS: styling responsive per card meteo aree
 *
 * Dati visualizzati (forniti da WeatherService):
 * - Temperatura per area (Nord, Centro, Sud)
 * - Umidità relativa (%)
 * - Precipitazioni (mm)
 * - Velocità del vento (km/h)
 * - Radiazione solare (W/m²)
 * - Condizioni meteo (Soleggiato, Nuvoloso, Pioggia)
 * - Timestamp di aggiornamento
 *
 * Flusso di caricamento dati:
 * 1. Browser richiesta GET /meteo
 * 2. Spring rendering template Thymeleaf
 * 3. Template carica pagina HTML (contiene placeholder per dati)
 * 4. JavaScript (nel template) esegue fetch() a endpoint API
 * 5. Endpoint API ritorna JSON con dati meteo correnti
 * 6. JavaScript popola il DOM con i dati ricevuti
 * 7. Repeat ogni 10 secondi (refresh automatico)
 *
 * Possibili estensioni future:
 * - Aggiungere Model parameter per pre-popolare dati sul server
 * - Integrare API meteo esterna (OpenWeatherMap, Meteo.it)
 * - Aggiungere grafici storici (serie temporale meteo)
 * - Aggiungere alert meteo (temperature estreme, gelate)
 * - Aggiungere previsioni meteo (forecast prossimi 7 giorni)
 *
 * Note sulla semplicità:
 * - Questo controller è volutamente minimalista
 * - Complessi siano delegati alla view e alle API REST
 * - Separazione delle responsabilità: MVC vs API REST
 * - Frontend carica dati dinamicamente (decoupling da backend)
 *
 * Sicurezza:
 * - No input parameters: nessun rischio di injection
 * - No model data: nessun XSS risk da dati server
 * - CORS se API in dominio diverso: configurare esplicitamente
 */
@Controller
public class MeteoController {

    // ========================================================================
    // ENDPOINT HTTP - DASHBOARD METEO
    // ========================================================================

    /**
     * Endpoint GET /meteo: visualizza la dashboard meteorologica in tempo reale.
     *
     * Metodo HTTP: GET
     * Mapping: /meteo
     * Risposta: HTML renderizzato (Thymeleaf template "meteo.html")
     * Content-Type: text/html; charset=UTF-8
     *
     * Parametri query: NESSUNO
     * - Questo endpoint non accetta parametri (diversamente da KPI controller)
     * - I dati meteo sono sempre attuali e globali (non filtrabili)
     * - Eventuali filtri sarebbero gestiti lato client (JavaScript)
     *
     * Esempio URL:
     * GET /meteo → visualizza dashboard meteo
     *
     * Flusso di esecuzione:
     *
     * STEP 1: RICHIESTA HTTP
     * - Browser richiesta GET /meteo
     * - Spring riceve richiesta e mappa a questo metodo
     *
     * STEP 2: RENDERING TEMPLATE
     * - Spring risolve il nome "meteo" come template Thymeleaf
     * - Carica file templates/meteo.html
     * - Rendering (nessun model data da processare in questo caso)
     * - Invia HTML al browser
     *
     * STEP 3: ESECUZIONE LATO CLIENT
     * - Browser parsing HTML + CSS + JavaScript
     * - JavaScript (nel template) esegue subito:
     *   a) Fetch GET /api/weather (o endpoint API meteo)
     *   b) Riceve JSON con dati meteo correnti
     *   c) Popola il DOM con i dati
     *   d) Configura refresh automatico ogni 10 secondi
     *
     * STEP 4: AGGIORNAMENTI PERIODICI
     * - Ogni 10 secondi, JavaScript fa fetch() a /api/weather
     * - Aggiorna solo i dati che cambiano (temperatura, condizioni)
     * - Non ricarica tutta la pagina (SPA-like behavior)
     *
     * Note sull'architettura:
     * - Questo controller è un semplice "view controller"
     * - Non contiene logica di business (tutto in Service layer)
     * - Non contiene logica di presentazione (tutto in Template)
     * - Semplicemente mappa richiesta HTTP a template
     *
     * Contrasto con KPI controller:
     * - KPI controller: calcola dati server-side, popola Model, passa a view
     * - Meteo controller: view self-service, carica dati via API client-side
     *
     * Vantaggi dell'approccio client-side:
     * - Refresh automatico senza ricarica pagina
     * - Migliore UX (meno flickering)
     * - Decoupling fra template e backend
     * - Possibilità di mockare API in test
     *
     * Possibili miglioramenti:
     * - Aggiungere Model parameter per server-side rendering iniziale
     *   @GetMapping("/meteo")
     *   public String meteo(Model model) {
     *       model.addAttribute("weatherData", weatherService.getCurrentWeather());
     *       return "meteo";
     *   }
     *
     * - Aggiungere error handling per fetch API failure
     * - Aggiungere spinner/loading indicator durante fetch
     * - Aggiungere fallback data se API non disponibile
     *
     * @return Nome della view Thymeleaf ("meteo")
     *         Spring lo risolve come templates/meteo.html
     */
    @GetMapping("/meteo")
    public String meteo() {
        return "meteo";
    }
}