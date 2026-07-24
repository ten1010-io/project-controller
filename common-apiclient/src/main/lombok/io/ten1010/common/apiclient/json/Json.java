package io.ten1010.common.apiclient.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import io.gsonfire.GsonFireBuilder;
import lombok.Getter;
import lombok.Setter;

import java.io.StringReader;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Date;

public class Json {

    private static final DateTimeFormatter RFC3339MICRO_FORMATTER =
            new DateTimeFormatterBuilder()
                    .parseDefaulting(ChronoField.OFFSET_SECONDS, 0)
                    .append(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
                    .optionalStart()
                    .appendFraction(ChronoField.NANO_OF_SECOND, 6, 6, true)
                    .optionalEnd()
                    .appendOffsetId()
                    .toFormatter();

    private static GsonBuilder createGson() {
        return new GsonFireBuilder().createGsonBuilder();
    }

    @Getter
    @Setter
    private Gson gson;
    @Getter
    @Setter
    private boolean isLenientOnJson;
    private final DateTypeAdapter dateTypeAdapter;
    private final SqlDateTypeAdapter sqlDateTypeAdapter;
    private final OffsetDateTimeTypeAdapter offsetDateTimeTypeAdapter;
    private final LocalDateTypeAdapter localDateTypeAdapter;

    public Json() {
        this.isLenientOnJson = false;
        this.dateTypeAdapter = new DateTypeAdapter();
        this.sqlDateTypeAdapter = new SqlDateTypeAdapter();
        this.offsetDateTimeTypeAdapter = new OffsetDateTimeTypeAdapter();
        this.offsetDateTimeTypeAdapter.setFormatter(RFC3339MICRO_FORMATTER);
        this.localDateTypeAdapter = new LocalDateTypeAdapter();
        ByteArrayAdapter byteArrayAdapter = new ByteArrayAdapter();
        this.gson = createGson()
                .registerTypeAdapter(Date.class, this.dateTypeAdapter)
                .registerTypeAdapter(java.sql.Date.class, this.sqlDateTypeAdapter)
                .registerTypeAdapter(OffsetDateTime.class, this.offsetDateTimeTypeAdapter)
                .registerTypeAdapter(LocalDate.class, this.localDateTypeAdapter)
                .registerTypeAdapter(byte[].class, byteArrayAdapter)
                .create();
    }

    public String serialize(Object obj) {
        return this.gson.toJson(obj);
    }

    @SuppressWarnings("unchecked")
    public <T> T deserialize(String body, Type returnType) {
        try {
            if (this.isLenientOnJson) {
                JsonReader jsonReader = new JsonReader(new StringReader(body));
                jsonReader.setLenient(true);
                return this.gson.fromJson(jsonReader, returnType);
            } else {
                return this.gson.fromJson(body, returnType);
            }
        } catch (JsonParseException e) {
            if (returnType.equals(String.class)) {
                return (T) body;
            } else {
                throw (e);
            }
        }
    }

    public void setOffsetDateTimeFormat(DateTimeFormatter dateFormat) {
        this.offsetDateTimeTypeAdapter.setFormatter(dateFormat);
    }

    public void setLocalDateFormat(DateTimeFormatter dateFormat) {
        this.localDateTypeAdapter.setFormatter(dateFormat);
    }

    public void setDateFormat(DateFormat dateFormat) {
        this.dateTypeAdapter.setDateFormat(dateFormat);
    }

    public void setSqlDateFormat(DateFormat dateFormat) {
        this.sqlDateTypeAdapter.setDateFormat(dateFormat);
    }

}
