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
import java.sql.Date;
import java.text.DateFormat;
import java.text.ParseException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Getter
@Setter
class SqlDateTypeAdapter extends TypeAdapter<Date> {

    @Nullable
    private DateFormat dateFormat;

    @Override
    public void write(JsonWriter out, @Nullable Date date) throws IOException {
        if (date == null) {
            out.nullValue();
        } else {
            String value;
            if (this.dateFormat != null) {
                value = this.dateFormat.format(date);
            } else {
                value = date.toString();
            }
            out.value(value);
        }
    }

    @Override
    @Nullable
    public Date read(JsonReader in) throws IOException {
        if (Objects.requireNonNull(in.peek()) == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String date = in.nextString();
        try {
            if (this.dateFormat != null) {
                return new Date(this.dateFormat.parse(date).getTime());
            }
            return new Date(
                    Instant.from(DateTimeFormatter.ISO_INSTANT.parse(date)).toEpochMilli());
        } catch (ParseException e) {
            throw new JsonParseException(e);
        }
    }

}
