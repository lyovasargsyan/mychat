package com.classchat.repository;

import com.classchat.model.EmailVerificationChallenge;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationChallengeRepository extends JpaRepository<EmailVerificationChallenge, Long> {

    Optional<EmailVerificationChallenge> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
