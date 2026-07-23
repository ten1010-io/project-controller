package io.ten1010.common.apiclient;

import io.ten1010.common.apiclient.json.Json;
import lombok.Getter;
import lombok.Setter;
import okhttp3.*;
import okhttp3.internal.http.HttpMethod;
import okhttp3.internal.tls.OkHostnameVerifier;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;
import okio.BufferedSink;
import okio.Okio;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiClient {

    @Getter
    @Setter
    private String basePath;

    @Getter
    @Setter
    private OkHttpClient httpClient;

    @Getter
    @Setter
    private Json json;

    @Getter
    private boolean verifyingSsl;

    @Getter
    @Nullable
    private String sslCaCert;

    @Getter
    private KeyManager @Nullable [] keyManagers;

    @Getter
    @Setter
    @Nullable
    private Authentication authentication;

    @Getter
    private boolean debugging;

    @Getter
    @Setter
    @Nullable
    private String tempFolderPath;

    @Getter
    @Setter
    private Map<String, String> defaultHeaderMap;

    @Getter
    @Setter
    private Map<String, String> defaultCookieMap;

    @Nullable
    private HttpLoggingInterceptor loggingInterceptor;

    public ApiClient() {
        this.basePath = "http://localhost";
        this.json = new Json();
        this.verifyingSsl = true;
        this.debugging = false;
        this.defaultHeaderMap = new HashMap<>();
        this.defaultCookieMap = new HashMap<>();
        initHttpClient();
    }

    public Call buildCall(
            String path,
            String method) {
        Request request = buildRequest(
                path,
                method,
                new HashMap<>(),
                null,
                null,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                null);

        return httpClient.newCall(request);
    }

    public Call buildCall(
            String path,
            String method,
            Map<String, String> queryParams) {
        Request request = buildRequest(
                path,
                method,
                queryParams,
                null,
                null,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                null);

        return httpClient.newCall(request);
    }

    public Call buildCall(
            String path,
            String method,
            Map<String, String> queryParams,
            Map<String, String> headerParams) {
        Request request = buildRequest(
                path,
                method,
                queryParams,
                null,
                null,
                headerParams,
                new HashMap<>(),
                new HashMap<>(),
                null);

        return httpClient.newCall(request);
    }

    public Call buildCall(
            String path,
            String method,
            Map<String, String> queryParams,
            Map<String, String> headerParams,
            Map<String, String> cookieParams) {
        Request request = buildRequest(
                path,
                method,
                queryParams,
                null,
                null,
                headerParams,
                cookieParams,
                new HashMap<>(),
                null);

        return httpClient.newCall(request);
    }

    public Call buildCall(
            String path,
            String method,
            Map<String, String> queryParams,
            Map<String, String> headerParams,
            Map<String, String> cookieParams,
            @Nullable ProgressCallback callback) {
        Request request = buildRequest(
                path,
                method,
                queryParams,
                null,
                null,
                headerParams,
                cookieParams,
                new HashMap<>(),
                callback);

        return httpClient.newCall(request);
    }

    public Call buildCall(
            String path,
            String method,
            String contentType,
            Object body) {
        Request request = buildRequest(
                path,
                method,
                new HashMap<>(),
                contentType,
                body,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                null);

        return httpClient.newCall(request);
    }

    public Call buildCall(
            String path,
            String method,
            String contentType,
            Object body,
            Map<String, String> queryParams) {
        Request request = buildRequest(
                path,
                method,
                queryParams,
                contentType,
                body,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                null);

        return httpClient.newCall(request);
    }

    public Call buildCall(
            String path,
            String method,
            String contentType,
            Object body,
            Map<String, String> queryParams,
            Map<String, String> headerParams) {
        Request request = buildRequest(
                path,
                method,
                queryParams,
                contentType,
                body,
                headerParams,
                new HashMap<>(),
                new HashMap<>(),
                null);

        return httpClient.newCall(request);
    }

    public Call buildCall(
            String path,
            String method,
            String contentType,
            Object body,
            Map<String, String> queryParams,
            Map<String, String> headerParams,
            Map<String, String> cookieParams) {
        Request request = buildRequest(
                path,
                method,
                queryParams,
                contentType,
                body,
                headerParams,
                cookieParams,
                new HashMap<>(),
                null);

        return httpClient.newCall(request);
    }

    public Call buildCall(
            String path,
            String method,
            String contentType,
            Object body,
            Map<String, String> queryParams,
            Map<String, String> headerParams,
            Map<String, String> cookieParams,
            @Nullable ProgressCallback callback) {
        Request request = buildRequest(
                path,
                method,
                queryParams,
                contentType,
                body,
                headerParams,
                cookieParams,
                new HashMap<>(),
                callback);

        return httpClient.newCall(request);
    }

    public Call buildCall(
            String path,
            String method,
            String contentType,
            Map<String, Object> formParams,
            Map<String, String> queryParams,
            Map<String, String> headerParams,
            Map<String, String> cookieParams,
            @Nullable ProgressCallback callback) {
        Request request = buildRequest(
                path,
                method,
                queryParams,
                contentType,
                null,
                headerParams,
                cookieParams,
                formParams,
                callback);

        return httpClient.newCall(request);
    }

    public Call buildCall(
            String path,
            String method,
            @Nullable String contentType,
            @Nullable Object body,
            Map<String, String> queryParams,
            Map<String, String> headerParams,
            Map<String, String> cookieParams,
            Map<String, Object> formParams,
            @Nullable ProgressCallback callback) {
        Request request = buildRequest(
                path,
                method,
                queryParams,
                contentType,
                body,
                headerParams,
                cookieParams,
                formParams,
                callback);

        return httpClient.newCall(request);
    }

    public ApiResponse execute(Call call) throws IOException {
        try (Response response = call.execute()) {
            return new ApiResponse(response.code(), response.headers().toMultimap(), response.body() == null ? null : response.body().bytes());
        }
    }

    public void executeAsync(Call call, ApiCallback callback) {
        call.enqueue(
                new Callback() {

                    @Override
                    public void onFailure(Call call, IOException e) {
                        callback.onFailure(e);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        ApiResponse apiResponse = new ApiResponse(
                                response.code(),
                                response.headers().toMultimap(),
                                response.body() == null ? null : response.body().bytes());
                        callback.onSuccess(apiResponse);
                    }

                });
    }

    public File executeAndDownloadFile(Call call) throws IOException {
        try (Response response = call.execute()) {
            File file = prepareDownloadFile(response);
            BufferedSink sink = Okio.buffer(Okio.sink(file));
            Objects.requireNonNull(response.body());
            Objects.requireNonNull(response.body().source());
            sink.writeAll(response.body().source());
            sink.close();
            return file;
        }
    }

    public void setVerifyingSsl(boolean verifyingSsl) {
        this.verifyingSsl = verifyingSsl;
        applySslSettings();
    }

    public void setSslCaCert(String sslCaCert) {
        this.sslCaCert = sslCaCert;
        applySslSettings();
    }

    public void setKeyManagers(KeyManager[] managers) {
        this.keyManagers = managers;
        applySslSettings();
    }

    public void setDebugging(boolean debugging) {
        if (debugging != this.debugging) {
            if (debugging) {
                this.loggingInterceptor = new HttpLoggingInterceptor();
                this.loggingInterceptor.setLevel(Level.BODY);
                this.httpClient = this.httpClient.newBuilder().addInterceptor(this.loggingInterceptor).build();
            } else {
                this.httpClient.interceptors().remove(this.loggingInterceptor);
                this.loggingInterceptor = null;
            }
        }
        this.debugging = debugging;
    }

    private void initHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.addNetworkInterceptor(getProgressInterceptor());
        this.httpClient = builder.build();
    }

    private Interceptor getProgressInterceptor() {
        return chain -> {
            final Request request = chain.request();
            final Response originalResponse = chain.proceed(request);
            if (originalResponse.body() != null && request.tag() instanceof ProgressCallback callback) {
                return originalResponse
                        .newBuilder()
                        .body(new ProgressResponseBody(originalResponse.body(), callback))
                        .build();
            }
            return originalResponse;
        };
    }

    private void applySslSettings() {
        try {
            TrustManager[] trustManagers;
            HostnameVerifier hostnameVerifier;
            if (!verifyingSsl) {
                trustManagers = new TrustManager[]{
                        new X509TrustManager() {

                            @Override
                            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                            }

                            @Override
                            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                            }

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[]{};
                            }

                        }
                };
                hostnameVerifier = (hostname, session) -> true;
            } else {
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                if (this.sslCaCert == null) {
                    trustManagerFactory.init((KeyStore) null);
                } else {
                    char[] password = null;
                    CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                    Collection<? extends Certificate> certificates =
                            certificateFactory.generateCertificates(new ByteArrayInputStream(this.sslCaCert.getBytes()));
                    if (certificates.isEmpty()) {
                        throw new IllegalArgumentException("expected non-empty set of trusted certificates");
                    }
                    KeyStore caKeyStore = newEmptyKeyStore(password);
                    int index = 0;
                    for (Certificate certificate : certificates) {
                        String certificateAlias = "ca" + Integer.toString(index++);
                        caKeyStore.setCertificateEntry(certificateAlias, certificate);
                    }
                    trustManagerFactory.init(caKeyStore);
                }
                trustManagers = trustManagerFactory.getTrustManagers();
                hostnameVerifier = OkHostnameVerifier.INSTANCE;
            }

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(this.keyManagers, trustManagers, new SecureRandom());
            this.httpClient = httpClient
                    .newBuilder()
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0])
                    .hostnameVerifier(hostnameVerifier)
                    .build();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private KeyStore newEmptyKeyStore(char @Nullable [] password) throws GeneralSecurityException {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, password);
            return keyStore;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private void processHeaderParams(Map<String, String> headerParams, @Nullable String contentType, Request.Builder reqBuilder) {
        if (contentType != null) {
            reqBuilder.header("Content-Type", contentType);
        }
        for (Map.Entry<String, String> param : headerParams.entrySet()) {
            reqBuilder.header(param.getKey(), param.getValue());
        }
        for (Map.Entry<String, String> header : this.defaultHeaderMap.entrySet()) {
            if (!headerParams.containsKey(header.getKey())) {
                reqBuilder.header(header.getKey(), header.getValue());
            }
        }
    }

    private void processCookieParams(Map<String, String> cookieParams, Request.Builder reqBuilder) {
        for (Map.Entry<String, String> param : cookieParams.entrySet()) {
            reqBuilder.addHeader("Cookie", String.format("%s=%s", param.getKey(), param.getValue()));
        }
        for (Map.Entry<String, String> param : this.defaultCookieMap.entrySet()) {
            if (!cookieParams.containsKey(param.getKey())) {
                reqBuilder.addHeader("Cookie", String.format("%s=%s", param.getKey(), param.getValue()));
            }
        }
    }

    private boolean isJsonMime(String mime) {
        String jsonMime = "(?i)^(application/json|[^;/ \t]+/[^;/ \t]+[+]json)[ \t]*(;.*)?$";
        return mime.matches(jsonMime) || mime.equals("*/*");
    }

    private String buildUrl(String path, Map<String, String> queryParams) {
        final StringBuilder url = new StringBuilder();
        url.append(this.basePath).append(path);

        if (!queryParams.isEmpty()) {
            String prefix = path.contains("?") ? "&" : "?";
            for (Map.Entry<String, String> param : queryParams.entrySet()) {
                if (prefix != null) {
                    url.append(prefix);
                    prefix = null;
                } else {
                    url.append("&");
                }
                url.append(escapeString(param.getKey())).append("=").append(escapeString(param.getValue()));
            }
        }

        return url.toString();
    }

    private RequestBody buildRequestBodyFormEncoding(Map<String, Object> formParams) {
        FormBody.Builder formBuilder = new FormBody.Builder();
        for (Map.Entry<String, Object> param : formParams.entrySet()) {
            formBuilder.add(param.getKey(), parameterToString(param.getValue()));
        }
        return formBuilder.build();
    }

    private String parameterToString(@Nullable Object param) {
        if (param == null) {
            return "";
        } else if (param instanceof Date
                || param instanceof OffsetDateTime
                || param instanceof LocalDate) {
            String jsonStr = this.json.serialize(param);
            return jsonStr.substring(1, jsonStr.length() - 1);
        } else if (param instanceof Collection<?> collection) {
            StringBuilder b = new StringBuilder();
            for (Object o : collection) {
                if (!b.isEmpty()) {
                    b.append(",");
                }
                b.append(String.valueOf(o));
            }
            return b.toString();
        } else {
            return String.valueOf(param);
        }
    }

    private RequestBody buildRequestBodyMultipart(Map<String, Object> formParams) {
        MultipartBody.Builder mpBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM);
        for (Map.Entry<String, Object> param : formParams.entrySet()) {
            if (param.getValue() instanceof File file) {
                Headers partHeaders =
                        Headers.of(
                                "Content-Disposition",
                                "form-data; name=\"" + param.getKey() + "\"; filename=\"" + file.getName() + "\"");
                MediaType mediaType = MediaType.parse(guessContentTypeFromFile(file));
                mpBuilder.addPart(partHeaders, RequestBody.create(mediaType, file));
            } else {
                Headers partHeaders =
                        Headers.of("Content-Disposition", "form-data; name=\"" + param.getKey() + "\"");
                mpBuilder.addPart(
                        partHeaders, RequestBody.create(null, parameterToString(param.getValue())));
            }
        }
        return mpBuilder.build();
    }

    private String guessContentTypeFromFile(File file) {
        String contentType = URLConnection.guessContentTypeFromName(file.getName());
        if (contentType == null) {
            return "application/octet-stream";
        }

        return contentType;
    }

    private Request buildRequest(
            String path,
            String method,
            Map<String, String> queryParams,
            @Nullable String contentType,
            @Nullable Object body,
            Map<String, String> headerParams,
            Map<String, String> cookieParams,
            Map<String, Object> formParams,
            @Nullable ProgressCallback callback) {
        if (this.authentication != null) {
            this.authentication.apply(queryParams, headerParams, cookieParams);
        }

        final String url = buildUrl(path, queryParams);
        final Request.Builder reqBuilder = new Request.Builder().url(url);
        processHeaderParams(headerParams, contentType, reqBuilder);
        processCookieParams(cookieParams, reqBuilder);

        if (body != null && contentType == null) {
            throw new IllegalArgumentException("Content Type must be set when body is not null");
        }
        RequestBody reqBody;
        if (!HttpMethod.permitsRequestBody(method)) {
            reqBody = null;
        } else if (body == null) {
            reqBody = null;
        } else if ("application/x-www-form-urlencoded".equals(contentType)) {
            reqBody = buildRequestBodyFormEncoding(formParams);
        } else if ("multipart/form-data".equals(contentType)) {
            reqBody = buildRequestBodyMultipart(formParams);
        } else {
            reqBody = serialize(body, contentType);
        }

        if (callback != null) {
            reqBuilder.tag(callback);
            if (reqBody != null) {
                ProgressRequestBody progressRequestBody = new ProgressRequestBody(reqBody, callback);
                return reqBuilder.method(method, progressRequestBody).build();
            }
        }

        return reqBuilder.method(method, reqBody).build();
    }

    private File prepareDownloadFile(Response response) throws IOException {
        String filename = null;
        String contentDisposition = response.header("Content-Disposition");
        if (contentDisposition != null && !contentDisposition.isEmpty()) {
            Pattern pattern = Pattern.compile("filename=['\"]?([^'\"\\s]+)['\"]?");
            Matcher matcher = pattern.matcher(contentDisposition);
            if (matcher.find()) {
                filename = sanitizeFilename(matcher.group(1));
            }
        }

        String prefix = null;
        String suffix = null;
        if (filename == null) {
            prefix = "download-";
            suffix = "";
        } else {
            int pos = filename.lastIndexOf(".");
            if (pos == -1) {
                prefix = filename + "-";
            } else {
                prefix = filename.substring(0, pos) + "-";
                suffix = filename.substring(pos);
            }
            if (prefix.length() < 3) prefix = "download-";
        }

        if (this.tempFolderPath == null) {
            return File.createTempFile(prefix, suffix);
        }

        return File.createTempFile(prefix, suffix, new File(this.tempFolderPath));
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll(".*[/\\\\]", "");
    }

    private String escapeString(String str) {
        return URLEncoder.encode(str, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
    }

    private RequestBody serialize(Object obj, String contentType) {
        if (obj instanceof byte[]) {
            return RequestBody.create(MediaType.parse(contentType), (byte[]) obj);
        } else if (obj instanceof File) {
            return RequestBody.create(MediaType.parse(contentType), (File) obj);
        } else if (isJsonMime(contentType)) {
            return RequestBody.create(MediaType.parse(contentType), this.json.serialize(obj));
        } else {
            throw new IllegalArgumentException("Content type \"" + contentType + "\" is not supported");
        }
    }

}
