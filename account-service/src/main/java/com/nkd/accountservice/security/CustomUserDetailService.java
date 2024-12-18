package com.nkd.accountservice.security;

import org.jooq.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static com.nkd.accountservice.Tables.*;

public class CustomUserDetailService implements UserDetailsService {

    @Autowired
    private DSLContext context;

    @Override
    public CustomUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var data = context.select(USER_ACCOUNT.ACCOUNT_NAME, CREDENTIAL.PASSWORD, ROLE.ROLE_PRIVILEGES)
                .from(USER_ACCOUNT.join(CREDENTIAL).on(USER_ACCOUNT.CREDENTIAL_ID.eq(CREDENTIAL.CREDENTIAL_ID))
                        .join(ROLE).on(USER_ACCOUNT.ROLE_ID.eq(ROLE.ROLE_ID)))
                .where(USER_ACCOUNT.ACCOUNT_NAME.eq(username))
                .fetchOptional();

        if(data.isEmpty())
            return null;

        Record3<String, String, String> record = data.get();

        return CustomUserDetails.builder()
                .username(record.component1())
                .password(record.component2())
                .rolePrivileges(record.component3())
                .build();
    }

}
