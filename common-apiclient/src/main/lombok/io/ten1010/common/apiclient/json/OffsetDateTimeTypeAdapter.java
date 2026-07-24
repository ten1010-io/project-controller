package io.ten1010.common.apiclient.json;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

@Getter
@Setter
class OffsetDateTimeTypeAdapter extends TypeAdapter<OffsetDateTime> {

    private DateTimeFormatter formatter;

    public OffsetDateTimeTypeAdapter() {
        this.formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    }

    @Override
    public void write(JsonWriter out, @Nullable OffsetDateTime date) throws IOException {
        if (date == null) {
            out.nullValue();
        } else {
            out.value(formatter.format(date));
        }
    }

    @Override
    @Nullable
    public OffsetDateTime read(JsonReader in) throws IOException {
        if (Objects.requireNonNull(in.peek()) == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String date = in.nextString();
        if (date.endsWith("+0000")) {
            date = date.substring(0, date.length() - 5) + "Z";
        }
        try {
            return OffsetDateTime.parse(date, formatter);
        } catch (DateTimeParseException e) {
            // backward-compatibility for ISO8601 timestamp format
            return OffsetDateTime.parse(date, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }

}
