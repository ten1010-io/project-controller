package io.ten1010.common.apiclient;

import java.util.Map;

public interface Authentication {

    void apply(Map<String, String> queryParams, Map<String, String> headerParams, Map<String, String> cookieParams);

}
