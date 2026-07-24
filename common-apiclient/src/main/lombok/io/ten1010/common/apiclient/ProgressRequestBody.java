package io.ten1010.common.apiclient;

import lombok.AllArgsConstructor;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.*;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

@AllArgsConstructor
public class ProgressRequestBody extends RequestBody {

    private final RequestBody requestBody;
    private final ProgressCallback callback;

    @Override
    @Nullable
    public MediaType contentType() {
        return this.requestBody.contentType();
    }

    @Override
    public long contentLength() throws IOException {
        return this.requestBody.contentLength();
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        BufferedSink bufferedSink = Okio.buffer(sink(sink));
        this.requestBody.writeTo(bufferedSink);
        bufferedSink.flush();
    }

    private Sink sink(Sink sink) {
        return new ForwardingSink(sink) {

            long contentLength = 0L;
            long bytesWritten = 0L;

            @Override
            public void write(Buffer source, long byteCount) throws IOException {
                super.write(source, byteCount);
                if (this.contentLength == 0) {
                    this.contentLength = contentLength();
                }

                this.bytesWritten += byteCount;
                callback.onUploadProgress(this.bytesWritten, this.contentLength, bytesWritten == contentLength);
            }

        };
    }

}
