package com.classchat.service;

import com.classchat.model.UserAccount;
import com.classchat.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserAccountRepository userAccounts;

    public CurrentUserService(UserAccountRepository userAccounts) {
        this.userAccounts = userAccounts;
    }

    public UserAccount requireByHandle(String handle) {
        return userAccounts.findByHandleIgnoreCase(handle)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Your account could not be found."));
    }
}
