package com.shopsync.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small recursive-descent JSON parser (the counterpart of {@link Json}).
 * Produces Map (objects), List (arrays), String, Double, Boolean or null.
 * Used for request bodies (login, assistant messages) and AI API responses.
 */
public final class JsonParser {

    private final String s;
    private int pos;

    private JsonParser(String s) {
        this.s = s;
    }

    public static Object parse(String json) {
        JsonParser p = new JsonParser(json == null ? "" : json);
        p.skipWs();
        Object value = p.readValue();
        p.skipWs();
        if (p.pos != p.s.length()) throw p.error("Unexpected trailing characters");
        return value;
    }

    /** Parses a JSON object, returning an empty map for blank or invalid input. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        try {
            Object o = parse(json);
            return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
        } catch (IllegalArgumentException e) {
            return new LinkedHashMap<>();
        }
    }

    private Object readValue() {
        if (pos >= s.length()) throw error("Unexpected end of input");
        char c = s.charAt(pos);
        switch (c) {
            case '{': return readObject();
            case '[': return readArray();
            case '"': return readString();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default: return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWs();
        if (peek() == '}') { pos++; return map; }
        while (true) {
            skipWs();
            String key = readString();
            skipWs();
            if (peek() != ':') throw error("Expected ':'");
            pos++;
            skipWs();
            map.put(key, readValue());
            skipWs();
            char c = peek();
            pos++;
            if (c == '}') return map;
            if (c != ',') throw error("Expected ',' or '}'");
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWs();
        if (peek() == ']') { pos++; return list; }
        while (true) {
            skipWs();
            list.add(readValue());
            skipWs();
            char c = peek();
            pos++;
            if (c == ']') return list;
            if (c != ',') throw error("Expected ',' or ']'");
        }
    }

    private String readString() {
        if (peek() != '"') throw error("Expected string");
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < s.length()) {
            char c = s.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                char e = s.charAt(pos++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default: sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
        throw error("Unterminated string");
    }

    private Double readNumber() {
        int start = pos;
        while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
        if (start == pos) throw error("Unexpected character '" + s.charAt(pos) + "'");
        return Double.parseDouble(s.substring(start, pos));
    }

    private void expect(String word) {
        if (!s.startsWith(word, pos)) throw error("Expected " + word);
        pos += word.length();
    }

    private char peek() {
        if (pos >= s.length()) throw error("Unexpected end of input");
        return s.charAt(pos);
    }

    private void skipWs() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException(msg + " at position " + pos);
    }
}
