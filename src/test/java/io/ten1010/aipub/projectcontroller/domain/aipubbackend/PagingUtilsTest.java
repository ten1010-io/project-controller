package io.ten1010.aipub.projectcontroller.domain.aipubbackend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PagingUtilsTest {

  private final List<Integer> requestedOffsets = new ArrayList<>();
  private final List<Integer> requestedSizes = new ArrayList<>();

  /** 전체 {@code total} 건을 페이지로 나눠 주는 가짜 백엔드. */
  private PagingUtils.PageFetcher<Integer> backendWith(int total) {
    return (pageOffset, pageSize) -> {
      this.requestedOffsets.add(pageOffset);
      this.requestedSizes.add(pageSize);
      int from = Math.min(pageOffset * pageSize, total);
      int to = Math.min(from + pageSize, total);
      return IntStream.range(from, to).boxed().toList();
    };
  }

  @Test
  void lastPageShorterThanPageSize_stopsWithoutExtraCall() {
    List<Integer> items = PagingUtils.listAllPages("t", backendWith(150));

    assertThat(items).hasSize(150).doesNotHaveDuplicates();
    assertThat(this.requestedOffsets).containsExactly(0, 1);
  }

  @Test
  void emptyFirstPage_returnsEmpty() {
    assertThat(PagingUtils.listAllPages("t", backendWith(0))).isEmpty();
    assertThat(this.requestedOffsets).containsExactly(0);
  }

  @Test
  void exactMultipleOfPageSize_stopsAtFollowingEmptyPage() {
    List<Integer> items = PagingUtils.listAllPages("t", backendWith(PagingUtils.PAGE_SIZE * 2));

    assertThat(items).hasSize(PagingUtils.PAGE_SIZE * 2);
    assertThat(this.requestedOffsets).containsExactly(0, 1, 2);
  }

  @Test
  void requestsZeroBasedPageIndexWithHarborMaxPageSize() {
    // pageOffset 은 행 오프셋(0, 100, 200)이 아니라 페이지 번호여야 한다 — 백엔드가 +1 해 Harbor page 로 보낸다.
    PagingUtils.listAllPages("t", backendWith(250));

    assertThat(this.requestedOffsets).containsExactly(0, 1, 2);
    assertThat(this.requestedSizes).containsOnly(100);
  }

  @Test
  void alwaysFullPages_stopsAtPageCap() {
    List<Integer> items = PagingUtils.listAllPages("t", (pageOffset, pageSize) -> {
      this.requestedOffsets.add(pageOffset);
      return Collections.nCopies(pageSize, pageOffset);
    });

    assertThat(this.requestedOffsets).hasSize(PagingUtils.MAX_PAGES);
    assertThat(items).hasSize(PagingUtils.MAX_PAGES * PagingUtils.PAGE_SIZE);
  }

}
