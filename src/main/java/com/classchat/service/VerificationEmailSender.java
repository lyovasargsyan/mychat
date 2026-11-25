package com.classchat.service;

public interface VerificationEmailSender {

    void sendVerificationCode(String email, String code);
}
