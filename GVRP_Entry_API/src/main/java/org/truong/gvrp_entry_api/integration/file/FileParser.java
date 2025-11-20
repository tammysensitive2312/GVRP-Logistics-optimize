package org.truong.gvrp_entry_api.integration.file;

import org.springframework.web.multipart.MultipartFile;
import org.truong.gvrp_entry_api.exception.InvalidFileFormatException;

import java.io.IOException;

public interface FileParser<T> {
    ParseResult<T> parse(MultipartFile file) throws InvalidFileFormatException, IOException;
}
