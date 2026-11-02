package com.ashkan.hotel;

import java.io.IOException;
import java.util.List;

/** Persistence boundary for one bounded-context service. */
public interface ServiceDataStore extends AutoCloseable {
    void initialize() throws Exception;
    List<String> ids() throws Exception;
    String get(String id) throws Exception;
    void insert(String id, String payload) throws Exception;
    void delete(String id) throws Exception;
    @Override default void close() throws Exception { }
}
