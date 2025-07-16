package io.ten1010.aipub.projectcontroller.configuration;

import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.aipub")
@Data
public class AipubProperties {

    @Nullable
    private Boolean enabled;
    @Nullable
    private String serverUrl;
    @Nullable
    private Boolean verifyingSsl;
    @Nullable
    private String username;
    @Nullable
    private String password;
    private List<String> reservedNamespace = new ArrayList<>();

}
