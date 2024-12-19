package com.nkd.accountservice.service;

import com.nkd.accountservice.domain.AccountDTO;
import com.nkd.accountservice.domain.Response;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

public interface AccountService {
    Response handleSignUp(AccountDTO accountDTO);
    Response activateAccount(String accountID, String confirmationID, String token);
    Response handleLogin(AccountDTO accountDTO, HttpServletRequest request);
    Response handleLogout(HttpServletRequest request);
}