package com.nkd.accountservice.service.impl;

import com.nkd.accountservice.domain.Profile;
import com.nkd.accountservice.domain.Response;
import com.nkd.accountservice.service.UserDataService;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.types.UInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

import static com.nkd.accountservice.Tables.*;

@Service
@RequiredArgsConstructor
public class UserDataServiceImpl implements UserDataService {

    private final DSLContext context;

    @Override
    public String getOrganizerProfiles(String email) {
        return context.select(PROFILE.PROFILE_ID, PROFILE.PROFILE_NAME, PROFILE.PROFILE_IMAGE_URL, PROFILE.CUSTOM_URL)
                .from(USER_ACCOUNT.join(PROFILE).on(PROFILE.ACCOUNT_ID.eq(USER_ACCOUNT.ACCOUNT_ID)))
                .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email))
                .fetch().formatJSON();
    }

    @Override
    public Response saveOrganizerProfile(String email, Profile profile) {
        Optional<UInteger> accountId = context.select(USER_ACCOUNT.ACCOUNT_ID)
                .from(USER_ACCOUNT)
                .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email))
                .fetchOptionalInto(UInteger.class);
        if(accountId.isEmpty()){
            return new Response(HttpStatus.BAD_REQUEST.name(), "User not found", null);
        }
        context.insertInto(PROFILE)
                .set(PROFILE.PROFILE_NAME, profile.getPpName())
                .set(PROFILE.PROFILE_IMAGE_URL, profile.getPpImageURL())
                .set(PROFILE.DESCRIPTION, profile.getPpDescription())
                .set(PROFILE.EMAIL_OPT_IN, Byte.valueOf(profile.getEmailOptIn()))
                .set(PROFILE.ACCOUNT_ID, accountId.get())
                .set(PROFILE.CUSTOM_URL, profile.getCustomURL())
                .set(PROFILE.SOCIAL_MEDIA_LINKS, profile.getSocialMediaLinks())
                .execute();
        return new Response(HttpStatus.OK.name(), "Profile created", null);
    }

    @Override
    public Map<String, Object> getProfile(String profileId) {
        Condition condition;
        Optional<String> customURL = context.select(PROFILE.CUSTOM_URL)
                .from(PROFILE)
                .where(PROFILE.PROFILE_ID.eq(UInteger.valueOf(profileId)))
                .fetchOptionalInto(String.class);
        condition = customURL.map(PROFILE.CUSTOM_URL::eq).orElseGet(() -> PROFILE.PROFILE_ID.eq(UInteger.valueOf(profileId)));

        return context.select(PROFILE.PROFILE_ID, PROFILE.PROFILE_NAME, PROFILE.PROFILE_IMAGE_URL, PROFILE.DESCRIPTION,
                PROFILE.EMAIL_OPT_IN, PROFILE.CUSTOM_URL, PROFILE.SOCIAL_MEDIA_LINKS)
                .from(USER_ACCOUNT.join(PROFILE).on(PROFILE.ACCOUNT_ID.eq(USER_ACCOUNT.ACCOUNT_ID)))
                .where(condition)
                .fetchOneMap();
    }

    @Override
    public Response updateProfile(String profileId, Profile profile) {
        context.update(PROFILE)
                .set(PROFILE.PROFILE_NAME, profile.getPpName())
                .set(PROFILE.PROFILE_IMAGE_URL, profile.getPpImageURL())
                .set(PROFILE.DESCRIPTION, profile.getPpDescription())
                .set(PROFILE.EMAIL_OPT_IN, Byte.valueOf(profile.getEmailOptIn()))
                .set(PROFILE.CUSTOM_URL, profile.getCustomURL())
                .set(PROFILE.SOCIAL_MEDIA_LINKS, profile.getSocialMediaLinks())
                .where(PROFILE.PROFILE_ID.eq(UInteger.valueOf(profileId)))
                .execute();
        return new Response(HttpStatus.OK.name(), "Profile updated", null);
    }

    @Override
    @Transactional
    public Response deleteProfile(String profileID, String email) {
        UInteger defaultProfileID = context.select(USER_ACCOUNT.DEFAULT_PROFILE_ID)
                .from(USER_ACCOUNT)
                .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email))
                .fetchOneInto(UInteger.class);

        assert defaultProfileID != null;
        if(defaultProfileID.equals(UInteger.valueOf(profileID))){
            Optional<UInteger> nextProfileID = context.select(PROFILE.PROFILE_ID)
                    .from(USER_ACCOUNT.join(PROFILE).on(PROFILE.ACCOUNT_ID.eq(USER_ACCOUNT.ACCOUNT_ID)))
                    .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email))
                    .orderBy(PROFILE.PROFILE_ID.asc())
                    .limit(1)
                    .fetchOptionalInto(UInteger.class);

            if(nextProfileID.isPresent()){
                UInteger userDataID = context.deleteFrom(PROFILE)
                        .where(PROFILE.PROFILE_ID.eq(UInteger.valueOf(profileID)))
                        .returningResult(PROFILE.USER_DATA_ID)
                        .fetchOneInto(UInteger.class);

                context.update(PROFILE)
                        .set(PROFILE.USER_DATA_ID, userDataID)
                        .where(PROFILE.PROFILE_ID.eq(nextProfileID.get()))
                        .execute();
                context.update(USER_ACCOUNT)
                        .set(USER_ACCOUNT.DEFAULT_PROFILE_ID, nextProfileID.get())
                        .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email))
                        .execute();
            }
            else{
                context.update(PROFILE)
                        .set(PROFILE.PROFILE_NAME, "Default")
                        .setNull(PROFILE.DESCRIPTION)
                        .set(PROFILE.EMAIL_OPT_IN, Byte.valueOf("0"))
                        .setNull(PROFILE.CUSTOM_URL)
                        .setNull(PROFILE.SOCIAL_MEDIA_LINKS)
                        .execute();
            }
        }
        else{
            context.deleteFrom(PROFILE)
                    .where(PROFILE.PROFILE_ID.eq(UInteger.valueOf(profileID)))
                    .execute();
        }

        return new Response(HttpStatus.OK.name(), "Profile deleted", null);
    }
}
