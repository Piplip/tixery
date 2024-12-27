package com.nkd.accountservice.domain;

import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class Profile {

    String fullName;
    String nickname;
    String dob;
    String gender;
    String phone;
    String nationality;
    String ppName;
    String ppImageURL;
    String ppDescription;
}
