package com.nkd.accountservice.service;

import com.nkd.accountservice.domain.Profile;
import com.nkd.accountservice.domain.Response;

import java.util.Map;

public interface UserDataService {
    String getOrganizerProfiles(String email);
    Response saveOrganizerProfile(String email, Profile profile);
    Map<String, Object> getProfile(String profileId);
    Response updateProfile(String profileId, Profile profile);
    Response deleteProfile(String profileId, String email);
}
