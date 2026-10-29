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
import org.truong.gvrp_entry_api.dto.request.OrderImportRequest;
import org.truong.gvrp_entry_api.dto.response.ImportResultDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.mapper.OrderMapper;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.service.GeocodingService;
import org.truong.gvrp_entry_api.service.integration.file.CsvFileParser;
import org.truong.gvrp_entry_api.service.integration.file.JsonFileParser;
import org.truong.gvrp_entry_api.service.integration.file.TextDataParser;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UO-T5 (UX-ORDERS-001, S2): text import rows get the dialog's delivery date
 * before Bean Validation, so valid rows are imported. Real text parser and
 * validator; repositories, mapper and geocoder are mocks.
 */
@ExtendWith(MockitoExtension.class)
class OrderImportDeliveryDateTest {

    private static final Long BRANCH = 1L;
    private static final LocalDate DATE = LocalDate.of(2026, 10, 29);

    @Mock private GeocodingService geocodingService;
    @Mock private BranchRepository branchRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderMapper orderMapper;
    @Mock private CsvFileParser csvFileParser;
    @Mock private JsonFileParser jsonFileParser;

    private ValidatorFactory factory;
    private OrderImportService service;

    @BeforeEach
    void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        service = new OrderImportService(new ObjectMapper().findAndRegisterModules(), geocodingService,
                branchRepository, orderRepository, orderMapper, csvFileParser, jsonFileParser,
                new TextDataParser(), factory.getValidator());
    }

    @AfterEach
    void tearDown() {
        factory.close();
    }

    @Test
    void uoT5_twoValidTextRowsWithAddresses_areImported() {
        Branch branch = Branch.builder().id(BRANCH).build();
        when(branchRepository.findById(BRANCH)).thenReturn(Optional.of(branch));
        when(geocodingService.geocode(anyString())).thenReturn(new GeocodingService.GeocodeResult(21.02, 105.85));
        when(orderRepository.existsByBranchIdAndOrderCode(anyLong(), anyString())).thenReturn(false);
        when(orderMapper.toEntity(any(), any(), any())).thenReturn(Order.builder().build());

        OrderImportRequest request = OrderImportRequest.builder()
                .textData("""
                        ORD-1|Nguyen Van A|0901234567|1 Trang Tien, Ha Noi|25.5|08:00|11:00|1|Call first|10
                        ORD-2|Tran Thi B||2 Hang Bai, Ha Noi|12|||2||5
                        """)
                .deliveryDate(DATE)
                .serviceTime(5)
                .build();

        ImportResultDTO result = service.createOrdersByImportRequest(request, BRANCH);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getImportedCount()).isEqualTo(2);
        verify(orderMapper, times(2)).toEntity(any(), eq(branch), eq(DATE));
    }
}
