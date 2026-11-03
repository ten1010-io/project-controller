package io.ten1010.aipub.projectcontroller.domain.aipubbackend;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class PagingUtils {

  /** 백엔드가 Harbor 상한(100)으로 잘라 보내므로 이보다 크게 잡아도 한 번에 더 받지 못한다. */
  public static final int PAGE_SIZE = 100;

  /** 페이지마다 백엔드 호출이 직렬로 하나씩 붙는다. 웹훅 timeout(10초) 안에 끝나도록 상한을 둔다. */
  public static final int MAX_PAGES = 10;

  @FunctionalInterface
  public interface PageFetcher<T> {

    /**
     * @param pageOffset 행 오프셋이 아니라 0부터 세는 페이지 번호다. 백엔드가 Harbor page 로 +1 해 보낸다.
     */
    List<T> fetch(int pageOffset, int pageSize);

  }

  /**
   * 마지막 페이지(PAGE_SIZE 미만)까지 이어 받는다. {@link #MAX_PAGES} 에 닿으면 거기까지만 돌려준다.
   *
   * <p>생략된 pageSize 는 게이트웨이가 50 으로 채우므로, 한 번만 부르면 그 뒤 항목은 조용히 빠진다.
   */
  public static <T> List<T> listAllPages(String target, PageFetcher<T> fetcher) {
    List<T> result = new ArrayList<>();
    for (int pageOffset = 0; pageOffset < MAX_PAGES; pageOffset++) {
      List<T> page = fetcher.fetch(pageOffset, PAGE_SIZE);
      result.addAll(page);
      if (page.size() < PAGE_SIZE) {
        return result;
      }
    }
    log.warn("Stopped listing {} at the page cap {}", target, MAX_PAGES);
    return result;
  }

}
