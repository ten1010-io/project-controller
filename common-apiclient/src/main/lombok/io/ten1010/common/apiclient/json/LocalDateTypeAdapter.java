package io.ten1010.common.apiclient.json;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Getter
@Setter
class LocalDateTypeAdapter extends TypeAdapter<LocalDate> {

    private DateTimeFormatter formatter;

    public LocalDateTypeAdapter() {
        this.formatter = DateTimeFormatter.ISO_LOCAL_DATE;
    }

    @Override
    public void write(JsonWriter out, @Nullable LocalDate date) throws IOException {
        if (date == null) {
            out.nullValue();
        } else {
            out.value(formatter.format(date));
        }
    }

    @Override
    @Nullable
    public LocalDate read(JsonReader in) throws IOException {
        if (Objects.requireNonNull(in.peek()) == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String date = in.nextString();
        return LocalDate.parse(date, formatter);
    }

}
