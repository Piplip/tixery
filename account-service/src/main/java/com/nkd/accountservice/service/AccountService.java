package com.nkd.accountservice.service;

import com.nkd.accountservice.domain.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jooq.types.UInteger;

import java.util.List;
import java.util.Map;

public interface AccountService {
    Response handleSignUp(AccountDTO accountDTO);
    Response activateAccount(String accountID, String confirmationID, String token);
    Response handleLogin(AccountDTO accountDTO, HttpServletRequest request, HttpServletResponse response);
    Response handleLogout(HttpServletRequest request);
    Response checkEmailExists(String email);
    Response resendActivation(String accountID);
    Response createSetUpProfileRequest(String accountID);
    Response handleCreateProfile(String requestID, Profile profile, String role);
    Response handleCreateOAuth2Profile(String email, Profile profile, String role);
    Response getUpdatedToken(String email);
    Response handleForgotPassword(String email);
    Response handleForgotPasswordVerification(String email, String code);
    Response handleForgotPasswordReset(String email, String newPassword);
    Integer getUserID(String email);
    String generateInternalJWT(String email);
    Response handleFollowOrganizer(Integer profileID, Integer organizerID, Boolean follow);
    List<Integer> getFollow(Integer profileID);
    List<Map<String, Object>> getFollowDetail(List<UInteger> profileIDs);
    Map<String, Object> getAttendeeStats(String profileID);
    String getProfiles(String email);
    Response updateNotificationPreferences(Integer profileID, String role, NotifyPreference preferences);
    String getNotificationPreferences(Integer profileID);
    Map<String, Object> getAttendeeProfile(String profileID);
    Response updateAttendeeProfile(Integer profileID, Integer userDataID, Profile profile);
    Boolean checkAccountHasSetUpPassword(String email);
    Response updatePassword(PasswordDTO passwordDTO);
    Response setPasswordForOauth2User(String email, String password);
    Response handleSetPasswordRequestForOauth2User(String email);
    Response switchProfile(String email, Integer profileID);
    Response handleSaveInterest(Integer userDataID, String interests);
    String getInterest(Integer userDataID);
    Map<String, Object> getOrderAttendeeInfo(Integer profileID);
    Map<String, Object> getAccountData(Integer profileID);
}