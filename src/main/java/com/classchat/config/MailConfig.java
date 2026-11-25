package com.classchat.config;

import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailConfig {

    @Bean
    JavaMailSender javaMailSender(
            @Value("${classchat.mail.host:}") String host,
            @Value("${classchat.mail.port:587}") int port,
            @Value("${classchat.mail.username:}") String username,
            @Value("${classchat.mail.password:}") String password,
            @Value("${classchat.mail.smtp-auth:true}") boolean smtpAuth,
            @Value("${classchat.mail.starttls:true}") boolean startTls,
            @Value("${classchat.mail.connection-timeout:5000}") int connectionTimeout,
            @Value("${classchat.mail.read-timeout:5000}") int readTimeout,
            @Value("${classchat.mail.write-timeout:5000}") int writeTimeout) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        if (!username.isBlank()) {
            sender.setUsername(username);
            sender.setPassword(password);
        }
        Properties mailProperties = sender.getJavaMailProperties();
        mailProperties.put("mail.smtp.auth", smtpAuth);
        mailProperties.put("mail.smtp.starttls.enable", startTls);
        mailProperties.put("mail.smtp.starttls.required", startTls);
        mailProperties.put("mail.smtp.connectiontimeout", connectionTimeout);
        mailProperties.put("mail.smtp.timeout", readTimeout);
        mailProperties.put("mail.smtp.writetimeout", writeTimeout);
        return sender;
    }
}
