package com.classchat;

import com.classchat.service.VerificationEmailSender;
import com.classchat.service.PasswordResetEmailSender;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestMailConfiguration {

    @Bean
    @Primary
    RecordingVerificationEmailSender recordingVerificationEmailSender() {
        return new RecordingVerificationEmailSender();
    }

    public static class RecordingVerificationEmailSender
            implements VerificationEmailSender, PasswordResetEmailSender {

        private final Map<String, String> codes = new ConcurrentHashMap<>();
        private final Map<String, String> resetCodes = new ConcurrentHashMap<>();

        @Override
        public void sendVerificationCode(String email, String code) {
            codes.put(email, code);
        }

        @Override
        public void sendPasswordResetCode(String email, String code) {
            resetCodes.put(email, code);
        }

        public String codeFor(String email) {
            return codes.get(email);
        }

        public String resetCodeFor(String email) {
            return resetCodes.get(email);
        }
    }
}
