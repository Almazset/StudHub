package kz.studhub.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** Registered only inside Spring Security, after CSRF and before credential verification. */
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> SECURITY_PATHS = Set.of("/api/settings/password", "/api/settings/profile",
            "/api/settings/two-factor", "/api/settings/two-factor/start", "/api/settings/two-factor/enable");
    private final AuthRateLimiter limiter;
    private final AuthResponses responses;
    public AuthRateLimitFilter(AuthRateLimiter limiter, AuthResponses responses) {
        this.limiter = limiter; this.responses = responses;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        // Mock requests may omit servletPath; real requests use it (also works under a context path).
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        AuthRateLimiter.Action action = null;
        String account = null;
        if ("POST".equals(request.getMethod()) && (path.equals("/login") || path.equals("/register"))) {
            action = path.equals("/login") ? AuthRateLimiter.Action.LOGIN : AuthRateLimiter.Action.REGISTER;
            account = request.getParameter(path.equals("/login") ? "username" : "email");
        } else if (Set.of("POST", "PUT", "DELETE").contains(request.getMethod()) && SECURITY_PATHS.contains(path)) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                action = AuthRateLimiter.Action.SECURITY; account = auth.getName();
            }
        }
        if (action != null) {
            // Forwarded headers are untrusted in the current direct/local deployment.
            long retry = limiter.acquire(action, request.getRemoteAddr(), account);
            if (retry > 0) { responses.limited(request, response, retry); return; }
        }
        chain.doFilter(request, response);
    }
}
