package com.classchat.repository;

import com.classchat.model.UserAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    Optional<UserAccount> findByHandleIgnoreCase(String handle);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByHandleIgnoreCase(String handle);

    List<UserAccount> findTop10ByHandleContainingIgnoreCaseAndIdNotOrderByHandleAsc(String handle, Long id);
}
