package org.opentripplanner.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces variables specified on the format ${variable} in a string with current system
 * environment variables
 */
public class EnvironmentVariableReplacer {

        private static Pattern PATTERN = Pattern.compile("\\$\\{(\\w+)\\}|\\$(\\w+)");

        private Map<String, String> environmentVariables = new HashMap<>();

        public String replace(String s) {
                Matcher matcher = PATTERN.matcher(s);
                while (matcher.find()) {
                        String envVar = matcher.group(0);
                        String nameOnly = matcher.group(1);
                        if (!environmentVariables.containsKey(nameOnly)) {
                                if (System.getenv(nameOnly) != null) {
                                environmentVariables.put(
                                        envVar,
                                        System.getenv(nameOnly));
                                } else {
                                        throw new IllegalArgumentException("Environment variable "
                                                + nameOnly + " not specified");
                                }
                        }
                }

                for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
                        s = s.replace(entry.getKey(), entry.getValue());
                }

                return s;
        }
}
