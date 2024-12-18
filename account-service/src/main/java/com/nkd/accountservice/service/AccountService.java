package com.nkd.accountservice.service;

import com.nkd.accountservice.domain.AccountDTO;
import com.nkd.accountservice.domain.Response;

public interface AccountService {
    Response handleSignUp(AccountDTO accountDTO);
}