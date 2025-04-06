package com.nkd.accountservice.service.impl;

import com.nkd.accountservice.domain.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.types.UInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.nkd.accountservice.tables.Profile.PROFILE;
import static com.nkd.accountservice.tables.Role.ROLE;
import static com.nkd.accountservice.tables.UserAccount.USER_ACCOUNT;
import static com.nkd.accountservice.tables.UserData.USER_DATA;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminService {

    private final DSLContext context;

    public Map<String, Object> loadUsers(Integer page, Integer size){
        var users = context.select(USER_ACCOUNT.ACCOUNT_ID, USER_ACCOUNT.ACCOUNT_EMAIL, ROLE.ROLE_NAME, USER_DATA.FULL_NAME, USER_ACCOUNT.ACCOUNT_STATUS)
                .from(USER_ACCOUNT.join(ROLE).on(USER_ACCOUNT.ROLE_ID.eq(ROLE.ROLE_ID))
                        .leftJoin(PROFILE).on(USER_ACCOUNT.DEFAULT_PROFILE_ID.eq(PROFILE.PROFILE_ID))
                        .leftJoin(USER_DATA).on(PROFILE.USER_DATA_ID.eq(USER_DATA.USER_DATA_ID)))
                .orderBy(USER_ACCOUNT.ACCOUNT_ID.asc())
                .limit(size)
                .offset((page - 1) * size)
                .fetchMaps();

        var total = context.selectCount()
                .from(USER_ACCOUNT)
                .fetchOptionalInto(Integer.class).orElseThrow(() -> new RuntimeException("Failed to fetch total count"));

        return Map.of("users", users, "total", total);
    }

    public Map<String, Object> loadUserDetail(String role, String userID){
        var selectSentence = switch (role) {
            case "host" -> context.select(USER_ACCOUNT.ACCOUNT_EMAIL, ROLE.ROLE_NAME, PROFILE.asterisk(), USER_DATA.asterisk().except(USER_DATA.USER_DATA_ID))
                    .from(USER_ACCOUNT.join(ROLE).on(USER_ACCOUNT.ROLE_ID.eq(ROLE.ROLE_ID))
                            .leftJoin(PROFILE).on(USER_ACCOUNT.DEFAULT_PROFILE_ID.eq(PROFILE.PROFILE_ID))
                            .leftJoin(USER_DATA).on(PROFILE.USER_DATA_ID.eq(USER_DATA.USER_DATA_ID)));
            case "attendee", "admin" -> context.select(USER_ACCOUNT.ACCOUNT_ID, USER_ACCOUNT.ACCOUNT_EMAIL, ROLE.ROLE_NAME,
                            USER_DATA.asterisk(), PROFILE.PROFILE_IMAGE_URL, PROFILE.NOTIFY_PREFERENCES)
                    .from(USER_ACCOUNT.join(ROLE).on(USER_ACCOUNT.ROLE_ID.eq(ROLE.ROLE_ID))
                            .leftJoin(PROFILE).on(USER_ACCOUNT.DEFAULT_PROFILE_ID.eq(PROFILE.PROFILE_ID))
                            .leftJoin(USER_DATA).on(PROFILE.USER_DATA_ID.eq(USER_DATA.USER_DATA_ID)));
            default -> throw new IllegalArgumentException("Invalid role: " + role);
        };

        return selectSentence
                .where(USER_ACCOUNT.ACCOUNT_ID.eq(UInteger.valueOf(userID)))
                .fetchOneMap();
    }

    public Response deleteUser(String userID) {
        var deleted = context.deleteFrom(USER_ACCOUNT)
                .where(USER_ACCOUNT.ACCOUNT_ID.eq(UInteger.valueOf(userID)))
                .execute();

        if (deleted == 0) {
            return new Response(HttpStatus.NOT_FOUND.name(), "User not found", null);
        }

        return new Response(HttpStatus.OK.name(), "User deleted successfully", null);
    }
}
