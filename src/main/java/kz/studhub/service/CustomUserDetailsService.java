package kz.studhub.service;

import kz.studhub.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserAccountRepository users;
    public CustomUserDetailsService(UserAccountRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var account = users.findByEmailIgnoreCase(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return User.withUsername(account.getEmail())
            .password(account.getPassword())
            .roles(account.getRole().name())
            .build();
    }
}
