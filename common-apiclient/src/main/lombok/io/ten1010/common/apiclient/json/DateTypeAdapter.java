package io.ten1010.common.apiclient.json;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Objects;

@Getter
@Setter
class DateTypeAdapter extends TypeAdapter<Date> {

    @Nullable
    private DateFormat dateFormat;

    @Override
    public void write(JsonWriter out, @Nullable Date date) throws IOException {
        if (date == null) {
            out.nullValue();
        } else {
            String value;
            if (dateFormat != null) {
                value = dateFormat.format(date);
            } else {
                value = DateTimeFormatter.ISO_INSTANT.format(date.toInstant());
            }
            out.value(value);
        }
    }

    @Override
    @Nullable
    public Date read(JsonReader in) throws IOException {
        try {
            if (Objects.requireNonNull(in.peek()) == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String date = in.nextString();
            try {
                if (dateFormat != null) {
                    return dateFormat.parse(date);
                }
                return Date.from(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(date)));
            } catch (ParseException e) {
                throw new JsonParseException(e);
            }
        } catch (IllegalArgumentException e) {
            throw new JsonParseException(e);
        }
    }

}
