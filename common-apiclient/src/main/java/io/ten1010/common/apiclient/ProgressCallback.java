package io.ten1010.common.apiclient;

public interface ProgressCallback {

    void onUploadProgress(long bytesWritten, long contentLength, boolean done);

    void onDownloadProgress(long bytesWritten, long contentLength, boolean done);

}
