package io.ten1010.common.apiclient;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.*;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

public class ProgressResponseBody extends ResponseBody {

    private final ResponseBody responseBody;
    private final ProgressCallback callback;
    private final BufferedSource bufferedSource;

    public ProgressResponseBody(ResponseBody responseBody, ProgressCallback callback) {
        this.responseBody = responseBody;
        this.callback = callback;
        this.bufferedSource = Okio.buffer(source(responseBody.source()));
    }

    @Override
    @Nullable
    public MediaType contentType() {
        return this.responseBody.contentType();
    }

    @Override
    public long contentLength() {
        return this.responseBody.contentLength();
    }

    @Override
    public BufferedSource source() {
        return this.bufferedSource;
    }

    private Source source(Source source) {
        return new ForwardingSource(source) {

            long totalBytesRead = 0L;

            @Override
            public long read(Buffer sink, long byteCount) throws IOException {
                long bytesRead = super.read(sink, byteCount);
                // read() returns the number of bytes read, or -1 if this source is exhausted.
                this.totalBytesRead += bytesRead != -1 ? bytesRead : 0;
                callback.onDownloadProgress(this.totalBytesRead, responseBody.contentLength(), bytesRead == -1);
                return bytesRead;
            }

        };
    }
}
