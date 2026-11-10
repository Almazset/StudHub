package kz.studhub.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetails;

public class TwoFactorAuthenticationProvider extends DaoAuthenticationProvider {
    private final TwoFactorService twoFactor;
    public TwoFactorAuthenticationProvider(TwoFactorService twoFactor) { this.twoFactor = twoFactor; }

    @Override
    protected void additionalAuthenticationChecks(UserDetails user, UsernamePasswordAuthenticationToken authentication) {
        super.additionalAuthenticationChecks(user, authentication);
        String code = authentication.getDetails() instanceof LoginDetails details ? details.getCode() : "";
        if (!twoFactor.verifyLogin(user.getUsername(), code)) throw new BadCredentialsException("Invalid credentials");
    }
}
