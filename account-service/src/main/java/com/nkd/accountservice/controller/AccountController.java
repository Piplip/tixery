package com.nkd.accountservice.controller;

import com.nkd.accountservice.domain.AccountDTO;
import com.nkd.accountservice.domain.Response;
import com.nkd.accountservice.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/sign-up")
    public Response signUp(@RequestBody AccountDTO accountDTO){
        return accountService.handleSignUp(accountDTO);
    }

    @PostMapping("/activate")
    public Response activeAccount(@RequestParam("uid") String accountID, @RequestParam("confirmid") String confirmationID, @RequestParam("token") String token){
        return accountService.activateAccount(accountID, confirmationID, token);
    }

    @PostMapping("/login")
    public Response login(@RequestBody AccountDTO accountDTO, HttpServletRequest request){
        return accountService.handleLogin(accountDTO, request);
    }

    @GetMapping("/logout")
    public Response logout(HttpServletRequest request){
        return accountService.handleLogout(request);
    }

    @GetMapping("/protected")
    public Response protectedResource(HttpServletRequest request){
        return new Response("Protected Resource", "You are authorized to access this resource", null);
    }
}
