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
import org.jooq.DSLContext;
import org.jooq.types.UInteger;
import org.springframework.context.ApplicationEventPublisher;
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

    @Override
    @Transactional
    public Response handleSignUp(AccountDTO accountDTO) {
        System.out.println(accountDTO.toString());

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
                .set(USER_ACCOUNT.ACCOUNT_NAME, accountDTO.getUsername())
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
                    "accountName", accountDTO.getUsername(),
                    "email", accountDTO.getEmail(),
                    "accountID", accountID.intValue(),
                    "confirmationID", confirmation.getLeft(),
                    "token", confirmation.getMiddle(),
                    "expirationTime", confirmation.getRight()));
        publisher.publishEvent(registrationEvent);

        return new Response(HttpStatus.OK.name(), "Account created", null);
    }

    @Override
    @Transactional
    public Response activateAccount(String accountID, String confirmationID, String token) {
        UserAccountAccountStatus status = context.select(USER_ACCOUNT.ACCOUNT_STATUS)
                        .from(USER_ACCOUNT)
                            .where(USER_ACCOUNT.ACCOUNT_ID.eq(UInteger.valueOf(accountID)))
                            .fetchSingleInto(UserAccountAccountStatus.class);

        if(status == UserAccountAccountStatus.VERIFIED){
            return new Response(HttpStatus.BAD_REQUEST.name(), "Account already verified", null);
        }

        Optional<Confirmation> confirmation = context.selectFrom(CONFIRMATION)
                .where(CONFIRMATION.CONFIRMATION_ID.eq(UInteger.valueOf(confirmationID)))
                .and(CONFIRMATION.TOKEN.eq(token))
                .fetchOptionalInto(Confirmation.class);

        if(confirmation.isEmpty())
            return new Response(HttpStatus.BAD_REQUEST.name(), "Invalid token or token is expired!", null);


        if(!confirmation.get().getToken().equals(token))
            return new Response(HttpStatus.BAD_REQUEST.name(), "Invalid operation", null);

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
        try {
            Authentication authenticationRequest = UsernamePasswordAuthenticationToken.unauthenticated(accountDTO.getUsername(), accountDTO.getPassword());
            Authentication authenticationResponse = authenticationManager.authenticate(authenticationRequest);

            SecurityContext securityContext = SecurityContextHolder.getContext();
            securityContext.setAuthentication(authenticationResponse);

            HttpSession session = request.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        } catch (AuthenticationException e) {
            log.error("Authentication failed : {}", e.getMessage());
            return new Response(HttpStatus.BAD_REQUEST.name(), "Username or password is incorrect", null);
        }

        return new Response(HttpStatus.OK.name(), "Login successful", null);
    }

    @Override
    public Response handleLogout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        request.getSession().invalidate();
        return new Response(HttpStatus.OK.name(), "Logout successful", null);
    }

    private Triple<Integer, String, LocalDateTime> generateConfirmation(UInteger accountID){
        String token = UUID.randomUUID().toString();
        var expirationTime = LocalDateTime.now().plusDays(1);
        Integer confirmationID = context.insertInto(CONFIRMATION)
                .set(CONFIRMATION.ACCOUNT_ID, accountID)
                .set(CONFIRMATION.TOKEN, token)
                .set(CONFIRMATION.EXPIRED_AT, expirationTime)
                .returningResult(CONFIRMATION.CONFIRMATION_ID)
                .fetchSingleInto(Integer.class);

        return Triple.of(confirmationID, token, expirationTime);
    }
}
