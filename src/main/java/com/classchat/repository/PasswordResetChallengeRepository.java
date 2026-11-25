package com.classchat.repository;

import com.classchat.model.PasswordResetChallenge;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetChallengeRepository extends JpaRepository<PasswordResetChallenge, Long> {

    Optional<PasswordResetChallenge> findByUserId(Long userId);
}
