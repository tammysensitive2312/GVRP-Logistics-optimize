package org.truong.gvrp_entry_api.service.orders;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.truong.gvrp_entry_api.dto.request.OrderImportRequest;
import org.truong.gvrp_entry_api.dto.response.ImportError;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OF-T1, OF-T2, OF-T4…OF-T7 (ORDERS-FIX-001, design §3): ungeocodable rows are
 * not saved, every error carries the source line of its row, and neither the
 * logs nor {@code errors[]} carry customer data or exception text.
 *
 * <p>Same setup as {@link OrderImportDeliveryDateTest}: real parsers and a real
 * validator; repositories, mapper and geocoder are Mockito substitutes.
 * Customer data below is fictional.
 */
@ExtendWith(MockitoExtension.class)
class OrderImportRowsTest {

    private static final Long BRANCH = 1L;
    private static final LocalDate DATE = LocalDate.of(2026, 10, 29);

    private static final String NAME_A = "Nguyen Van A";
    private static final String PHONE_A = "0901234567";
    private static final String GOOD_ADDRESS = "1 Example Street, Ha Noi";
    private static final String NAME_B = "Tran Thi B";
    private static final String PHONE_B = "0912000111";
    private static final String BAD_ADDRESS = "99 Nowhere Lane, Atlantis";
    private static final double LAT = 21.0285;
    private static final double LNG = 105.8542;

    /** Two text rows: row 1 geocodable, row 2 not. */
    private static final String TWO_ROWS =
            "ORD-1|" + NAME_A + "|" + PHONE_A + "|" + GOOD_ADDRESS + "|25.5|08:00|11:00|1|Call first|10\n"
                    + "ORD-2|" + NAME_B + "|" + PHONE_B + "|" + BAD_ADDRESS + "|12|||2||5\n";

    @Mock private GeocodingService geocodingService;
    @Mock private BranchRepository branchRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderMapper orderMapper;

    private ValidatorFactory factory;
    private OrderImportService service;
    private Branch branch;

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        service = new OrderImportService(new ObjectMapper().findAndRegisterModules(), geocodingService,
                branchRepository, orderRepository, orderMapper, new CsvFileParser(), new JsonFileParser(),
                new TextDataParser(), factory.getValidator());

        branch = Branch.builder().id(BRANCH).build();
        when(branchRepository.findById(BRANCH)).thenReturn(Optional.of(branch));
        lenient().when(geocodingService.geocode(GOOD_ADDRESS)).thenReturn(new GeocodingService.GeocodeResult(LAT, LNG));
        lenient().when(geocodingService.geocode(BAD_ADDRESS)).thenReturn(null);
        lenient().when(orderRepository.existsByBranchIdAndOrderCode(anyLong(), anyString())).thenReturn(false);
        lenient().when(orderMapper.toEntity(any(), any(), any())).thenReturn(Order.builder().build());

        logger = (Logger) LoggerFactory.getLogger(OrderImportService.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        factory.close();
    }

    private static OrderImportRequest textRequest(String text, boolean skipErrors) {
        return OrderImportRequest.builder()
                .textData(text).deliveryDate(DATE).serviceTime(5)
                .skipValidationErrors(skipErrors)
                .build();
    }

    private static OrderImportRequest fileRequest(MockMultipartFile file, boolean skipErrors) {
        return OrderImportRequest.builder()
                .file(file).deliveryDate(DATE).serviceTime(5)
                .skipValidationErrors(skipErrors)
                .build();
    }

    private static List<Integer> lines(ImportResultDTO result) {
        return result.getErrors().stream().map(ImportError::getLineNumber).toList();
    }

    // ---------- OF-T1 / OF-T2 ----------

