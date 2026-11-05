package com.shopsync.ai;

import com.shopsync.util.Json;
import com.shopsync.util.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls Google's Gemini API (which has a free tier) using Java's built-in
 * HttpClient. Enabled by setting the GEMINI_API_KEY environment variable;
 * the model can be changed with GEMINI_MODEL.
 *
 * Get a key at https://aistudio.google.com/apikey
 */
public class GeminiClient implements LlmClient {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    public static final String DEFAULT_MODEL = "gemini-3.8-flash";

    private final String apiKey;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public GeminiClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    /** Builds a client from environment variables, or {@link LlmClient#NONE} if no key is set. */
    public static LlmClient fromEnvironment() {
        String key = System.getenv("GEMINI_API_KEY");
        if (key == null || key.isBlank()) return LlmClient.NONE;
        String model = System.getenv("GEMINI_MODEL");
        return new GeminiClient(key.trim(), model == null || model.isBlank() ? DEFAULT_MODEL : model.trim());
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "Gemini (" + model + ")";
    }

    @Override
    public String complete(String systemInstruction, String prompt) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("system_instruction", Collections.singletonMap("parts",
                Collections.singletonList(Collections.singletonMap("text", systemInstruction))));
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", "user");
        content.put("parts", Collections.singletonList(Collections.singletonMap("text", prompt)));
        body.put("contents", Collections.singletonList(content));
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("temperature", 0.6);
        // Newer Gemini models "think" before answering and that counts towards this limit,
        // so leave plenty of room; the system prompt keeps the visible answer short.
        config.put("maxOutputTokens", 4096);
        body.put("generationConfig", config);

        String url = String.format(ENDPOINT, URLEncoder.encode(model, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(40))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)   // key in a header, not the URL, so it never appears in logs
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while calling Gemini", e);
        }
        if (response.statusCode() != 200) {
            throw new IOException("Gemini returned HTTP " + response.statusCode() + ": "
                    + response.body().substring(0, Math.min(300, response.body().length())));
        }
        return extractText(response.body());
    }

    /** Pulls candidates[0].content.parts[*].text out of the response JSON. */
    @SuppressWarnings("unchecked")
    private static String extractText(String json) throws IOException {
        try {
            Map<String, Object> root = (Map<String, Object>) JsonParser.parse(json);
            List<Object> candidates = (List<Object>) root.get("candidates");
            Map<String, Object> first = (Map<String, Object>) candidates.get(0);
            Map<String, Object> content = (Map<String, Object>) first.get("content");
            StringBuilder sb = new StringBuilder();
            for (Object part : (List<Object>) content.get("parts")) {
                Object text = ((Map<String, Object>) part).get("text");
                if (text != null) sb.append(text);
            }
            if (sb.length() == 0) throw new IOException("Empty reply from Gemini");
            return sb.toString().trim();
        } catch (RuntimeException e) {
            throw new IOException("Unexpected Gemini response format", e);
        }
    }
}
