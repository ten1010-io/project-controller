package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1ResourceQuota;

import java.util.Map;

public abstract class ResourceQuotaUtils {

    public static Map<String, Quantity> getStatusHard(V1ResourceQuota object) {
        if (object.getStatus() == null ||
                object.getStatus().getHard() == null) {
            return Map.of();
        }
        return object.getStatus().getHard();
    }

    public static Map<String, Quantity> getStatusUsed(V1ResourceQuota object) {
        if (object.getStatus() == null ||
                object.getStatus().getUsed() == null) {
            return Map.of();
        }
        return object.getStatus().getUsed();
    }

}
