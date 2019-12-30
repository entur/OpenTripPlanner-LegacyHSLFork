package org.opentripplanner.util;

public class StringUtil {


    /**
     * Pad a text with quotes(") around it. If the text is null return the text "null".
     */
    public static String quote(String text) {
        return text == null ? "null" : "\"" + text + "\"";
    }
}
