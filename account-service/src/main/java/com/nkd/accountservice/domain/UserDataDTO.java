package com.nkd.accountservice.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.jooq.types.UInteger;

import java.util.List;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDataDTO {

    private UInteger accountId;
    private UInteger profileId;
    private Integer userDataId;
    private String accountEmail;
    private String roleName;
    private String fullName;
    private String dateOfBirth;
    private String gender;
    private String phoneNumber;
    private String nationality;
    private String accountStatus;
    private String profileName;
    private String description;
    private String profileImageUrl;
    private Integer emailOptIn;
    private List<String> socialMediaLinks;
    private String customUrl;
    private Integer totalFollowers;
    private Integer totalAttendeeHosted;
    private Integer totalEventHosted;
    private String notifyPreferences;
    private String authorities;
}
