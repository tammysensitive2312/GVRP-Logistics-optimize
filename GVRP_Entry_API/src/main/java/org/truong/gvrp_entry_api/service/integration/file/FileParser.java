package org.truong.gvrp_entry_api.service.integration.file;

import org.springframework.web.multipart.MultipartFile;
import org.truong.gvrp_entry_api.exception.DataInvalidException;

import java.io.IOException;

public interface FileParser<T> {
    ParseResult<T> parse(MultipartFile file) throws DataInvalidException, IOException;
}
