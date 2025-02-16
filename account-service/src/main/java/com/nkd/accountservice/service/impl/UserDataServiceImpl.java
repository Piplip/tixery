package com.nkd.accountservice.service.impl;

import com.nkd.accountservice.domain.Profile;
import com.nkd.accountservice.domain.Response;
import com.nkd.accountservice.service.JwtService;
import com.nkd.accountservice.service.UserDataService;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.types.UInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.nkd.accountservice.Tables.*;

@Service
@RequiredArgsConstructor
public class UserDataServiceImpl implements UserDataService {

    private final DSLContext context;
    private final JwtService jwtService;

    @Override
    public String getOrganizerProfiles(String email) {
        return context.select(PROFILE.PROFILE_ID, PROFILE.PROFILE_NAME, PROFILE.PROFILE_IMAGE_URL, PROFILE.CUSTOM_URL
                    , PROFILE.TOTAL_EVENT_HOSTED, PROFILE.TOTAL_FOLLOWERS
                )
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
    public Map<String, Object> getProfile(String profileID) {
        Condition condition;
        if (profileID.matches("[0-9]+")) {
            condition = PROFILE.PROFILE_ID.eq(UInteger.valueOf(profileID));
        } else {
            condition = PROFILE.CUSTOM_URL.eq(profileID);
        }

        return context.select(PROFILE.PROFILE_ID, PROFILE.PROFILE_NAME, PROFILE.PROFILE_IMAGE_URL, PROFILE.DESCRIPTION,
                PROFILE.TOTAL_FOLLOWERS, PROFILE.TOTAL_ATTENDEE_HOSTED, PROFILE.TOTAL_EVENT_HOSTED,
                PROFILE.EMAIL_OPT_IN, PROFILE.CUSTOM_URL, PROFILE.SOCIAL_MEDIA_LINKS)
                .from(USER_ACCOUNT.join(PROFILE).on(PROFILE.ACCOUNT_ID.eq(USER_ACCOUNT.ACCOUNT_ID)))
                .where(condition)
                .fetchOneMap();
    }

    @Override
    public Response updateProfile(String profileId, String email, Profile profile) {
        var update = context.update(PROFILE)
                .set(PROFILE.PROFILE_NAME, profile.getPpName())
                .set(PROFILE.DESCRIPTION, profile.getPpDescription())
                .set(PROFILE.EMAIL_OPT_IN, Byte.valueOf(profile.getEmailOptIn()))
                .set(PROFILE.CUSTOM_URL, profile.getCustomURL())
                .set(PROFILE.SOCIAL_MEDIA_LINKS, profile.getSocialMediaLinks());

        if(profile.getPpImageURL() != null){
            update = update.set(PROFILE.PROFILE_IMAGE_URL, profile.getPpImageURL());
        }

        update.where(PROFILE.PROFILE_ID.eq(UInteger.valueOf(profileId)))
                .execute();

        boolean isDefaultProfile = context.fetchExists(USER_ACCOUNT.join(PROFILE).on(PROFILE.ACCOUNT_ID.eq(USER_ACCOUNT.ACCOUNT_ID))
                .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email)
                        .and(PROFILE.PROFILE_ID.eq(UInteger.valueOf(profileId))
                                .and(USER_ACCOUNT.DEFAULT_PROFILE_ID.eq(UInteger.valueOf(profileId))))));

        if(isDefaultProfile){
            String newToken = jwtService.generateLoginToken(email);
            return new Response(HttpStatus.OK.name(), "Profile updated", newToken);
        }

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
                    .where(USER_ACCOUNT.ACCOUNT_EMAIL.eq(email).and(PROFILE.PROFILE_ID.ne(UInteger.valueOf(profileID))))
                    .orderBy(PROFILE.PROFILE_ID.asc())
                    .limit(1)
                    .fetchOptionalInto(UInteger.class);

            if(nextProfileID.isPresent()){
                UInteger userDataID = context.select(PROFILE.USER_DATA_ID)
                        .from(PROFILE)
                        .where(PROFILE.PROFILE_ID.eq(UInteger.valueOf(profileID)))
                        .fetchOneInto(UInteger.class);
                context.deleteFrom(PROFILE)
                        .where(PROFILE.PROFILE_ID.eq(UInteger.valueOf(profileID)))
                        .execute();
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

        String newToken = jwtService.generateLoginToken(email);

        return new Response(HttpStatus.OK.name(), "Profile deleted", newToken);
    }

    @Override
    public Response checkUniqueCustomURL(String customURL) {
        boolean isUnique = context.fetchExists(PROFILE, PROFILE.CUSTOM_URL.eq(customURL));
        if(!isUnique){
            return new Response(HttpStatus.OK.name(), "Unique", null);
        }
        return new Response(HttpStatus.BAD_REQUEST.name(), "Not Unique", null);
    }

    @Override
    public Response updateProfileStatistic(String profileID, String field, String type) {
        switch (field){
            case "events" -> {
                if(type.equals("add")){
                    context.update(PROFILE)
                            .set(PROFILE.TOTAL_EVENT_HOSTED, PROFILE.TOTAL_EVENT_HOSTED.add(1))
                            .where(PROFILE.PROFILE_ID.eq(UInteger.valueOf(profileID)))
                            .execute();
                }
                else{
                    context.update(PROFILE)
                            .set(PROFILE.TOTAL_EVENT_HOSTED, PROFILE.TOTAL_EVENT_HOSTED.sub(1))
                            .where(PROFILE.PROFILE_ID.eq(UInteger.valueOf(profileID)))
                            .execute();
                }
            }
            case "attendee" -> {
                if(type.equals("add")){
                    context.update(PROFILE)
                            .set(PROFILE.TOTAL_ATTENDEE_HOSTED, PROFILE.TOTAL_ATTENDEE_HOSTED.add(1))
                            .where(PROFILE.PROFILE_ID.eq(UInteger.valueOf(profileID)))
                            .execute();
                }
                else{
                    context.update(PROFILE)
                            .set(PROFILE.TOTAL_ATTENDEE_HOSTED, PROFILE.TOTAL_ATTENDEE_HOSTED.sub(1))
                            .where(PROFILE.PROFILE_ID.eq(UInteger.valueOf(profileID)))
                            .execute();
                }
            }
        }
        return new Response(HttpStatus.OK.name(), "Profile statistic updated", null);
    }

    @Override
    public Map<Integer, String> getListProfileName(String profileIdList) {
        if (profileIdList == null) {
            return new HashMap<>();
        }

        List<Integer> profileID = Stream.of(profileIdList.split(",")).map(Integer::parseInt).toList();

        var nameList = context.select(PROFILE.PROFILE_ID, PROFILE.PROFILE_NAME)
                .from(PROFILE)
                .where(PROFILE.PROFILE_ID.in(profileID))
                .fetchMap(PROFILE.PROFILE_ID, PROFILE.PROFILE_NAME);

        Map<Integer, String> returnVal = new HashMap<>();
        for (Integer id : profileID) {
            returnVal.put(id, nameList.get(UInteger.valueOf(id)));
        }
        return returnVal;
    }
}
