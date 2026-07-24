package io.ten1010.common.apiclient;

import java.io.IOException;

public interface ApiCallback {

    void onFailure(IOException e);

    void onSuccess(ApiResponse apiResponse);

}
