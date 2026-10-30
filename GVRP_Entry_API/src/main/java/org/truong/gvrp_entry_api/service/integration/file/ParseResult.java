package org.truong.gvrp_entry_api.service.integration.file;

import lombok.Getter;
import org.truong.gvrp_entry_api.dto.response.ImportError;

import java.util.List;

@Getter
public class ParseResult<T> {
    private final List<T> validItems;
    private final List<ImportError> errors;
    private final List<Integer> sourceLines;

    public ParseResult(List<T> validItems, List<ImportError> errors) {
        this(validItems, errors, null);
    }

    public ParseResult(List<T> validItems, List<ImportError> errors, List<Integer> sourceLines) {
        this.validItems = validItems;
        this.errors = errors;
        this.sourceLines = sourceLines;
    }

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
