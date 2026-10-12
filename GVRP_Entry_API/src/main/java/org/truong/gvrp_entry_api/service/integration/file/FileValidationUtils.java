package org.truong.gvrp_entry_api.service.integration.file;

import java.util.ArrayList;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

/**
 * Shared file validation for order import parsers.
 */
public final class FileValidationUtils {

    private FileValidationUtils() {
    }

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * Validate file before parsing, collecting all errors into a DataInvalidException.
     *
     * @param file uploaded file
     * @param extension expected lowercase extension including dot (e.g. ".csv", ".json")
     */
    public static void validate(MultipartFile file, String extension) {
        List<ErrorDetail> fileErrors = new ArrayList<>();

        if (file == null || file.isEmpty()) {
            fileErrors.add(ErrorDetail.builder()
                    .code(ErrorCode.EMPTY_FIELD_ERROR.getCode())
                    .message(ErrorCode.EMPTY_FIELD_ERROR.getMessage())
                    .resource(AppConstant.FILE)
                    .build());
        } else {
            if (file.getSize() > MAX_FILE_SIZE) {
                fileErrors.add(ErrorDetail.builder()
                        .code(ErrorCode.FILE_SIZE_EXCEEDED.getCode())
                        .message(ErrorCode.FILE_SIZE_EXCEEDED.getMessage())
                        .resource(AppConstant.FILE)
                        .build());
            }

            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(extension)) {
                fileErrors.add(ErrorDetail.builder()
                        .code(ErrorCode.VALIDATION_ERROR.getCode())
                        .message(ErrorCode.VALIDATION_ERROR.getMessage())
                        .resource(AppConstant.FILE)
                        .build());
            }
        }

        if (!fileErrors.isEmpty()) {
            throw new DataInvalidException(fileErrors);
        }
    }
}
