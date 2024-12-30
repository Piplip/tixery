package com.nkd.accountservice.controller;

import com.nkd.accountservice.domain.AccountDTO;
import com.nkd.accountservice.domain.Profile;
import com.nkd.accountservice.domain.Response;
import com.nkd.accountservice.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/sign-up")
    public Response signUp(@RequestBody AccountDTO accountDTO){
        return accountService.handleSignUp(accountDTO);
    }

    @GetMapping("/activate")
    public ResponseEntity<?> activeAccount(@RequestParam("uid") String accountID, @RequestParam("confirmid") String confirmationID, @RequestParam("token") String token){
        Response response = accountService.activateAccount(accountID, confirmationID, token);

        if(response.status().equals(HttpStatus.OK.name())){
            String redirectURL = "http://localhost:5173/accounts/verify/success" + "?uid=" + accountID;
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(java.net.URI.create(redirectURL))
                    .build();
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(java.net.URI.create("http://localhost:5173/accounts/verify/failed" + "?uid=" + accountID + "&error=" + response.message()))
                .build();
    }

    @PutMapping("/resend-activation")
    public Response resendActivation(@RequestParam("uid") String accountID){
        return accountService.resendActivation(accountID);
    }

    @PostMapping("/login")
    public Response login(@RequestBody AccountDTO accountDTO, HttpServletRequest request){
        return accountService.handleLogin(accountDTO, request);
    }

    @GetMapping("/logout")
    public Response logout(HttpServletRequest request){
        return accountService.handleLogout(request);
    }

    @GetMapping("/check-email")
    public Response checkEmailExists(@RequestParam("email") String email){
        return accountService.checkEmailExists(email);
    }

    @PostMapping("/profile/setup")
    public Response createSetUpProfileRequest(@RequestParam("uid") String accountID){
        return accountService.createSetUpProfileRequest(accountID);
    }

    @PostMapping("/profile/create")
    public Response handleCreateProfile(@RequestParam("rid") String requestID, @RequestBody Profile profile, @RequestParam("type") String role){
        System.out.println("Profile: " + profile.toString());
        return accountService.handleCreateProfile(requestID, profile, role);
    }

    @PostMapping("/profile/oauth/create")
    public Response handleCreateOauth2Profile(@RequestParam("email") String email, @RequestBody Profile profile, @RequestParam("type") String role){
        return accountService.handleCreateOAuth2Profile(email, profile, role);
    }

    @GetMapping("/profile/oauth/update")
    public Response getNewDataToken(@RequestParam("for") String email){
        return accountService.getUpdatedToken(email);
    }

    @GetMapping("/protected")
    public String protectedEndpoint(){
        return "This is a protected endpoint";
    }
}
