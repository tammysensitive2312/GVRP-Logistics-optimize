package org.truong.gvrp_entry_api.integration.file;

public interface DataParser<T> {
    T parse(String data) throws Exception;
}
