package kz.studhub.web;

import kz.studhub.model.UserAccount;
import kz.studhub.repository.UserAccountRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class ProfilePageController {
    private final UserAccountRepository users;

    public ProfilePageController(UserAccountRepository users) {
        this.users = users;
    }

    @GetMapping("/profile")
    public String profile(@ModelAttribute("currentUser") UserAccount currentUser, Model model) {
        ProfileForm form = new ProfileForm();
        form.setFullName(currentUser.getFullName());
        model.addAttribute("profileForm", form);
        return "redirect:/settings#profile";
    }

    @PostMapping("/profile")
    public String update(@Valid @ModelAttribute("profileForm") ProfileForm form,
                         BindingResult result,
                         Authentication authentication,
                         Model model) {
        if (result.hasErrors()) return "profile";

        if (users.updateFullName(authentication.getName(), form.getFullName().trim()) == 0) {
            throw new IllegalStateException("Authenticated account was not found");
        }
        return "redirect:/settings#profile";
    }

    public static class ProfileForm {
        @NotBlank @Size(min = 2, max = 80)
        private String fullName;

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
    }
}
