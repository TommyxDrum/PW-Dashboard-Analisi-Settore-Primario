package it.floro.dashboard.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MeteoController {

    @GetMapping("/meteo")
    public String meteo() {
        return "meteo";
    }
}