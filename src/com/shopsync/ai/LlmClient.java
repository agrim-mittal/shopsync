package com.shopsync.ai;

import java.io.IOException;

/**
 * A large-language-model provider. ShopSync works without one (it has its own
 * rule-based assistant); when one is configured it is used to phrase replies
 * and write product descriptions from the app's real data.
 */
public interface LlmClient {

    boolean isEnabled();

    /** Provider/model shown in the UI, e.g. "Gemini (gemini-2.5-flash)". */
    String getName();

    /**
     * @param systemInstruction how the model should behave
     * @param prompt            the task, including the data it must use
     * @return the model's text reply
     */
    String complete(String systemInstruction, String prompt) throws IOException;

    /** Fallback used when no API key is configured. */
    LlmClient NONE = new LlmClient() {
        @Override public boolean isEnabled() { return false; }
        @Override public String getName() { return "ShopSync built-in engine"; }
        @Override public String complete(String s, String p) throws IOException {
            throw new IOException("No AI model configured");
        }
    };
}
