package com.classchat.service;

import com.classchat.dto.RegisterRequest;
import com.classchat.dto.VerificationPendingResponse;
import com.classchat.model.EmailVerificationChallenge;
import com.classchat.model.UserAccount;
import com.classchat.repository.EmailVerificationChallengeRepository;
import com.classchat.repository.UserAccountRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

    private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final AccountService accountService;
    private final UserAccountRepository userAccounts;
    private final EmailVerificationChallengeRepository challenges;
    private final PasswordEncoder passwordEncoder;
    private final VerificationEmailSender emailSender;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificationService(AccountService accountService,
                                    UserAccountRepository userAccounts,
                                    EmailVerificationChallengeRepository challenges,
                                    PasswordEncoder passwordEncoder,
                                    VerificationEmailSender emailSender,
                                    Clock clock) {
        this.accountService = accountService;
        this.userAccounts = userAccounts;
        this.challenges = challenges;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.clock = clock;
    }

    @Transactional
    public VerificationPendingResponse startRegistration(RegisterRequest request) {
        UserAccount account = accountService.register(request);
        issueCode(account, clock.instant());
        return pendingResponse(account.getEmail());
    }

    @Transactional
    public VerificationPendingResponse resend(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        userAccounts.findByEmailIgnoreCase(normalizedEmail)
                .filter(account -> !account.isEmailVerified())
                .ifPresent(account -> {
                    Instant now = clock.instant();
                    EmailVerificationChallenge existing = challenges.findByUserId(account.getId()).orElse(null);
                    if (existing == null || !now.isBefore(existing.getSentAt().plus(RESEND_COOLDOWN))) {
                        issueCode(account, now);
                    }
                });
        return pendingResponse(normalizedEmail);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void verify(String email, String code) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        UserAccount account = userAccounts.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> invalidCode());
        if (account.isEmailVerified()) {
            return;
        }

        EmailVerificationChallenge challenge = challenges.findByUserId(account.getId())
                .orElseThrow(() -> invalidCode());
        Instant now = clock.instant();
        if (!now.isBefore(challenge.getExpiresAt())) {
            challenges.delete(challenge);
            throw new ApiException(HttpStatus.BAD_REQUEST, "The code has expired. Request a new one.");
        }
        if (challenge.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect codes. Request a new verification code.");
        }
        if (!passwordEncoder.matches(code, challenge.getCodeHash())) {
            challenge.recordFailedAttempt();
            throw invalidCode();
        }

        account.markEmailVerified();
        challenges.delete(challenge);
    }

    private void issueCode(UserAccount account, Instant now) {
        String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        String codeHash = passwordEncoder.encode(code);
        EmailVerificationChallenge challenge = challenges.findByUserId(account.getId()).orElse(null);
        if (challenge == null) {
            challenge = new EmailVerificationChallenge(account, codeHash, now.plus(CODE_LIFETIME), now);
        } else {
            challenge.replaceCode(codeHash, now.plus(CODE_LIFETIME), now);
        }
        challenges.save(challenge);
        emailSender.sendVerificationCode(account.getEmail(), code);
    }

    private VerificationPendingResponse pendingResponse(String email) {
        return new VerificationPendingResponse(email,
                "If the address needs verification, a code has been emailed.");
    }

    private ApiException invalidCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, "The verification code is incorrect or unavailable.");
    }
}
