package kz.studhub.security;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Locale;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

public class AuthResponses {
    private final SpringTemplateEngine templates;
    public AuthResponses(SpringTemplateEngine templates) { this.templates = templates; }
    public void limited(HttpServletRequest request, HttpServletResponse response, long seconds) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(seconds));
        response.setHeader("Cache-Control", "no-store");
        response.setCharacterEncoding("UTF-8");
        if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"settings.error.rateLimit\",\"retryAfter\":" + seconds + "}");
        } else {
            response.setContentType("text/html");
            var context = new Context(Locale.forLanguageTag("ru"));
            context.setVariable("base", request.getContextPath());
            context.setVariable("seconds", seconds);
            templates.process("auth-limited", context, response.getWriter());
        }
    }
}
