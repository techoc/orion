package cn.techoc.oriongateway.core.logging.access;

import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;

public class AccessLogFormatter {

    private static final DateTimeFormatter ACCESS_LOG_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");

    public static String format(String pattern, Map<String, Object> vars) {
        String log = pattern;
        for (Map.Entry<String, Object> entry : vars.entrySet()) {
            log = log.replace("$" + entry.getKey(), entry.getValue() == null ? "-" : String.valueOf(entry.getValue()));
        }
        return log;
    }

    /**
     * Format using the AccessLogVariable enum for type safety
     */
    public static String format(String pattern, EnumMap<AccessLogVariable, Object> vars) {
        String log = pattern;
        for (Map.Entry<AccessLogVariable, Object> entry : vars.entrySet()) {
            log = log.replace(
                    entry.getKey().getPatternPlaceholder(),
                    entry.getValue() == null ? "-" : String.valueOf(entry.getValue()));
        }
        return log;
    }

    public static String now() {
        return ACCESS_LOG_FORMATTER.format(ZonedDateTime.now());
    }

    public static String now(String zoneId) {
        if (StringUtils.hasText(zoneId)) {
            try {
                ZoneId zone = ZoneId.of(zoneId);
                return ACCESS_LOG_FORMATTER.format(ZonedDateTime.now(zone));
            } catch (Exception e) {
                return now();
            }
        }
        return now();
    }
}
