package io.ten1010.common.apiclient;

import lombok.AllArgsConstructor;
import okhttp3.Credentials;

import java.util.Map;

@AllArgsConstructor
public class HttpBasicAuthentication implements Authentication {

    private String username;
    private String password;

    @Override
    public void apply(Map<String, String> queryParams, Map<String, String> headerParams, Map<String, String> cookieParams) {
        headerParams.put("Authorization", Credentials.basic(this.username, this.password));
    }

}
