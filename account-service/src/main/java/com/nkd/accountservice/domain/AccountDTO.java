package com.nkd.accountservice.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AccountDTO {

    private String username;
    private String email;
    private String password;
    private String role;
}
