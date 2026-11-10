package kz.studhub.web;

import kz.studhub.model.UserAccount;
import kz.studhub.service.RegistrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {
    private final RegistrationService registration;
    public AuthController(RegistrationService registration) { this.registration = registration; }

    @GetMapping("/login") public String login() { return "login"; }

    @GetMapping("/register")
    public String registerForm(Model model) { model.addAttribute("form", new RegistrationForm()); return "register"; }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form, BindingResult result, Model model) {
        if (form.getPassword() != null && !form.getPassword().equals(form.getConfirmPassword())) {
            result.rejectValue("password", "auth.passwordMismatch");
        }
        if (result.hasErrors()) return "register";
        try {
            registration.register(form.getFullName(), form.getEmail(), form.getPassword());
            return "redirect:/login?registered";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("registrationError", ex.getMessage());
            return "register";
        }
    }

    public static class RegistrationForm {
        @NotBlank @Size(min=2,max=80) private String fullName;
        @NotBlank @Email @Size(max=120) private String email;
        @NotBlank @Size(min=8,max=100) private String password;
        private String confirmPassword;
        public String getFullName(){return fullName;} public void setFullName(String v){fullName=v;}
        public String getEmail(){return email;} public void setEmail(String v){email=v;}
        public String getPassword(){return password;} public void setPassword(String v){password=v;}
        public String getConfirmPassword(){return confirmPassword;} public void setConfirmPassword(String v){confirmPassword=v;}
    }
}
