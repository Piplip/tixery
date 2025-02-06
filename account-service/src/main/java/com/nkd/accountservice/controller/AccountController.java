package com.nkd.accountservice.controller;

import com.nkd.accountservice.domain.*;
import com.nkd.accountservice.service.AccountService;
import com.nkd.accountservice.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jooq.types.UInteger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    @PutMapping("/resend-activation")
    public Response resendActivation(@RequestParam("uid") String accountID){
        return accountService.resendActivation(accountID);
    }

    @PostMapping("/login")
    public Response login(@RequestBody AccountDTO accountDTO, HttpServletRequest request, HttpServletResponse response){
        return accountService.handleLogin(accountDTO, request, response);
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

    @PostMapping("/follow")
    public Response handleFollowOrganizer(@RequestParam("upid") Integer profileID, @RequestParam("opid") Integer organizerID, @RequestParam("follow") Boolean follow){
        return accountService.handleFollowOrganizer(profileID, organizerID, follow);
    }

    @GetMapping("/follow")
    public List<Integer> getFollow(@RequestParam("pid") Integer profileID){
        return accountService.getFollow(profileID);
    }

    @PostMapping("/follow/detail")
    public List<Map<String, Object>> getFollowDetail(@RequestBody List<UInteger> profileIDs){
        return accountService.getFollowDetail(profileIDs);
    }

    @GetMapping("/attendee/stats")
    public Map<String, Object> getAttendeeStats(@RequestParam("pid") String profileID){
        return accountService.getAttendeeStats(profileID);
    }

    @GetMapping("/attendee/profiles")
    public String getOrganizerProfiles(@RequestParam(value = "u") String email){
        return accountService.getProfiles(email);
    }

    @PostMapping("/notification/preferences/update")
    public Response updateNotificationPreferences(@RequestParam("pid") Integer profileID, @RequestParam("role") String role,
                                                  @RequestBody NotifyPreference preferences){
        return accountService.updateNotificationPreferences(profileID, role, preferences);
    }

    @GetMapping("/notification/preferences")
    public String getNotificationPreferences(@RequestParam("pid") Integer profileID){
        return accountService.getNotificationPreferences(profileID);
    }

    @GetMapping("/attendee/profile")
    public Map<String, Object> getAttendeeProfile(@RequestParam("pid") String profileID){
        return accountService.getAttendeeProfile(profileID);
    }

    @PutMapping("/attendee/profile/update")
    public Response updateAttendeeProfile(@RequestParam("pid") Integer profileID, @RequestParam("udid") Integer userDataID, @RequestBody Profile profile){
        return accountService.updateAttendeeProfile(profileID, userDataID, profile);
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
}
