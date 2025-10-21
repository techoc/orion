package cn.techoc.oriongateway.core.loggging;

import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;

public class AccessLogFormatter {

    public static String format(String pattern, Map<String, Object> vars) {
        String log = pattern;
        for (Map.Entry<String, Object> entry : vars.entrySet()) {
            log = log.replace("$" + entry.getKey(),
                    entry.getValue() == null ? "-" : String.valueOf(entry.getValue()));
        }
        return log;
    }

    /**
     * Format using the AccessLogVariable enum for type safety
     */
    public static String format(String pattern, EnumMap<AccessLogVariable, Object> vars) {
        String log = pattern;
        for (Map.Entry<AccessLogVariable, Object> entry : vars.entrySet()) {
            log = log.replace(entry.getKey().getPatternPlaceholder(),
                    entry.getValue() == null ? "-" : String.valueOf(entry.getValue()));
        }
        return log;
    }

    public static String now() {
        return DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z")
                .format(java.time.ZonedDateTime.now());
    }
}