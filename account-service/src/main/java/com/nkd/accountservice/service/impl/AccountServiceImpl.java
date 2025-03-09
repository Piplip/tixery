package com.nkd.accountservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nkd.accountservice.client.EventClient;
import com.nkd.accountservice.domain.*;
import com.nkd.accountservice.enumeration.EventType;
import com.nkd.accountservice.enums.RoleRoleName;
import com.nkd.accountservice.enums.UserAccountAccountStatus;
import com.nkd.accountservice.event.UserEvent;
import com.nkd.accountservice.security.CustomUserDetailService;
import com.nkd.accountservice.security.CustomUserDetails;
import com.nkd.accountservice.service.AccountService;
import com.nkd.accountservice.service.JwtService;
import com.nkd.accountservice.tables.pojos.Confirmation;
import com.nkd.accountservice.utils.CommonUtils;
import com.nkd.accountservice.utils.ResponseMessageCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Triple;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.types.UByte;
import org.jooq.types.UInteger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.nkd.accountservice.Tables.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

    private final DSLContext context;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher publisher;
    private final AuthenticationManager authenticationManager;
    private final MessageSource messageSource;
    private final RedisTemplate<String, String> redisTemplate;
    private final JwtService jwtService;
    private final CustomUserDetailService userDetailService;
    private final EventClient eventClient;

    @Override
    @Transactional
    public Response handleSignUp(AccountDTO accountDTO) {
        UInteger credentialID = context.insertInto(CREDENTIAL)
                .set(CREDENTIAL.PASSWORD, encoder.encode(accountDTO.getPassword()))
                .set(CREDENTIAL.LAST_UPDATED_AT, LocalDateTime.now())
                .returningResult(CREDENTIAL.CREDENTIAL_ID)
                .fetchSingleInto(UInteger.class);

        UInteger accountID = context.insertInto(USER_ACCOUNT)
                .set(USER_ACCOUNT.ACCOUNT_EMAIL, accountDTO.getEmail())
                .set(USER_ACCOUNT.ACCOUNT_STATUS, UserAccountAccountStatus.CREATED)
                .set(USER_ACCOUNT.ACCOUNT_CREATED_AT, LocalDateTime.now())
                .set(USER_ACCOUNT.CREDENTIAL_ID, credentialID)
                .returningResult(USER_ACCOUNT.ACCOUNT_ID)
                .fetchSingleInto(UInteger.class);

        UserEvent registrationEvent = new UserEvent();
        registrationEvent.setEventType(EventType.REGISTRATION);
        Triple<Integer, String, LocalDateTime> confirmation = generateConfirmation(accountID);
        registrationEvent.setData(
                Map.of(
                    "email", accountDTO.getEmail(),
                    "accountID", accountID.intValue(),
                    "confirmationID", confirmation.getLeft(),
                    "token", confirmation.getMiddle(),
                    "expirationTime", confirmation.getRight()));
        publisher.publishEvent(registrationEvent);

        return new Response(HttpStatus.OK.name(), "Account created! Go to your gmail to complete registration", null);
    }

    @Override
    @Transactional
    public Response activateAccount(String accountID, String confirmationID, String token) {
        UserAccountAccountStatus status = context.select(USER_ACCOUNT.ACCOUNT_STATUS)
                        .from(USER_ACCOUNT)
                            .where(USER_ACCOUNT.ACCOUNT_ID.eq(UInteger.valueOf(accountID)))
                            .fetchSingleInto(UserAccountAccountStatus.class);

        if(status == UserAccountAccountStatus.VERIFIED){
            return new Response(HttpStatus.BAD_REQUEST.name(), "ACCOUNT_VERIFY_ALREADY", null);
        }

        Optional<Confirmation> confirmation = context.selectFrom(CONFIRMATION)
                .where(CONFIRMATION.CONFIRMATION_ID.eq(UInteger.valueOf(confirmationID)))
                .and(CONFIRMATION.TOKEN.eq(token))
                .fetchOptionalInto(Confirmation.class);

        if(confirmation.isEmpty() || confirmation.get().getExpiredAt().isBefore(LocalDateTime.now()))
            return new Response(HttpStatus.BAD_REQUEST.name(), "ACCOUNT_VERIFY_EXPIRED", null);

        if(!confirmation.get().getToken().equals(token))
            return new Response(HttpStatus.BAD_REQUEST.name(), "INVALID_OPERATION", null);

        context.deleteFrom(CONFIRMATION)
                .where(CONFIRMATION.CONFIRMATION_ID.eq(UInteger.valueOf(confirmationID)))
                .execute();

        context.update(USER_ACCOUNT)
                .set(USER_ACCOUNT.ACCOUNT_STATUS, UserAccountAccountStatus.VERIFIED)
                .where(USER_ACCOUNT.ACCOUNT_ID.eq(UInteger.valueOf(accountID)))
                .execute();

        return new Response(HttpStatus.OK.name(), "Account verified successfully", null);
    }

    @Override
    public Response handleLogin(AccountDTO accountDTO, HttpServletRequest request, HttpServletResponse response) {
        if(!isEmailExist(accountDTO.getEmail())){
            log.error("Email not found : {}", accountDTO.getEmail());
            return new Response(HttpStatus.BAD_REQUEST.name(), ResponseMessageCode.ACCOUNT_NOT_FOUND, null);
        }
        if(checkVerifiedAccount(accountDTO.getEmail())){
            log.error("Account not verified : {}", accountDTO.getEmail());
            return new Response(HttpStatus.BAD_REQUEST.name(), ResponseMessageCode.ACCOUNT_NOT_VERIFIED, null);
        }
        else if(context.fetchExists(context.selectFrom(USER_ACCOUNT).where(USER_ACCOUNT.CREDENTIAL_ID.isNull()
                .and(USER_ACCOUNT.ACCOUNT_EMAIL.eq(accountDTO.getEmail()))))) {
            log.error("Mismatch login method : {}", accountDTO.getEmail());
            return new Response(HttpStatus.BAD_REQUEST.name()
                    , ResponseMessageCode.MISMATCH_LOGIN_METHOD, null);
        }

        CustomUserDetails userDetails = userDetailService.loadUserByUsername(accountDTO.getEmail());
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(userDetails, accountDTO.getPassword(), userDetails.getAuthorities());
        Authentication authentication = authenticationManager.authenticate(authenticationToken);
        if (authentication.isAuthenticated()) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String token = jwtService.generateLoginToken(accountDTO.getEmail());

            Cookie cookie = new Cookie("AUTHENTICATED", "true");
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(60 * 60 * 24);
            response.addCookie(cookie);

            return new Response(HttpStatus.OK.name(), ResponseMessageCode.LOGIN_SUCCESSFUL, token);
        }
        return new Response(HttpStatus.BAD_REQUEST.name(), ResponseMessageCode.USERNAME_OR_PASSWORD_INCORRECT, null);
    }

    @Override
    public Response handleLogout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        request.getSession().invalidate();
        return new Response(HttpStatus.OK.name(), "Logout successful", null);
    }

    @Override
    public Response checkEmailExists(String email) {
        if(isEmailExist(email))
            return new Response(HttpStatus.OK.name(), "Email exists", Map.of("exists", true));
        return new Response(HttpStatus.OK.name(), null, null);
    }

    @Override
    public Response resendActivation(String accountID) {
        if(checkVerifiedAccount(UInteger.valueOf(accountID))){
            return new Response(HttpStatus.BAD_REQUEST.name(), "Account already verified", null);
        }

        // Check if user resend activation email within 1 minute
        String lastResendActivation = redisTemplate.opsForValue().get("resend_activation_" + accountID);
        if(lastResendActivation != null){
            LocalDateTime lastResendActivationTime = LocalDateTime.parse(lastResendActivation, DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
            if(lastResendActivationTime.plusMinutes(1).isAfter(LocalDateTime.now())){
                return new Response(HttpStatus.BAD_REQUEST.name(), "Resend activation email too frequently!"
                        , lastResendActivationTime.plusMinutes(1).format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")));
            }
        }

        UserEvent resendActivationEvt = new UserEvent();
        resendActivationEvt.setEventType(EventType.RESEND_ACTIVATION);
        Triple<Integer, String, LocalDateTime> confirmation = generateConfirmation(UInteger.valueOf(accountID));
        String userEmail = context.select(USER_ACCOUNT.ACCOUNT_EMAIL).from(USER_ACCOUNT).where(USER_ACCOUNT.ACCOUNT_ID.eq(UInteger.valueOf(accountID))).fetchSingleInto(String.class);
        resendActivationEvt.setData(
                Map.of(
                        "email", userEmail,
                        "accountID", Integer.parseInt(accountID),
                        "confirmationID", confirmation.getLeft(),
                        "token", confirmation.getMiddle(),
                        "expirationTime", confirmation.getRight()));
        publisher.publishEvent(resendActivationEvt);

        // Store new token event to redis to prevent spamming
        redisTemplate.opsForValue().set("resend_activation_" + accountID, LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")));
        return new Response(HttpStatus.OK.name(), "Resend activation email! Check your email to continue", null);
    }

    @Override
    public Response createSetUpProfileRequest(String accountID) {
        String requestID = CommonUtils.generateRandomString(20);
        redisTemplate.opsForValue().set("setup_profile_" + requestID, accountID, Duration.ofMinutes(10));
        return new Response(HttpStatus.OK.name(), "Request created", Map.of("requestID", requestID));
    }

    @Override
    @Transactional
    public Response handleCreateProfile(String requestID, Profile profile, String role) {
        String accountID = redisTemplate.opsForValue().get("setup_profile_" + requestID);
        if(accountID == null){
            return new Response(HttpStatus.BAD_REQUEST.name(), "Request not found", null);
        }

        UInteger profileID;

        if(role.equals("attendee")){
            profileID = saveAttendeeProfile(accountID, profile, null);
            createProfileNotificationPreferences(profileID, RoleRoleName.ATTENDEE);
        }
        else if(role.equals("organizer")){
            profileID = saveOrganizerProfile(accountID, profile);
            createProfileNotificationPreferences(profileID, RoleRoleName.HOST);
        }

        redisTemplate.delete("setup_profile_" + requestID);
        return new Response(HttpStatus.OK.name(), "Profile created", null);
    }

    @Override
    public Response handleCreateOAuth2Profile(String email, Profile profile, String role) {
        var res = context.select(USER_ACCOUNT.ACCOUNT_ID, USER_ACCOUNT.DEFAULT_PROFILE_ID)
                .from(USER_ACCOUNT)
                .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email))
                .fetchSingle();

        if(role.equals("organizer")){
            saveOrganizerProfile(res.value1().toString(), profile);
        }
        else saveAttendeeProfile(res.value1().toString(), profile, res.value2());

        return new Response(HttpStatus.OK.name(), "Profile created", null);
    }

    @Override
    public Response getUpdatedToken(String email) {
        return new Response(HttpStatus.OK.name(), "Token updated", jwtService.generateLoginToken(email));
    }

    @Override
    public Response handleForgotPassword(String email) {
        if(checkAccountHasSetUpPassword(email)){
            return new Response(HttpStatus.BAD_REQUEST.name(), "Invalid Operation", null);
        }

        String code = CommonUtils.generateRandomNumber(6);
        redisTemplate.opsForValue().set("forgot_password_" + email, code, Duration.ofMinutes(10));

        UserEvent forgotPasswordEvt = new UserEvent();
        forgotPasswordEvt.setEventType(EventType.FORGOT_PASSWORD);
        forgotPasswordEvt.setData(Map.of("email", email, "code", code, "expirationTime", LocalDateTime.now().plusMinutes(10)));
        publisher.publishEvent(forgotPasswordEvt);

        return new Response(HttpStatus.OK.name(), "Code sent to your email", null);
    }

    @Override
    public Response handleForgotPasswordVerification(String email, String code) {
        String storedCode = redisTemplate.opsForValue().get("forgot_password_" + email);
        if(storedCode == null){
            return new Response(HttpStatus.BAD_REQUEST.name(), "Code expired", null);
        }

        if(!storedCode.equals(code)){
            return new Response(HttpStatus.BAD_REQUEST.name(), "Invalid code", null);
        }

        return new Response(HttpStatus.OK.name(), "Code verified", null);
    }

    @Override
    public Response handleForgotPasswordReset(String email, String newPassword) {
        String storedCode = redisTemplate.opsForValue().get("forgot_password_" + email);
        if(storedCode == null){
            return new Response(HttpStatus.BAD_REQUEST.name(), "Invalid Operation", null);
        }

        UInteger credentialID = context.select(USER_ACCOUNT.CREDENTIAL_ID)
                .from(USER_ACCOUNT)
                .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email))
                .fetchSingleInto(UInteger.class);

        context.update(CREDENTIAL)
                .set(CREDENTIAL.PASSWORD, encoder.encode(newPassword))
                .set(CREDENTIAL.LAST_UPDATED_AT, LocalDateTime.now())
                .where(CREDENTIAL.CREDENTIAL_ID.eq(credentialID))
                .execute();

        redisTemplate.delete("forgot_password_" + email);
        return new Response(HttpStatus.OK.name(), "Password reset successfully", null);
    }

    @Override
    public Integer getUserID(String email) {
        return context.select(USER_ACCOUNT.ACCOUNT_ID).from(USER_ACCOUNT)
                .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email))
                .fetchSingleInto(Integer.class);
    }

    @Override
    public String generateInternalJWT(String email) {
        return jwtService.generateInternalToken(email);
    }

    @Override
    public Response handleFollowOrganizer(Integer profileID, Integer organizerID, Boolean follow) {
        var existingFollow = context.selectFrom(FOLLOWERS)
                .where(FOLLOWERS.FOLLOWER_PROFILE_ID.eq(UInteger.valueOf(profileID))
                        .and(FOLLOWERS.PROFILE_ID.eq(UInteger.valueOf(organizerID))))
                .fetchOne();

        if (follow) {
            if (existingFollow == null) {
                context.insertInto(FOLLOWERS)
                        .set(FOLLOWERS.FOLLOWER_PROFILE_ID, UInteger.valueOf(profileID))
                        .set(FOLLOWERS.PROFILE_ID, UInteger.valueOf(organizerID))
                        .set(FOLLOWERS.FOLLOW_DATE, LocalDateTime.now())
                        .execute();

                context.update(PROFILE)
                        .set(PROFILE.TOTAL_FOLLOWERS, PROFILE.TOTAL_FOLLOWERS.plus(1))
                        .where(PROFILE.PROFILE_ID.eq(UInteger.valueOf(organizerID)))
                        .execute();
            } else {
                if (existingFollow.getUnfollowDate() != null) {
                    context.update(FOLLOWERS)
                            .set(FOLLOWERS.FOLLOW_DATE, LocalDateTime.now())
                            .set(FOLLOWERS.UNFOLLOW_DATE, (LocalDateTime) null)
                            .where(FOLLOWERS.FOLLOWER_PROFILE_ID.eq(UInteger.valueOf(profileID))
                                    .and(FOLLOWERS.PROFILE_ID.eq(UInteger.valueOf(organizerID))))
                            .execute();

                    context.update(PROFILE)
                            .set(PROFILE.TOTAL_FOLLOWERS, PROFILE.TOTAL_FOLLOWERS.plus(1))
                            .where(PROFILE.PROFILE_ID.eq(UInteger.valueOf(organizerID)))
                            .execute();
                } else {
                    context.update(FOLLOWERS)
                            .set(FOLLOWERS.FOLLOW_DATE, LocalDateTime.now())
                            .where(FOLLOWERS.FOLLOWER_PROFILE_ID.eq(UInteger.valueOf(profileID))
                                    .and(FOLLOWERS.PROFILE_ID.eq(UInteger.valueOf(organizerID))))
                            .execute();
                }
            }
        } else {
            if (existingFollow != null && existingFollow.getUnfollowDate() == null) {
                context.update(FOLLOWERS)
                        .set(FOLLOWERS.UNFOLLOW_DATE, LocalDateTime.now())
                        .where(FOLLOWERS.FOLLOWER_PROFILE_ID.eq(UInteger.valueOf(profileID))
                                .and(FOLLOWERS.PROFILE_ID.eq(UInteger.valueOf(organizerID))))
                        .execute();

                context.update(PROFILE)
                        .set(PROFILE.TOTAL_FOLLOWERS, PROFILE.TOTAL_FOLLOWERS.minus(1))
                        .where(PROFILE.PROFILE_ID.eq(UInteger.valueOf(organizerID)))
                        .execute();
            }
        }

        return new Response(HttpStatus.OK.name(), "Success", null);
    }

    @Override
    public List<Integer> getFollow(Integer profileID) {
        return context.select(FOLLOWERS.PROFILE_ID)
                .from(FOLLOWERS)
                .where(FOLLOWERS.FOLLOWER_PROFILE_ID.eq(UInteger.valueOf(profileID))
                        .and(FOLLOWERS.UNFOLLOW_DATE.isNull()))
                .limit(10)
                .fetchInto(Integer.class);
    }

    @Override
    public List<Map<String, Object>> getFollowDetail(List<UInteger> profileIDs) {
        return context.select(PROFILE.PROFILE_ID, PROFILE.PROFILE_NAME, PROFILE.PROFILE_IMAGE_URL, PROFILE.CUSTOM_URL)
                .from(PROFILE)
                .where(PROFILE.PROFILE_ID.in(profileIDs))
                .fetchMaps();
    }

    @Override
    public Map<String, Object> getAttendeeStats(String profileID) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_saved", eventClient.getTotalFavouriteEvent(Integer.parseInt(profileID)));
        stats.put("total_followed", context.selectCount().from(FOLLOWERS)
                        .where(FOLLOWERS.FOLLOWER_PROFILE_ID.eq(UInteger.valueOf(profileID))
                        .and(FOLLOWERS.FOLLOW_DATE.isNotNull()))
                .fetchSingleInto(Integer.class));

        return stats;
    }

    @Override
    public String getProfiles(String email) {
        return context.select(PROFILE.PROFILE_ID, PROFILE.PROFILE_NAME, PROFILE.PROFILE_IMAGE_URL, PROFILE.CUSTOM_URL
                        , PROFILE.TOTAL_EVENT_HOSTED, PROFILE.TOTAL_FOLLOWERS
                )
                .from(USER_ACCOUNT.join(PROFILE).on(PROFILE.ACCOUNT_ID.eq(USER_ACCOUNT.ACCOUNT_ID)))
                .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email))
                .fetch().formatJSON();
    }

    @Override
    public Response updateNotificationPreferences(Integer profileID, String role, NotifyPreference preferences) {
        ObjectMapper mapper = new ObjectMapper();

        var rawPreferences = getNotificationPreferences(profileID);

        try {
            String newPreferences;
            if (rawPreferences != null) {
                NotifyPreference oldPreferences = mapper.readValue(rawPreferences, NotifyPreference.class);
                preferences.combinePreferences(oldPreferences);

            }
            newPreferences = role.equals("ATTENDEE") ?
                    preferences.buildAttendeePreferences() : preferences.buildOrganizerPreferences();

            context.update(PROFILE)
                    .set(PROFILE.NOTIFY_PREFERENCES, newPreferences)
                    .where(PROFILE.PROFILE_ID.eq(UInteger.valueOf(profileID)))
                    .execute();
        } catch (Exception e) {
            log.error("Error parsing preferences: {}", e.getMessage());
            return new Response(HttpStatus.BAD_REQUEST.name(), "Internal Server Error", null);
        }

        return new Response(HttpStatus.OK.name(), "Success", null);
    }

    @Override
    public String getNotificationPreferences(Integer profileID) {
        return context.select(PROFILE.NOTIFY_PREFERENCES)
                .from(PROFILE)
                .where(PROFILE.PROFILE_ID.eq(UInteger.valueOf(profileID)))
                .fetchSingleInto(String.class);
    }

    @Override
    public Map<String, Object> getAttendeeProfile(String profileID) {
        return context.select(PROFILE.PROFILE_ID, PROFILE.PROFILE_NAME, PROFILE.PROFILE_IMAGE_URL, PROFILE.DESCRIPTION, USER_ACCOUNT.ACCOUNT_CREATED_AT,
                        USER_DATA.FULL_NAME, USER_DATA.DATE_OF_BIRTH, USER_DATA.PHONE_NUMBER, USER_DATA.NATIONALITY, USER_DATA.GENDER,
                        USER_DATA.INTERESTS, USER_DATA.USER_DATA_ID
                )
                .from(PROFILE.join(USER_ACCOUNT).on(PROFILE.ACCOUNT_ID.eq(USER_ACCOUNT.ACCOUNT_ID))
                        .leftJoin(USER_DATA).on(PROFILE.USER_DATA_ID.eq(USER_DATA.USER_DATA_ID)))
                .where(PROFILE.PROFILE_ID.eq(UInteger.valueOf(profileID)))
                .fetchOneMap();
    }

    @Override
    public Response updateAttendeeProfile(Integer profileID, Integer userDataID, Profile profile) {
        context.update(PROFILE)
            .set(PROFILE.PROFILE_NAME, profile.getPpName())
            .set(PROFILE.DESCRIPTION, profile.getPpDescription())
            .set(PROFILE.PROFILE_IMAGE_URL, profile.getPpImageURL())
            .where(PROFILE.PROFILE_ID.eq(UInteger.valueOf(profileID)))
            .execute();

        context.update(USER_DATA)
                .set(USER_DATA.FULL_NAME, profile.getFullName())
                .set(USER_DATA.DATE_OF_BIRTH, LocalDate.parse(profile.getDob(), DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .set(USER_DATA.PHONE_NUMBER, profile.getPhone())
                .set(USER_DATA.NATIONALITY, profile.getNationality())
                .where(USER_DATA.USER_DATA_ID.eq(UInteger.valueOf(userDataID)))
                .execute();

        return new Response(HttpStatus.OK.name(), "Profile updated", null);
    }

    @Override
    public Boolean checkAccountHasSetUpPassword(String email) {
        return context.fetchExists(context.selectFrom(USER_ACCOUNT)
                .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email)
                    .and(USER_ACCOUNT.CREDENTIAL_ID.isNotNull())));
    }

    @Override
    public Response updatePassword(PasswordDTO passwordDTO) {
        var oldPassword = context.select(CREDENTIAL.PASSWORD)
                .from(USER_ACCOUNT.join(CREDENTIAL).on(USER_ACCOUNT.CREDENTIAL_ID.eq(CREDENTIAL.CREDENTIAL_ID)))
                .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(passwordDTO.getEmail()))
                .fetchSingleInto(String.class);

        if(!encoder.matches(passwordDTO.getPassword(), oldPassword)){
            return new Response(HttpStatus.BAD_REQUEST.name(), "Old password is incorrect", null);
        }

        UInteger credentialID = context.select(USER_ACCOUNT.CREDENTIAL_ID)
                .from(USER_ACCOUNT)
                .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(passwordDTO.getEmail()))
                .fetchSingleInto(UInteger.class);

        context.update(CREDENTIAL)
                .set(CREDENTIAL.PASSWORD, encoder.encode(passwordDTO.getNewPassword()))
                .set(CREDENTIAL.LAST_UPDATED_AT, LocalDateTime.now())
                .where(CREDENTIAL.CREDENTIAL_ID.eq(credentialID))
                .execute();

        return new Response(HttpStatus.OK.name(), "Password updated", null);
    }

    @Override
    public Response setPasswordForOauth2User(String email, String password) {
        UInteger credentialID = context.insertInto(CREDENTIAL)
                .set(CREDENTIAL.PASSWORD, encoder.encode(password))
                .set(CREDENTIAL.LAST_UPDATED_AT, LocalDateTime.now())
                .returningResult(CREDENTIAL.CREDENTIAL_ID)
                .fetchSingleInto(UInteger.class);

        context.update(USER_ACCOUNT)
                .set(USER_ACCOUNT.CREDENTIAL_ID, credentialID)
                .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email))
                .execute();

        return new Response(HttpStatus.OK.name(), "Password set", null);
    }

    @Override
    public Response handleSetPasswordRequestForOauth2User(String email) {
        UserEvent setPasswordEvt = new UserEvent();
        setPasswordEvt.setEventType(EventType.OAUTH2_SET_PASSWORD);
        setPasswordEvt.setData(Map.of("email", email));

        publisher.publishEvent(setPasswordEvt);

        return new Response(HttpStatus.OK.name(), "Set password request sent", null);
    }

    @Override
    public Response switchProfile(String email, Integer profileID) {
        context.update(USER_ACCOUNT)
                .set(USER_ACCOUNT.DEFAULT_PROFILE_ID, UInteger.valueOf(profileID))
                .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email))
                .execute();

        String newToken = jwtService.generateLoginToken(email);

        return new Response(HttpStatus.OK.name(), "Profile switched", newToken);
    }

    @Override
    public Response handleSaveInterest(Integer userDataID, String interests) {
        context.update(USER_DATA)
                .set(USER_DATA.INTERESTS, interests)
                .where(USER_DATA.USER_DATA_ID.eq(UInteger.valueOf(userDataID)))
                .execute();

        return new Response(HttpStatus.OK.name(), "Interests updated", null);
    }

    @Override
    public String getInterest(Integer userDataID) {
        return context.select(USER_DATA.INTERESTS)
                .from(USER_DATA)
                .where(USER_DATA.USER_DATA_ID.eq(UInteger.valueOf(userDataID)))
                .fetchSingleInto(String.class);
    }

    @Override
    public Map<String, Object> getOrderAttendeeInfo(Integer profileID) {
        return context.select(PROFILE.PROFILE_ID, PROFILE.PROFILE_NAME, USER_DATA.FULL_NAME, USER_DATA.PHONE_NUMBER, USER_ACCOUNT.ACCOUNT_EMAIL)
                .from(PROFILE.join(USER_ACCOUNT).on(PROFILE.ACCOUNT_ID.eq(USER_ACCOUNT.ACCOUNT_ID))
                        .join(USER_DATA).on(PROFILE.USER_DATA_ID.eq(USER_DATA.USER_DATA_ID)))
                .where(PROFILE.PROFILE_ID.eq(UInteger.valueOf(profileID)))
                .fetchOneMap();
    }

    private UInteger saveOrganizerProfile(String accountID, Profile profile){
        UInteger updateProfileID;
        Optional<UInteger> profileID = context.select(PROFILE.PROFILE_ID).from(PROFILE)
                .where(PROFILE.ACCOUNT_ID.eq(UInteger.valueOf(accountID)))
                .fetchOptionalInto(UInteger.class);
        if(profileID.isPresent()){
            UInteger userDataID = context.select(PROFILE.USER_DATA_ID).from(PROFILE)
                    .where(PROFILE.PROFILE_ID.eq(profileID.get()))
                    .fetchSingleInto(UInteger.class);
            var userDataUpdate = context.update(USER_DATA)
                    .set(USER_DATA.NATIONALITY, profile.getNationality())
                    .set(USER_DATA.PHONE_NUMBER, profile.getPhone());

            if(profile.getFullName() != null){
                userDataUpdate = userDataUpdate.set(USER_DATA.FULL_NAME, profile.getFullName());
            }
            userDataUpdate.where(USER_DATA.USER_DATA_ID.eq(userDataID)).execute();
            var updateProfile = context.update(PROFILE).set(PROFILE.PROFILE_NAME, profile.getPpName());
            if(profile.getPpImageURL() != null){
                updateProfile = updateProfile.set(PROFILE.PROFILE_NAME, profile.getFullName() + "'s Profile");
            }
            if(profile.getPpImageURL() != null){
               updateProfile = updateProfile.set(PROFILE.PROFILE_IMAGE_URL, profile.getPpImageURL());
            }
            updateProfile.where(PROFILE.PROFILE_ID.eq(profileID.get())).execute();
            updateProfileID = profileID.get();
        }
        else{
            UInteger userDataID = context.insertInto(USER_DATA)
                    .set(USER_DATA.FULL_NAME, profile.getFullName())
                    .set(USER_DATA.NATIONALITY, profile.getNationality())
                    .set(USER_DATA.PHONE_NUMBER, profile.getPhone())
                    .returningResult(USER_DATA.USER_DATA_ID)
                    .fetchSingleInto(UInteger.class);

            updateProfileID = context.insertInto(PROFILE)
                    .set(PROFILE.PROFILE_NAME, profile.getPpName())
                    .set(PROFILE.USER_DATA_ID, userDataID)
                    .set(PROFILE.PROFILE_IMAGE_URL, profile.getPpImageURL())
                    .set(PROFILE.ACCOUNT_ID, UInteger.valueOf(accountID))
                    .returningResult(PROFILE.PROFILE_ID)
                    .fetchSingleInto(UInteger.class);
        }

        UByte roleID = context.select(ROLE.ROLE_ID)
                .from(ROLE)
                .where(ROLE.ROLE_NAME.eq(RoleRoleName.HOST))
                .fetchSingleInto(UByte.class);

        context.update(USER_ACCOUNT)
                .set(USER_ACCOUNT.DEFAULT_PROFILE_ID, updateProfileID)
                .set(USER_ACCOUNT.ROLE_ID, roleID)
                .where(USER_ACCOUNT.ACCOUNT_ID.eq(UInteger.valueOf(accountID)))
                .execute();

        return updateProfileID;
    }

    private UInteger saveAttendeeProfile(String accountID, Profile profile, UInteger pid) {
        UInteger profileID = null;
        if(pid == null){
            UInteger userDataID = context.insertInto(USER_DATA)
                    .set(USER_DATA.FULL_NAME, profile.getFullName())
                    .set(USER_DATA.DATE_OF_BIRTH, LocalDate.from(DateTimeFormatter.ofPattern("dd/MM/yyyy").parse(profile.getDob())))
                    .set(USER_DATA.GENDER, profile.getGender())
                    .set(USER_DATA.PHONE_NUMBER, profile.getPhone())
                    .set(USER_DATA.NATIONALITY, profile.getNationality())
                    .returningResult(USER_DATA.USER_DATA_ID)
                    .fetchSingleInto(UInteger.class);
            profileID = context.insertInto(PROFILE)
                    .set(PROFILE.USER_DATA_ID, userDataID)
                    .set(PROFILE.ACCOUNT_ID, UInteger.valueOf(accountID))
                    .set(PROFILE.PROFILE_NAME, profile.getPpName())
                    .set(PROFILE.DESCRIPTION, profile.getPpDescription())
                    .set(PROFILE.PROFILE_IMAGE_URL, profile.getPpImageURL())
                    .returningResult(PROFILE.PROFILE_ID)
                    .fetchSingleInto(UInteger.class);
        }
        else{
            UInteger userDataID = context.select(PROFILE.USER_DATA_ID).from(PROFILE)
                    .where(PROFILE.PROFILE_ID.eq(pid))
                    .fetchSingleInto(UInteger.class);
            context.update(USER_DATA)
                    .set(USER_DATA.FULL_NAME, profile.getFullName())
                    .set(USER_DATA.DATE_OF_BIRTH, LocalDate.from(DateTimeFormatter.ofPattern("dd/MM/yyyy").parse(profile.getDob())))
                    .set(USER_DATA.GENDER, profile.getGender())
                    .set(USER_DATA.PHONE_NUMBER, profile.getPhone())
                    .set(USER_DATA.NATIONALITY, profile.getNationality())
                    .where(USER_DATA.USER_DATA_ID.eq(userDataID))
                    .execute();

            var updateQuery = context.update(PROFILE).set(PROFILE.USER_DATA_ID, userDataID);

            if (profile.getPpName() != null) {
                updateQuery = updateQuery.set(PROFILE.PROFILE_NAME, profile.getPpName());
            }
            if (profile.getPpDescription() != null) {
                updateQuery = updateQuery.set(PROFILE.DESCRIPTION, profile.getPpDescription());
            }
            if (profile.getPpImageURL() != null) {
                updateQuery = updateQuery.set(PROFILE.PROFILE_IMAGE_URL, profile.getPpImageURL());
            }

            updateQuery.where(PROFILE.PROFILE_ID.eq(pid)).execute();
        }

        UByte roleID = context.select(ROLE.ROLE_ID)
                .from(ROLE)
                .where(ROLE.ROLE_NAME.eq(RoleRoleName.ATTENDEE))
                .fetchSingleInto(UByte.class);

        context.update(USER_ACCOUNT)
            .set(USER_ACCOUNT.DEFAULT_PROFILE_ID, pid == null ? profileID : pid)
            .set(USER_ACCOUNT.ROLE_ID, roleID)
            .where(USER_ACCOUNT.ACCOUNT_ID.eq(UInteger.valueOf(accountID)))
            .execute();

        return profileID;
    }

    private boolean isEmailExist(String email) {
        return context.fetchExists(context.selectFrom(USER_ACCOUNT).where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email)));
    }

    private Triple<Integer, String, LocalDateTime> generateConfirmation(UInteger accountID){
        String token = UUID.randomUUID().toString();

        // NOTE: develop purpose only. Change to 1 day later
        var expirationTime = LocalDateTime.now().plusMinutes(1);
        Integer confirmationID = context.insertInto(CONFIRMATION)
                .set(CONFIRMATION.ACCOUNT_ID, accountID)
                .set(CONFIRMATION.TOKEN, token)
                .set(CONFIRMATION.EXPIRED_AT, expirationTime)
                .returningResult(CONFIRMATION.CONFIRMATION_ID)
                .fetchSingleInto(Integer.class);

        return Triple.of(confirmationID, token, expirationTime);
    }

    private boolean checkVerifiedAccount(Object sourceIdentity){
        Condition condition = sourceIdentity instanceof UInteger
                ? USER_ACCOUNT.ACCOUNT_ID.eq((UInteger) sourceIdentity)
                : USER_ACCOUNT.ACCOUNT_EMAIL.eq((String) sourceIdentity);

        return !context.fetchExists(context.selectFrom(USER_ACCOUNT)
                        .where(condition.and(USER_ACCOUNT.ACCOUNT_STATUS.eq(UserAccountAccountStatus.VERIFIED))));
    }

    private void createProfileNotificationPreferences(UInteger profileID, RoleRoleName role){
        String preferences = role.equals(RoleRoleName.ATTENDEE) ?
                NotifyPreference.defaultAttendeePreferences() : NotifyPreference.defaultOrganizerPreferences();

        context.update(PROFILE)
                .set(PROFILE.NOTIFY_PREFERENCES, preferences)
                .where(PROFILE.PROFILE_ID.eq(profileID))
                .execute();
    }
}
