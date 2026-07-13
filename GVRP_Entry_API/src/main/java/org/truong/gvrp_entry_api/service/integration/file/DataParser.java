package org.truong.gvrp_entry_api.service.integration.file;

public interface DataParser<T> {
    T parse(String data);
}
