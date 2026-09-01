package io.ten1010.aipub.projectcontroller.domain.k8s;

import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * 네임스페이스가 project controller 관리에서 allowlist되었는지 판정한다.
 *
 * <p>{@code project.ten1010.io/allowlisted: "true"} 라벨이 붙은 네임스페이스를 allowlist로 본다.
 * project 네임스페이스 밖에서 동작하면서도 project-managed 노드에 파드를 올려야 하는 시스템
 * 컴포넌트를 위한 것이다. allowlist 네임스페이스는 reconcile과 eviction에서 빠지고, 그 파드에는
 * 어느 노드의 project-managed taint든 견디는 {@code Exists} toleration이 붙는다.
 *
 * <p>판정은 네임스페이스 informer 캐시를 직접 읽으므로 라벨 변경이 재시작 없이 런타임에 반영된다.
 * 캐시에 없는 네임스페이스는 allowlist가 아닌 것으로 본다(fail-closed).
 */
public class NamespaceAllowlistResolver {

  private static final String ALLOWLISTED_VALUE = "true";

  private final KeyResolver keyResolver;
  private final Indexer<V1Namespace> namespaceIndexer;

  public NamespaceAllowlistResolver(Indexer<V1Namespace> namespaceIndexer) {
    this.keyResolver = new KeyResolver();
    this.namespaceIndexer = namespaceIndexer;
  }

  public static boolean isAllowlisted(V1Namespace namespace) {
    Map<String, String> labels = K8sObjectUtils.getLabels(namespace);
    String value = labels.get(LabelConstants.ALLOWLISTED_KEY);
    if (value == null) {
      return false;
    }
    return value.equalsIgnoreCase(ALLOWLISTED_VALUE);
  }

  public boolean isAllowlisted(@Nullable String namespaceName) {
    if (namespaceName == null || namespaceName.isBlank()) {
      return false;
    }
    V1Namespace namespace = this.namespaceIndexer.getByKey(
        this.keyResolver.resolveKey(namespaceName));
    if (namespace == null) {
      return false;
    }
    return isAllowlisted(namespace);
  }

}
