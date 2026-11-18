package com.shopsync.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rupee formatting with Indian digit grouping (₹1,10,000 rather than ₹110,000). */
public final class Money {

    private static final Pattern WESTERN = Pattern.compile("₹(\\d{1,3}(?:,\\d{3})+)");

    private Money() { }

    /** Formats an amount as rupees, rounded to the nearest rupee. */
    public static String inr(double amount) {
        long v = Math.round(amount);
        return (v < 0 ? "-₹" : "₹") + group(Long.toString(Math.abs(v)));
    }

    /** Rewrites any "₹123,456" in a sentence to Indian grouping ("₹1,23,456"). */
    public static String indianGrouping(String text) {
        if (text == null || text.indexOf('₹') < 0) return text;
        Matcher m = WESTERN.matcher(text);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement("₹" + group(m.group(1).replace(",", ""))));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String group(String digits) {
        if (digits.length() <= 3) return digits;
        String last3 = digits.substring(digits.length() - 3);
        StringBuilder rest = new StringBuilder(digits.substring(0, digits.length() - 3));
        for (int i = rest.length() - 2; i > 0; i -= 2) rest.insert(i, ',');
        return rest + "," + last3;
    }
}
