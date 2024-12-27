package com.nkd.accountservice.service;

import com.nkd.accountservice.domain.AccountDTO;
import com.nkd.accountservice.domain.Profile;
import com.nkd.accountservice.domain.Response;
import jakarta.servlet.http.HttpServletRequest;

public interface AccountService {
    Response handleSignUp(AccountDTO accountDTO);
    Response activateAccount(String accountID, String confirmationID, String token);
    Response handleLogin(AccountDTO accountDTO, HttpServletRequest request);
    Response handleLogout(HttpServletRequest request);
    Response checkEmailExists(String email);
    Response resendActivation(String accountID);
    Response createSetUpProfileRequest(String accountID);
    Response handleCreateProfile(String requestID, Profile profile);
}