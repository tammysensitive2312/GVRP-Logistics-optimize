package org.truong.gvrp_entry_api.service.integration.file;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.truong.gvrp_entry_api.dto.request.OrderInputDTO;
import org.truong.gvrp_entry_api.dto.response.ImportError;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** UO-T6 (UX-ORDERS-001, S2): optional latitude/longitude CSV columns. */
class CsvFileParserCoordinatesTest {

    private static final String HEADER =
            "orderCode,customerName,customerPhone,address,latitude,longitude,demand,serviceTime,"
                    + "timeWindowStart,timeWindowEnd,priority,deliveryNotes,requiredSkills\n";

    private final CsvFileParser parser = new CsvFileParser();

    private static MockMultipartFile csv(String rows) {
        return new MockMultipartFile("file", "orders.csv", "text/csv",
                (HEADER + rows).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void uoT6_coordinatesAreParsed_andBlankCoordinatesAreNull() {
        ParseResult<OrderInputDTO> result = parser.parse(csv("""
                ORD-1,Nguyen Van A,0901234567,"1 Trang Tien, Ha Noi",21.0245,105.8572,25.5,10,08:00,11:00,1,Call first,
                ORD-2,Tran Thi B,,"2 Hang Bai, Ha Noi",,,12,5,,,2,,
                """));

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValidItems()).hasSize(2);
        assertThat(result.getValidItems().get(0).getLatitude()).isEqualTo(21.0245);
        assertThat(result.getValidItems().get(0).getLongitude()).isEqualTo(105.8572);
        assertThat(result.getValidItems().get(1).getLatitude()).isNull();
        assertThat(result.getValidItems().get(1).getLongitude()).isNull();
    }

    @Test
    void uoT6_onlyLatitude_isAnErrorRow() {
        ParseResult<OrderInputDTO> result = parser.parse(csv("""
                ORD-3,C,,"3 Ly Thai To, Ha Noi",21.03,,10,5,,,1,,
                """));

        assertThat(result.getValidItems()).isEmpty();
        assertThat(result.getErrors()).hasSize(1);
        ImportError error = result.getErrors().get(0);
        assertThat(error.getLineNumber()).isEqualTo(2);
        assertThat(error.getErrorMessage()).contains("latitude and longitude must be given together");
    }

    @Test
    void uoT6_nonNumericLatitude_isAnErrorRowNamingTheColumn() {
        ParseResult<OrderInputDTO> result = parser.parse(csv("""
                ORD-4,D,,"4 Trang Thi, Ha Noi",abc,105.85,10,5,,,1,,
                """));

        assertThat(result.getValidItems()).isEmpty();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getField()).isEqualTo("latitude");
        assertThat(result.getErrors().get(0).getErrorMessage()).contains("latitude");
    }

    @Test
    void uoT6_fileWithoutCoordinateColumns_stillParses() {
        MockMultipartFile file = new MockMultipartFile("file", "orders.csv", "text/csv", """
                orderCode,customerName,address,demand
                ORD-5,E,"5 Hue, Ha Noi",7
                """.getBytes(StandardCharsets.UTF_8));

        ParseResult<OrderInputDTO> result = parser.parse(file);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValidItems()).hasSize(1);
        assertThat(result.getValidItems().get(0).getLatitude()).isNull();
    }
}
