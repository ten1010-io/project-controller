package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import io.kubernetes.client.common.KubernetesObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * project controller가 reconcile/mutating 대상에서 제외할 워크로드를 라벨 셀렉터로 판정한다.
 *
 * <p>각 셀렉터 문자열은 다음 두 형태를 지원한다.
 * <ul>
 *   <li>{@code "key=value"} : 라벨 {@code key} 의 값이 {@code value} 와 정확히 일치하면 매칭</li>
 *   <li>{@code "key"} : 라벨 {@code key} 가 존재하면(값 무관) 매칭</li>
 * </ul>
 *
 * <p>여러 셀렉터 중 하나라도 매칭되면 제외 대상으로 본다(OR). KubeVirt(virt-operator)처럼
 * 자체 컴포넌트를 직접 소유하는 인프라 오퍼레이터의 워크로드를 project controller가 건드리지
 * 않도록 하기 위한 용도다. 제외 대상 목록은 {@code app.aipub.reconcile-excluded-label-selectors}
 * 설정으로 주입되며, 책임과 관리 주체를 project controller로 일원화한다.
 */
public class WorkloadExclusionResolver {

  private record LabelSelector(String key, String value) {

    boolean matches(Map<String, String> labels) {
      if (!labels.containsKey(key)) {
        return false;
      }
      return value == null || value.equals(labels.get(key));
    }
  }

  private final List<LabelSelector> selectors;

  public WorkloadExclusionResolver(List<String> rawSelectors) {
    this.selectors = parse(rawSelectors);
  }

  private static List<LabelSelector> parse(List<String> rawSelectors) {
    List<LabelSelector> parsed = new ArrayList<>();
    if (rawSelectors == null) {
      return parsed;
    }
    for (String raw : rawSelectors) {
      if (raw == null) {
        continue;
      }
      String trimmed = raw.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      int idx = trimmed.indexOf('=');
      if (idx < 0) {
        parsed.add(new LabelSelector(trimmed, null));
      } else {
        String key = trimmed.substring(0, idx).trim();
        String value = trimmed.substring(idx + 1).trim();
        if (!key.isEmpty()) {
          parsed.add(new LabelSelector(key, value));
        }
      }
    }
    return parsed;
  }

  public boolean isExcluded(KubernetesObject object) {
    if (selectors.isEmpty() || object.getMetadata() == null) {
      return false;
    }
    Map<String, String> labels = object.getMetadata().getLabels();
    if (labels == null || labels.isEmpty()) {
      return false;
    }
    return selectors.stream().anyMatch(selector -> selector.matches(labels));
  }
}
