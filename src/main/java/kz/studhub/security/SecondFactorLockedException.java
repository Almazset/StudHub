package kz.studhub.security;

import org.springframework.security.core.AuthenticationException;

public class SecondFactorLockedException extends AuthenticationException {
    private final long retryAfter;
    public SecondFactorLockedException(long retryAfter) { super("Authentication temporarily limited"); this.retryAfter = retryAfter; }
    public long getRetryAfter() { return retryAfter; }
}
