package kz.studhub.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

public class LoginDetails extends WebAuthenticationDetails {
    private final String code;
    public LoginDetails(HttpServletRequest request) {
        super(request);
        String input = request.getParameter("code");
        code = input == null || input.length() > 32 ? "" : input.trim();
    }
    public String getCode() { return code; }
}
