package org.truong.gvrp_entry_api.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaginationUtilsTest {

    @Test
    void pageRequest_validBounds_returnsRequestedPageable() {
        Pageable pageable = PaginationUtils.pageRequest(2, 50, 1000, Sort.by("id").descending());

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(50);
    }

    @Test
    void pageRequest_negativePage_throwsUnsupportedValueWithPageField() {
        DataInvalidException ex = assertThrows(DataInvalidException.class,
                () -> PaginationUtils.pageRequest(-1, 10, 1000, Sort.by("id").descending()));

        ErrorDetail detail = ex.getErrors().get(0);
        assertThat(detail.getCode()).isEqualTo(ErrorCode.UNSUPPORTED_VALUE.getCode());
        assertThat(detail.getField()).isEqualTo("page");
    }

    @Test
    void pageRequest_outOfRangeSize_throwsUnsupportedValueWithSizeField() {
        for (int badSize : new int[]{0, -5, 1001}) {
            DataInvalidException ex = assertThrows(DataInvalidException.class,
                    () -> PaginationUtils.pageRequest(0, badSize, 1000, Sort.by("id").descending()));
            ErrorDetail detail = ex.getErrors().get(0);
            assertThat(detail.getCode()).isEqualTo(ErrorCode.UNSUPPORTED_VALUE.getCode());
            assertThat(detail.getField()).isEqualTo("size");
        }
    }

    @Test
    void limitRequest_outOfRangeLimit_throwsUnsupportedValueWithLimitField() {
        for (int badLimit : new int[]{0, -1, 201}) {
            DataInvalidException ex = assertThrows(DataInvalidException.class,
                    () -> PaginationUtils.limitRequest(badLimit, 200));
            ErrorDetail detail = ex.getErrors().get(0);
            assertThat(detail.getCode()).isEqualTo(ErrorCode.UNSUPPORTED_VALUE.getCode());
            assertThat(detail.getField()).isEqualTo("limit");
        }
    }

    @Test
    void limitRequest_validBounds_returnsFirstPageWithLimit() {
        Pageable pageable = PaginationUtils.limitRequest(200, 200);

        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(200);
    }
}
