package com.nkd.accountservice.service.impl;

import com.nkd.accountservice.domain.AccountDTO;
import com.nkd.accountservice.domain.Response;
import com.nkd.accountservice.enumeration.EventType;
import com.nkd.accountservice.enums.UserAccountAccountStatus;
import com.nkd.accountservice.event.UserEvent;
import com.nkd.accountservice.service.AccountService;
import com.nkd.accountservice.tables.pojos.Confirmation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Triple;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.types.UInteger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    @Override
    @Transactional
    public Response handleSignUp(AccountDTO accountDTO) {
        UInteger credentialID = context.insertInto(CREDENTIAL)
                .set(CREDENTIAL.PASSWORD, encoder.encode(accountDTO.getPassword()))
                .set(CREDENTIAL.LAST_UPDATED_AT, LocalDateTime.now())
                .returningResult(CREDENTIAL.CREDENTIAL_ID)
                .fetchSingleInto(UInteger.class);

//        if(Stream.of(RoleRoleName.values()).noneMatch(role -> role.getLiteral().equals(accountDTO.getRole()))){
//            log.error("Role not found : {}", accountDTO.getRole());
//            return new Response(HttpStatus.BAD_REQUEST.name(), "Role not found", null);
//        }

        UInteger accountID = context.insertInto(USER_ACCOUNT)
                .set(USER_ACCOUNT.ACCOUNT_EMAIL, accountDTO.getEmail())
//                .set(USER_ACCOUNT.ROLE_ID, context.select(ROLE.ROLE_ID).from(ROLE).where(ROLE.ROLE_NAME.eq(RoleRoleName.valueOf(accountDTO.getRole()))))
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
    public Response handleLogin(AccountDTO accountDTO, HttpServletRequest request) {
        if(checkVerifiedAccount(accountDTO.getEmail())){
            log.error("Account not verified : {}", accountDTO.getEmail());
            return new Response(HttpStatus.BAD_REQUEST.name(), "Account not verified", null);
        }

        try {
            Authentication authenticationRequest = UsernamePasswordAuthenticationToken.unauthenticated(accountDTO.getEmail(), accountDTO.getPassword());
            Authentication authenticationResponse = authenticationManager.authenticate(authenticationRequest);

            SecurityContext securityContext = SecurityContextHolder.getContext();
            securityContext.setAuthentication(authenticationResponse);

            HttpSession session = request.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        } catch (AuthenticationException e) {
            log.error("Authentication failed : {}", e.getMessage());
            return new Response(HttpStatus.BAD_REQUEST.name(), "Username or password is incorrect", null);
        }

        // TODO: Get user details and return back to client
        return new Response(HttpStatus.OK.name(), "Login successful", null);
    }

    @Override
    public Response handleLogout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        request.getSession().invalidate();
        return new Response(HttpStatus.OK.name(), "Logout successful", null);
    }

    @Override
    public Response checkEmailExists(String email) {
        boolean exists = context.fetchExists(context.selectFrom(USER_ACCOUNT).where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email)));
        if(exists)
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
}
