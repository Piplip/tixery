package com.nkd.accountservice.controller;

import com.nkd.accountservice.domain.AccountDTO;
import com.nkd.accountservice.domain.PasswordDTO;
import com.nkd.accountservice.domain.Profile;
import com.nkd.accountservice.domain.Response;
import com.nkd.accountservice.service.AccountService;
import com.nkd.accountservice.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final JwtService jwtService;

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

    @GetMapping("/info")
    public Map<String, Object> getAccountData(@RequestParam("pid") Integer profileID){
        return accountService.getAccountData(profileID);
    }

    @PutMapping("/resend-activation")
    public Response resendActivation(@RequestParam("uid") String accountID){
        return accountService.resendActivation(accountID);
    }

    @PostMapping("/login")
    public Response login(@RequestBody AccountDTO accountDTO, HttpServletRequest request, HttpServletResponse response){
        return accountService.handleLogin(accountDTO, request, response);
    }

    @GetMapping("/profile/email")
    public String getProfileEmail(@RequestParam("pid") Integer profileID){
        return accountService.getProfileEmail(profileID);
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

    @PostMapping("/forgot-password")
    public Response handleForgotPassword(@RequestParam("u") String email){
        return accountService.handleForgotPassword(email);
    }

    @PostMapping("/forgot-password/verify")
    public Response handleForgotPasswordVerification(@RequestParam("u") String email, @RequestParam("code") String code){
        return accountService.handleForgotPasswordVerification(email, code);
    }

    @PostMapping("/forgot-password/reset")
    public Response handleForgotPasswordReset(@RequestParam("u") String email, @RequestParam("password") String newPassword){
        return accountService.handleForgotPasswordReset(email, newPassword);
    }

    @GetMapping("internal/user/id")
    public Integer getUserID(@RequestParam("email") String email){
        return accountService.getUserID(email);
    }

    @GetMapping("/get/jwt")
    public String getJWT(@RequestParam String email){
        return accountService.generateInternalJWT(email);
    }

    @GetMapping("/internal/account/jwt")
    public String getAccountJWTToken(@RequestParam("email") String email){
        return jwtService.generateLoginToken(email);
    }

    @GetMapping("/check-password")
    public Boolean checkAccountHasSetUpPassword(@RequestParam("u") String email){
        return accountService.checkAccountHasSetUpPassword(email);
    }

    @PostMapping("/update-password")
    public Response updatePassword(@RequestBody PasswordDTO passwordDTO){
        return accountService.updatePassword(passwordDTO);
    }

    @GetMapping("/oauth2/set-password")
    public Response handleSetPasswordForOauth2User(@RequestParam("u") String email){
        return accountService.handleSetPasswordRequestForOauth2User(email);
    }

    @PostMapping("/oauth2/set-password")
    public Response setPasswordForOauth2User(@RequestParam("u") String email, @RequestBody String password){
        return accountService.setPasswordForOauth2User(email, password);
    }

    @GetMapping("/profile/switch")
    public Response switchProfile(@RequestParam("u") String email, @RequestParam("pid") Integer profileID){
        return accountService.switchProfile(email, profileID);
    }
}
