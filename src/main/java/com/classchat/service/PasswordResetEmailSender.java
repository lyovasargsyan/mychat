package com.classchat.service;

public interface PasswordResetEmailSender {

    void sendPasswordResetCode(String email, String code);
}
