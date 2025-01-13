package com.nkd.event.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.jooq.JSONB;

import java.io.IOException;

public class CustomJSONSerializer extends StdSerializer<JSONB> {

    public CustomJSONSerializer() {
        super(JSONB.class);
    }

    @Override
    public void serialize(JSONB value, JsonGenerator generator, SerializerProvider serializer) throws IOException {
        generator.writeRawValue(value.data());
    }
}
