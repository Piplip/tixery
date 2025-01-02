package com.nkd.accountservice.security.oauth2;

import com.nkd.accountservice.enums.RoleRoleName;
import com.nkd.accountservice.enums.UserAccountAccountStatus;
import com.nkd.accountservice.service.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.types.UInteger;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import static com.nkd.accountservice.Tables.*;

@Component
@RequiredArgsConstructor
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final DSLContext context;
    private final JwtService jwtService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;
        Map<String, Object> attributes = authToken.getPrincipal().getAttributes();

        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String picture = (String) attributes.get("picture");

        String token, redirectURL;
        if(checkEmailUseInNormalLogin(email)){
            response.setStatus(HttpServletResponse.SC_FOUND);
            response.sendRedirect("http://localhost:5173/login?ref=already-used");
            return;
        }
        else{
            if(!checkUserExist(email)){
                saveOauth2User(email, name, picture);
                redirectURL = "http://localhost:5173/u/interests?method=external";
                token = jwtService.generateOauth2LoginToken(email, name, picture == null ? "" : picture);
            }
            else{
                if(hasSelectedRole(email)){
                    redirectURL = "http://localhost:5173";
                    RoleRoleName role = context.select(ROLE.ROLE_NAME).from(ROLE.join(USER_ACCOUNT).on(ROLE.ROLE_ID.eq(USER_ACCOUNT.ROLE_ID)))
                            .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email)).fetchSingleInto(RoleRoleName.class);
                    if(role.equals(RoleRoleName.HOST)){
                        redirectURL += "/organizer";
                    }
                }
                else{
                    redirectURL = "http://localhost:5173/u/interests?method=external";
                }
                token = jwtService.generateLoginToken(email);
            }
        }

        Cookie cookie = new Cookie("tk", token);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 20);
        response.addCookie(cookie);
        response.sendRedirect(redirectURL);
    }

    private boolean hasSelectedRole(String email) {
        return context.fetchExists(USER_ACCOUNT, USER_ACCOUNT.ACCOUNT_EMAIL.eq(email).and(USER_ACCOUNT.ROLE_ID.isNotNull()));
    }

    private boolean checkEmailUseInNormalLogin(String email) {
        return context.fetchExists(USER_ACCOUNT, USER_ACCOUNT.ACCOUNT_EMAIL.eq(email).and(USER_ACCOUNT.CREDENTIAL_ID.isNotNull()));
    }

    private boolean checkUserExist(String email) {
        return context.fetchExists(USER_ACCOUNT, USER_ACCOUNT.ACCOUNT_EMAIL.eq(email));
    }

    private void saveOauth2User(String email, String name, String picture) {
        UInteger userDataID = context.insertInto(USER_DATA).set(USER_DATA.FULL_NAME, name).returningResult(USER_DATA.USER_DATA_ID).fetchSingleInto(UInteger.class);
        UInteger accountID = context.insertInto(USER_ACCOUNT)
                .set(USER_ACCOUNT.ACCOUNT_EMAIL, email)
                .set(USER_ACCOUNT.ACCOUNT_STATUS, UserAccountAccountStatus.VERIFIED)
                .set(USER_ACCOUNT.ACCOUNT_CREATED_AT, LocalDateTime.now())
                .returningResult(USER_ACCOUNT.ACCOUNT_ID).fetchSingleInto(UInteger.class);
        UInteger profileID = context.insertInto(PROFILE)
                .set(PROFILE.USER_DATA_ID, userDataID)
                .set(PROFILE.PROFILE_IMAGE_URL, picture)
                .set(PROFILE.PROFILE_NAME, name + "'s Profile")
                .set(PROFILE.ACCOUNT_ID, accountID)
                .returningResult(PROFILE.PROFILE_ID).fetchSingleInto(UInteger.class);
        context.update(USER_ACCOUNT)
                .set(USER_ACCOUNT.DEFAULT_PROFILE_ID, profileID)
                .where(USER_ACCOUNT.ACCOUNT_ID.eq(accountID)).execute();
    }
}
