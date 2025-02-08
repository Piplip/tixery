package com.nkd.event.payment;

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

    public static EventOperation buildOperation(Map<?, ?> data, EventOperationType type){
        switch (type){
            case EventOperationType.VIEW -> {
                return EventOperation.builder().data(data).type(EventOperationType.VIEW).build();
            }
            default -> throw new IllegalArgumentException("Invalid operation type");
        }
    }

}
