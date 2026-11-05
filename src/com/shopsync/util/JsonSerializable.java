package com.shopsync.util;

import java.util.Map;

/**
 * Any object that can describe itself as JSON implements this interface.
 * The {@link Json} utility turns the returned map into a JSON string.
 */
public interface JsonSerializable {
    Map<String, Object> toJsonMap();
}
