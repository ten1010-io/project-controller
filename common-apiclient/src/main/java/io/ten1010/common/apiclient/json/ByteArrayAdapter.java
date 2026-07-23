package io.ten1010.common.apiclient.json;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import okio.ByteString;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

class ByteArrayAdapter extends TypeAdapter<byte[]> {

    @Override
    public void write(JsonWriter out, byte @Nullable [] value) throws IOException {
        boolean oldHtmlSafe = out.isHtmlSafe();
        out.setHtmlSafe(false);
        if (value == null) {
            out.nullValue();
        } else {
            out.value(ByteString.of(value).base64());
        }
        out.setHtmlSafe(oldHtmlSafe);
    }

    @Override
    public byte @Nullable [] read(JsonReader in) throws IOException {
        if (Objects.requireNonNull(in.peek()) == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String bytesAsBase64 = in.nextString();
        ByteString byteString = ByteString.decodeBase64(bytesAsBase64);
        Objects.requireNonNull(byteString);
        return byteString.toByteArray();
    }

}
