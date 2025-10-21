package it.floro.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; // ← AGGIUNGI

@SpringBootApplication
@EnableScheduling // ← AGGIUNGI QUESTA RIGA
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}