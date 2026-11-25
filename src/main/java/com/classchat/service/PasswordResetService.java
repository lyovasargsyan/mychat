package com.classchat.service;

import com.classchat.dto.PasswordResetConfirmRequest;
import com.classchat.model.PasswordResetChallenge;
import com.classchat.model.UserAccount;
import com.classchat.repository.PasswordResetChallengeRepository;
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
public class PasswordResetService {

    private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final String REQUEST_MESSAGE =
            "If a verified account exists for this address, a reset code will be emailed. "
                    + "Wait one minute before requesting another code.";

    private final UserAccountRepository userAccounts;
    private final PasswordResetChallengeRepository challenges;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetEmailSender emailSender;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(UserAccountRepository userAccounts,
                                PasswordResetChallengeRepository challenges,
                                PasswordEncoder passwordEncoder,
                                PasswordResetEmailSender emailSender,
                                Clock clock) {
        this.userAccounts = userAccounts;
        this.challenges = challenges;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.clock = clock;
    }

    @Transactional
    public String requestReset(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        userAccounts.findByEmailIgnoreCase(normalizedEmail)
                .filter(UserAccount::isEmailVerified)
                .ifPresent(account -> {
                    Instant now = clock.instant();
                    PasswordResetChallenge existing = challenges.findByUserId(account.getId()).orElse(null);
                    if (existing == null || !now.isBefore(existing.getSentAt().plus(RESEND_COOLDOWN))) {
                        issueCode(account, now);
                    }
                });
        return REQUEST_MESSAGE;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void confirmReset(PasswordResetConfirmRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        UserAccount account = userAccounts.findByEmailIgnoreCase(normalizedEmail)
                .filter(UserAccount::isEmailVerified)
                .orElseThrow(this::invalidCode);
        PasswordResetChallenge challenge = challenges.findByUserId(account.getId())
                .orElseThrow(this::invalidCode);
        Instant now = clock.instant();
        if (!now.isBefore(challenge.getExpiresAt())) {
            challenges.delete(challenge);
            throw new ApiException(HttpStatus.BAD_REQUEST, "The reset code has expired. Request a new one.");
        }
        if (challenge.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect codes. Request a new reset code.");
        }
        if (!passwordEncoder.matches(request.code(), challenge.getCodeHash())) {
            challenge.recordFailedAttempt();
            throw invalidCode();
        }

        account.updatePasswordHash(passwordEncoder.encode(request.newPassword()));
        challenges.delete(challenge);
    }

    private void issueCode(UserAccount account, Instant now) {
        String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        String codeHash = passwordEncoder.encode(code);
        PasswordResetChallenge challenge = challenges.findByUserId(account.getId()).orElse(null);
        if (challenge == null) {
            challenge = new PasswordResetChallenge(account, codeHash, now.plus(CODE_LIFETIME), now);
        } else {
            challenge.replaceCode(codeHash, now.plus(CODE_LIFETIME), now);
        }
        challenges.save(challenge);
        emailSender.sendPasswordResetCode(account.getEmail(), code);
    }

    private ApiException invalidCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, "The reset code is incorrect or unavailable.");
    }
}
