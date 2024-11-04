package io.ten1010.aipub.projectcontroller.domain.aipubbackend;

import io.kubernetes.client.openapi.models.RbacV1Subject;
import io.ten1010.aipub.projectcontroller.domain.k8s.DefaultSubjectResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.RbacSubjectUtils;

import java.util.Objects;
import java.util.Optional;

public class AipubSubjectResolver extends DefaultSubjectResolver {

    private final String oidcIssuerUrl;

    public AipubSubjectResolver(OpenidProviderInfoService openidProviderInfoService) {
        String url = openidProviderInfoService.getOpenidProviderInfo().getIssuerUri();
        Objects.requireNonNull(url);
        this.oidcIssuerUrl = url;
    }

    @Override
    protected Optional<RbacV1Subject> resolve(String aipubUser) {
        RbacV1Subject subject = RbacSubjectUtils.buildSubject(this.oidcIssuerUrl, aipubUser);
        return Optional.of(subject);
    }

}
