package com.shopsync.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * A tiny JSON writer so the project needs no external libraries.
 * Supports: null, String, Number, Boolean, Enum, Map, Collection, arrays of
 * objects and anything implementing {@link JsonSerializable}.
 */
public final class Json {

    private Json() { } // utility class: no instances

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString(sb, (String) value);
        } else if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else if (d == Math.rint(d)) {
                sb.append((long) d);
            } else {
                sb.append(Math.round(d * 100.0) / 100.0);
            }
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Enum) {
            writeString(sb, ((Enum<?>) value).name());
        } else if (value instanceof JsonSerializable) {
            write(sb, ((JsonSerializable) value).toJsonMap());
        } else if (value instanceof Map) {
            sb.append('{');
            Iterator<? extends Map.Entry<?, ?>> it = ((Map<?, ?>) value).entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> e = it.next();
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
                if (it.hasNext()) sb.append(',');
            }
            sb.append('}');
        } else if (value instanceof Collection) {
            sb.append('[');
            Iterator<?> it = ((Collection<?>) value).iterator();
            while (it.hasNext()) {
                write(sb, it.next());
                if (it.hasNext()) sb.append(',');
            }
            sb.append(']');
        } else if (value instanceof Object[]) {
            sb.append('[');
            Object[] arr = (Object[]) value;
            for (int i = 0; i < arr.length; i++) {
                write(sb, arr[i]);
                if (i < arr.length - 1) sb.append(',');
            }
            sb.append(']');
        } else {
            writeString(sb, value.toString());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
