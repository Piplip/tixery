package com.nkd.accountservice.security;

import com.nkd.accountservice.enums.RoleRoleName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static com.nkd.accountservice.Tables.*;

@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailService implements UserDetailsService {

    private final DSLContext context;

    @Override
    public CustomUserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var data = context.select(USER_ACCOUNT.ACCOUNT_EMAIL, CREDENTIAL.PASSWORD, ROLE.ROLE_PRIVILEGES, ROLE.ROLE_NAME)
                .from(USER_ACCOUNT.leftJoin(CREDENTIAL).on(USER_ACCOUNT.CREDENTIAL_ID.eq(CREDENTIAL.CREDENTIAL_ID))
                        .leftJoin(ROLE).on(USER_ACCOUNT.ROLE_ID.eq(ROLE.ROLE_ID)))
                .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email))
                .fetchOptional();

        if(data.isEmpty()){
            log.error("User not found with email: {}", email);
            return null;
        }

        Record4<String, String, String, RoleRoleName> record = data.get();

        StringBuilder rolePrivileges = new StringBuilder();
        rolePrivileges.append(record.component3());
        RoleRoleName roleName = record.component4();
        if(roleName != null){
            rolePrivileges.append(",ROLE_").append(roleName);
        }
        return CustomUserDetails.builder()
                .username(record.component1())
                .password(record.component2())
                .rolePrivileges(rolePrivileges.toString())
                .build();
    }

}
