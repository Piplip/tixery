package com.nkd.accountservice.event;

import com.nkd.accountservice.enumeration.EventType;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserEvent<T> {

    private T payload;
    private EventType eventType;
    private Map<?,?> data;
}
