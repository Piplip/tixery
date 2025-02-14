package com.nkd.event.event;

import com.nkd.event.enumeration.EventOperationType;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventOperation {

    private Map<?, ?> data;
    private EventOperationType type;

}
