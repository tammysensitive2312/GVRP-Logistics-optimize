package org.truong.gvrp_entry_api.service.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.truong.gvrp_entry_api.dto.request.OrderImportRequest;
import org.truong.gvrp_entry_api.dto.response.ImportResultDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.mapper.OrderMapper;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.service.GeocodingService;
import org.truong.gvrp_entry_api.service.integration.file.CsvFileParser;
import org.truong.gvrp_entry_api.service.integration.file.JsonFileParser;
import org.truong.gvrp_entry_api.service.integration.file.TextDataParser;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SEC-LOW-001 LOW-T6 (audit lead #10): an order import is bounded in rows
 * and in geocoding calls, and an import above a ceiling is refused before
 * any (paid) geocoding call or database write.
 *
 * <p>The geocoder is a Mockito mock: Goong is never called. Text rows carry
 * no coordinates, so every row is address-only and would be geocoded.
 * Customer data below is fictional.
 */
@ExtendWith(MockitoExtension.class)
class OrderImportLimitsTest {

    private static final Long BRANCH = 1L;
    private static final LocalDate DATE = LocalDate.of(2026, 11, 10);

    @Mock private GeocodingService geocodingService;
    @Mock private BranchRepository branchRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderMapper orderMapper;

    private ValidatorFactory factory;
    private OrderImportService service;

    @BeforeEach
    void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        service = new OrderImportService(new ObjectMapper().findAndRegisterModules(), geocodingService,
                branchRepository, orderRepository, orderMapper, new CsvFileParser(), new JsonFileParser(),
                new TextDataParser(), factory.getValidator());
        when(branchRepository.findById(BRANCH)).thenReturn(Optional.of(Branch.builder().id(BRANCH).build()));
        lenient().when(geocodingService.geocode(anyString()))
                .thenReturn(new GeocodingService.GeocodeResult(21.0285, 105.8542));
        lenient().when(orderRepository.existsByBranchIdAndOrderCode(anyLong(), anyString())).thenReturn(false);
        lenient().when(orderMapper.toEntity(any(), any(), any())).thenReturn(Order.builder().build());
    }

    @AfterEach
    void tearDown() {
        factory.close();
    }

    /** {@code count} valid address-only text rows. */
    private static String rows(int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            text.append("LIM-").append(i).append("|Customer ").append(i)
                    .append("|0900000000|").append(i).append(" Example Street, Ha Noi|1|||1||5\n");
        }
        return text.toString();
    }

    private static OrderImportRequest textRequest(String text) {
        return OrderImportRequest.builder()
                .textData(text).deliveryDate(DATE).serviceTime(5)
                .skipValidationErrors(false)
                .build();
    }

    private static ErrorDetail onlyDetail(Throwable ex) {
        assertThat(((DataInvalidException) ex).getErrors()).hasSize(1);
        return ((DataInvalidException) ex).getErrors().get(0);
    }

    // ---------- default ceilings (no configuration) ----------

    @Test
    void defaultRowCeiling_refusesBeforeGeocodingAndDatabase() {
        assertThatThrownBy(() -> service.createOrdersByImportRequest(textRequest(rows(10_001)), BRANCH))
                .isInstanceOf(DataInvalidException.class)
                .satisfies(ex -> {
                    ErrorDetail detail = onlyDetail(ex);
                    assertThat(detail.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
                    assertThat(detail.getField()).isEqualTo(AppConstant.FILE);
                    assertThat(detail.getMessage()).contains("10001").contains("10000");
                });

        verifyNoInteractions(geocodingService);
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(orderMapper);
    }

    @Test
    void defaultGeocodingCeiling_refusesBeforeAnyGeocodingCall() {
        assertThatThrownBy(() -> service.createOrdersByImportRequest(textRequest(rows(5_001)), BRANCH))
                .isInstanceOf(DataInvalidException.class)
                .satisfies(ex -> {
                    ErrorDetail detail = onlyDetail(ex);
                    assertThat(detail.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
                    assertThat(detail.getField()).isEqualTo(AppConstant.FILE);
                    assertThat(detail.getMessage()).contains("5001").contains("5000");
                });

        verifyNoInteractions(geocodingService);
        verifyNoInteractions(orderRepository);
    }

    // ---------- configured ceilings ----------

    @Test
    void configuredRowCeiling_atTheCeiling_importsAsBefore() {
        ReflectionTestUtils.setField(service, "maxImportRows", 3);

        ImportResultDTO result = service.createOrdersByImportRequest(textRequest(rows(3)), BRANCH);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getImportedCount()).isEqualTo(3);
        verify(geocodingService, times(3)).geocode(anyString());
        verify(orderRepository, times(3)).save(any());
    }

    @Test
    void configuredRowCeiling_oneAbove_refused() {
        ReflectionTestUtils.setField(service, "maxImportRows", 3);

        assertThatThrownBy(() -> service.createOrdersByImportRequest(textRequest(rows(4)), BRANCH))
                .isInstanceOf(DataInvalidException.class);

        verifyNoInteractions(geocodingService);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void configuredGeocodingCeiling_oneAbove_refused() {
        ReflectionTestUtils.setField(service, "maxGeocodeRows", 2);

        assertThatThrownBy(() -> service.createOrdersByImportRequest(textRequest(rows(3)), BRANCH))
                .isInstanceOf(DataInvalidException.class)
                .satisfies(ex -> assertThat(onlyDetail(ex).getMessage()).contains("geocoding"));

        verifyNoInteractions(geocodingService);
        verify(orderRepository, never()).save(any());
    }
}
