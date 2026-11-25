package com.classchat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpVerificationEmailSender implements VerificationEmailSender, PasswordResetEmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpVerificationEmailSender.class);

    private final JavaMailSender mailSender;
    private final String host;
    private final String from;

    public SmtpVerificationEmailSender(JavaMailSender mailSender,
                                       @Value("${classchat.mail.host:}") String host,
                                       @Value("${classchat.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.host = host;
        this.from = from;
    }

    @Override
    public void sendVerificationCode(String email, String code) {
        sendCode(email, "Your ClassChat verification code",
                "Your ClassChat verification code is " + code);
    }

    @Override
    public void sendPasswordResetCode(String email, String code) {
        sendCode(email, "Your ClassChat password reset code",
                "Your ClassChat password reset code is " + code);
    }

    private void sendCode(String email, String subject, String codeMessage) {
        if (host.isBlank() || from.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Email delivery is not configured. Set MAIL_HOST and MAIL_FROM.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(subject);
        message.setText(codeMessage
                + ". It expires in 10 minutes. If you did not request this, you can ignore this email.");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.warn("SMTP could not send an account email: {}",
                    exception.getClass().getSimpleName());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not send the email. Check the SMTP settings and try again.");
        }
    }
}
