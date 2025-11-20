package org.truong.gvrp_entry_api.integration.file;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.truong.gvrp_entry_api.dto.response.ImportError;

import java.util.List;

@RequiredArgsConstructor
@Getter
public class ParseResult<T>{
    private final List<T> validItems;
    private final List<ImportError> errors;

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean isAllValid() {
        return errors.isEmpty();
    }

    public boolean isPartialSuccess() {
        return !validItems.isEmpty() && !errors.isEmpty();
    }
}
