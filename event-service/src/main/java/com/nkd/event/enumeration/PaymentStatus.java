package com.nkd.event.enumeration;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    USER_CANCELLED("User canceled payment"),
    INVALID_BALANCE("Invalid balance for payment");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }

}
