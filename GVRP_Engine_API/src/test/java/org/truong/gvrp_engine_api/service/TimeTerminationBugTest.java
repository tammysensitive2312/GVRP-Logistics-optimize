package org.truong.gvrp_engine_api.service;

import com.graphhopper.jsprit.core.algorithm.SearchStrategy;
import com.graphhopper.jsprit.core.algorithm.termination.TimeTermination;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test xac nhan gia thuyet:
 *
 * TimeTermination implements ca PrematureAlgorithmTermination va AlgorithmStartsListener.
 * Neu chi goi algorithm.setPrematureAlgorithmTermination(timeoutTermination) ma KHONG
 * goi algorithm.addListener(timeoutTermination), thi informAlgorithmStarts() se KHONG
 * duoc goi -> startTime giu gia tri mac dinh cua long la 0.
 *
 * Khi do isPrematureBreak() se tinh:
 *   now() - 0 > timeThreshold
 * -> System.currentTimeMillis() (~ 1.7 ty giay) chac chan > bat ky timeThreshold nao
 * -> luon tra ve true ngay tu lan goi dau tien, du timeThreshold la 480000ms hay bat ky gia tri nao.
 *
 * Day chinh la nguyen nhan khien thuat toan dung o "iteration 1" trong log thuc te.
 */
class TimeTerminationBugTest {

    @Test
    void isPrematureBreak_shouldReturnFalse_whenJustCreated_ifStartTimeNeverSet() {
        // Given: TimeTermination duoc tao voi threshold rat lon (480 giay)
        // nhung KHONG duoc dang ky qua addListener() -> informAlgorithmStarts() khong duoc goi
        long timeoutMs = 480_000L; // 480 giay, giong config thuc te cua Truong
        TimeTermination timeTermination = new TimeTermination(timeoutMs);

        // When: goi isPrematureBreak() ngay lap tuc, gia lap iteration dau tien
        // (discoveredSolution khong duoc TimeTermination su dung trong logic,
        //  nen truyen null la an toan cho muc dich test nay)
        boolean prematureBreak = timeTermination.isPrematureBreak(
                (SearchStrategy.DiscoveredSolution) null
        );

        // Then (BUG HIEN TAI): vi startTime chua bao gio duoc set (mac dinh = 0),
        // now() - 0 se la mot so cuc lon (epoch millis hien tai),
        // luon > timeThreshold -> tra ve true ngay lap tuc.
        //
        // Neu assertion nay PASS, gia thuyet duoc xac nhan: bug la do thieu addListener().
        assertThat(prematureBreak)
                .as("BUG: isPrematureBreak() tra true ngay lap tuc vi startTime chua duoc " +
                        "khoi tao qua informAlgorithmStarts() (thieu goi algorithm.addListener())")
                .isTrue();

        // Doi chieu: getStartTime() phai la 0 (gia tri mac dinh, chua bao gio duoc set)
        assertThat(timeTermination.getStartTime())
                .as("Xac nhan startTime van la 0 vi informAlgorithmStarts() chua duoc goi")
                .isEqualTo(0L);
    }

    @Test
    void isPrematureBreak_shouldReturnFalse_whenStartTimeIsSetCorrectly() {
        // Given: mo phong dung cach Jsprit se goi khi TimeTermination duoc dang ky
        // qua algorithm.addListener(timeoutTermination) - tuc la informAlgorithmStarts()
        // duoc goi ngay khi searchSolutions() bat dau.
        long timeoutMs = 480_000L;
        TimeTermination timeTermination = new TimeTermination(timeoutMs);

        // Gia lap Jsprit goi informAlgorithmStarts() dung luc searchSolutions() bat dau
        timeTermination.informAlgorithmStarts(null, null, null);

        // When: goi isPrematureBreak() ngay sau do (chua co thoi gian nao troi qua dang ke)
        boolean prematureBreak = timeTermination.isPrematureBreak(
                (SearchStrategy.DiscoveredSolution) null
        );

        // Then (HANH VI DUNG): vi startTime vua duoc set = now(),
        // now() - startTime xap xi 0ms, chac chan < 480000ms -> tra ve false.
        assertThat(prematureBreak)
                .as("FIX: khi startTime duoc set dung qua informAlgorithmStarts() " +
                        "(tuc la da addListener()), thuat toan KHONG dung ngay lap tuc")
                .isFalse();

        assertThat(timeTermination.getStartTime())
                .as("startTime phai duoc set gan voi thoi diem hien tai, khong con la 0")
                .isGreaterThan(0L);
    }
}