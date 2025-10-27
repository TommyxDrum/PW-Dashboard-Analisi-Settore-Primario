package it.floro.dashboard.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String loginPage(Model model, String error, String logout) {
        if (error != null) {
            model.addAttribute("errorMsg", "Credenziali non valide. Riprova.");
        }
        if (logout != null) {
            model.addAttribute("logoutMsg", "Logout effettuato con successo.");
        }
        return "login"; // ritorna il nome della view (es: login.html in templates/)
    }

    @GetMapping("/accessDenied")
    public String accessDenied(Model model) {
        model.addAttribute("errorMsg", "Non hai i permessi per accedere a questa pagina.");
        return "accessDenied";
    }
}