    @Test
    void ofT1_ungeocodableRowWithSkipOn_isReportedOnceAndNotSaved() {
        ImportResultDTO result = service.createOrdersByImportRequest(textRequest(TWO_ROWS, true), BRANCH);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getImportedCount()).isEqualTo(1);
        assertThat(result.getErrors()).hasSize(1);
        ImportError error = result.getErrors().get(0);
        assertThat(error.getLineNumber()).isEqualTo(2);
        assertThat(error.getField()).isEqualTo("address");
        assertThat(error.getOrderCode()).isEqualTo("ORD-2");
        assertThat(error.getErrorMessage()).doesNotContain(BAD_ADDRESS).doesNotContain("Nowhere");
        assertThat(error.getRawData()).isNull();
        verify(orderMapper, times(1)).toEntity(any(), eq(branch), eq(DATE));
        verify(orderRepository, times(1)).save(any());
    }

    @Test
    void ofT2_ungeocodableRowWithSkipOff_refusesTheWholeImport() {
        ImportResultDTO result = service.createOrdersByImportRequest(textRequest(TWO_ROWS, false), BRANCH);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getImportedCount()).isZero();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getLineNumber()).isEqualTo(2);
        verify(orderRepository, never()).save(any());
        verify(orderMapper, never()).toEntity(any(), any(), any());
    }

    // ---------- OF-T4 / OF-T5 / OF-T6: source line numbers ----------

    @Test
    void ofT4_csvErrorsCarryTheFileLine_andTheValidRowIsImported() {
        String csv = "orderCode,customerName,customerPhone,address,demand\n"
                + "ORD-A,Customer A,,\"" + GOOD_ADDRESS + "\",abc\n"        // line 2: bad demand
                + "ORD-B,Customer B,,\"" + BAD_ADDRESS + "\",10\n"          // line 3: ungeocodable
                + "ORD-C,Customer C,,\"" + GOOD_ADDRESS + "\",7\n";         // line 4: valid
        MockMultipartFile file = new MockMultipartFile("file", "orders.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        ImportResultDTO result = service.createOrdersByImportRequest(fileRequest(file, true), BRANCH);

        assertThat(lines(result)).containsExactlyInAnyOrder(2, 3);
        assertThat(result.getImportedCount()).isEqualTo(1);
        verify(orderRepository, times(1)).save(any());
    }

    @Test
    void ofT5_textWithABlankFirstLine_reportsTheRealLine() {
        String text = "\n"
                + "ORD-2|" + NAME_B + "|" + PHONE_B + "|" + BAD_ADDRESS + "|12|||2||5\n";

        ImportResultDTO result = service.createOrdersByImportRequest(textRequest(text, true), BRANCH);

        assertThat(lines(result)).containsExactly(2);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void ofT6_jsonErrorCarriesTheLineOfItsObject_notItsIndex() {
        String json = "[\n"
                + "  {\"orderCode\":\"ORD-J1\",\"customerName\":\"Customer J1\",\"address\":\"" + GOOD_ADDRESS
                + "\",\"demand\":5},\n"
                + "  {\"orderCode\":\"ORD-J2\",\"customerName\":\"Customer J2\",\"address\":\"" + BAD_ADDRESS
                + "\",\"demand\":6}\n"
                + "]\n";
        MockMultipartFile file = new MockMultipartFile("file", "orders.json", "application/json",
                json.getBytes(StandardCharsets.UTF_8));

        ImportResultDTO result = service.createOrdersByImportRequest(fileRequest(file, true), BRANCH);

        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getOrderCode()).isEqualTo("ORD-J2");
        assertThat(result.getErrors().get(0).getLineNumber())
                .as("the second object starts on line 3; its index would give 2")
                .isEqualTo(3);
        assertThat(result.getImportedCount()).isEqualTo(1);
    }

    // ---------- OF-T7: no personal data, no exception text ----------

    private List<String> sensitive() {
        return List.of(NAME_A, PHONE_A, GOOD_ADDRESS, NAME_B, PHONE_B, BAD_ADDRESS,
                String.valueOf(LAT), String.valueOf(LNG), "Duplicate entry");
    }

    private void assertNothingSensitive(ImportResultDTO result) {
        List<String> texts = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            texts.add(event.getFormattedMessage());
            if (event.getArgumentArray() != null) {
                for (Object argument : event.getArgumentArray()) {
                    texts.add(String.valueOf(argument));
                }
            }
            if (event.getThrowableProxy() != null) {
                texts.add(event.getThrowableProxy().getMessage());
            }
        }
        for (ImportError error : result.getErrors()) {
            texts.add(error.getErrorMessage());
            texts.add(String.valueOf(error.getRawData()));
        }
        for (String text : texts) {
            for (String value : sensitive()) {
                assertThat(text).as("log or error text").doesNotContain(value);
            }
        }
    }

    @Test
    void ofT7_importLogsAndErrorsCarryNoCustomerData() {
        ImportResultDTO result = service.createOrdersByImportRequest(textRequest(TWO_ROWS, true), BRANCH);

        assertThat(appender.list).isNotEmpty();
        assertNothingSensitive(result);
    }

    @Test
    void ofT7_databaseFailureIsGeneric_andCarriesTheRowLine() {
        when(orderRepository.save(any()))
                .thenThrow(new RuntimeException("Duplicate entry '" + NAME_A + "' for key 'uk_order'"));
        String text = "\n"
                + "ORD-1|" + NAME_A + "|" + PHONE_A + "|" + GOOD_ADDRESS + "|25.5|08:00|11:00|1|Call first|10\n";

        ImportResultDTO result = service.createOrdersByImportRequest(textRequest(text, true), BRANCH);

        assertThat(result.getErrors()).hasSize(1);
        ImportError error = result.getErrors().get(0);
        assertThat(error.getErrorMessage()).isEqualTo("Could not save this order.");
        assertThat(error.getField()).isEqualTo("Database");
        assertThat(error.getLineNumber()).isEqualTo(2);
        assertThat(appender.list).anyMatch(event -> event.getFormattedMessage().contains("RuntimeException"));
        assertNothingSensitive(result);
    }
}
