package com.classchat.controller;

import com.classchat.dto.LoginRequest;
import com.classchat.dto.EmailVerificationRequest;
import com.classchat.dto.PasswordResetConfirmRequest;
import com.classchat.dto.PasswordResetRequest;
import com.classchat.dto.ResendVerificationRequest;
import com.classchat.dto.RegisterRequest;
import com.classchat.dto.UserResponse;
import com.classchat.model.UserAccount;
import com.classchat.repository.UserAccountRepository;
import com.classchat.service.ApiException;
import com.classchat.service.EmailVerificationService;
import com.classchat.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.web.csrf.CsrfToken;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final UserAccountRepository userAccounts;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(EmailVerificationService emailVerificationService,
                          PasswordResetService passwordResetService,
                          UserAccountRepository userAccounts,
                          AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository) {
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
        this.userAccounts = userAccounts;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of("token", csrfToken.getToken(), "headerName", csrfToken.getHeaderName());
    }

    @PostMapping("/register")
    public ResponseEntity<com.classchat.dto.VerificationPendingResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.accepted().body(emailVerificationService.startRegistration(request));
    }

    @PostMapping("/verify-email")
    public Map<String, String> verifyEmail(@Valid @RequestBody EmailVerificationRequest request) {
        emailVerificationService.verify(request.email(), request.code());
        return Map.of("message", "Email verified. Your account is ready.");
    }

    @PostMapping("/resend-verification")
    public com.classchat.dto.VerificationPendingResponse resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        return emailVerificationService.resend(request.email());
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Map<String, String>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        return ResponseEntity.accepted()
                .body(Map.of("message", passwordResetService.requestReset(request.email())));
    }

    @PostMapping("/password-reset/confirm")
    public Map<String, String> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request);
        return Map.of("message", "Your password was reset. You can now sign in.");
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest servletRequest,
                              HttpServletResponse servletResponse) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.identity().trim(), request.password()));
        } catch (DisabledException exception) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Verify your email first. Use 'Verify an existing account' to receive a code.");
        } catch (BadCredentialsException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email, username, or password is incorrect.");
        }
        saveAuthentication(authentication, servletRequest, servletResponse);
        UserAccount account = userAccounts.findByHandleIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account was not found."));
        return new UserResponse(account.getId(), account.getHandle());
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        UserAccount account = userAccounts.findByHandleIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account was not found."));
        return new UserResponse(account.getId(), account.getHandle());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    private void saveAuthentication(Authentication authentication,
                                    HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
