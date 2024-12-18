package com.nkd.accountservice.service.impl;

import com.nkd.accountservice.domain.AccountDTO;
import com.nkd.accountservice.domain.Response;
import com.nkd.accountservice.enums.RoleRoleName;
import com.nkd.accountservice.enums.UserAccountAccountStatus;
import com.nkd.accountservice.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.types.UInteger;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static com.nkd.accountservice.Tables.*;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final DSLContext context;
    private final PasswordEncoder encoder;

    @Override
    @Transactional
    public Response handleSignUp(AccountDTO accountDTO) {
        UInteger credentialID = context.insertInto(CREDENTIAL)
                .set(CREDENTIAL.PASSWORD, encoder.encode(accountDTO.getPassword()))
                .set(CREDENTIAL.LAST_UPDATED_AT, LocalDateTime.now())
                .returningResult(CREDENTIAL.CREDENTIAL_ID)
                .fetchSingleInto(UInteger.class);

        if(Stream.of(RoleRoleName.values()).noneMatch(role -> role.getLiteral().equals(accountDTO.getRole()))){
            return new Response(HttpStatus.BAD_REQUEST.name(), "Role not found", null);
        }

        context.insertInto(USER_ACCOUNT)
                .set(USER_ACCOUNT.ACCOUNT_NAME, accountDTO.getUsername())
                .set(USER_ACCOUNT.ACCOUNT_EMAIL, accountDTO.getEmail())
                .set(USER_ACCOUNT.ROLE_ID, context.select(ROLE.ROLE_ID).from(ROLE).where(ROLE.ROLE_NAME.eq(RoleRoleName.valueOf(accountDTO.getRole()))))
                .set(USER_ACCOUNT.ACCOUNT_STATUS, UserAccountAccountStatus.CREATED)
                .set(USER_ACCOUNT.ACCOUNT_CREATED_AT, LocalDateTime.now())
                .set(USER_ACCOUNT.CREDENTIAL_ID, credentialID)
                .execute();

        // TODO : Send email verification

        return new Response(HttpStatus.OK.name(), "Account created", null);
    }
}
