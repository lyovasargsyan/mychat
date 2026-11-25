package com.classchat.service;

import com.classchat.dto.RegisterRequest;
import com.classchat.model.UserAccount;
import com.classchat.repository.UserAccountRepository;
import java.time.Clock;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final UserAccountRepository userAccounts;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountService(UserAccountRepository userAccounts, PasswordEncoder passwordEncoder, Clock clock) {
        this.userAccounts = userAccounts;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public UserAccount register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String handle = normalizeHandle(request.handle());

        if (userAccounts.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "That email is already registered.");
        }
        if (userAccounts.existsByHandleIgnoreCase(handle)) {
            throw new ApiException(HttpStatus.CONFLICT, "That username is already taken.");
        }

        return userAccounts.save(new UserAccount(
                email, handle, passwordEncoder.encode(request.password()), clock.instant()));
    }

    public static String normalizeHandle(String handle) {
        String normalized = handle.trim();
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
