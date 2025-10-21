package cn.techoc.oriongateway.core.loggging;

import lombok.Getter;

/**
 * Enum for access log variables that can be used in log patterns
 */
@Getter
public enum AccessLogVariable {

    /**
     * Client IP address
     */
    REMOTE_ADDR("remote_addr"),

    /**
     * Remote user identifier
     */
    REMOTE_USER("remote_user"),

    /**
     * Local time of the request
     */
    TIME_LOCAL("time_local"),
    /**
     * Request processing time
     */
    REQUEST_TIME("request_time"),

    /**
     * Request method and path
     */
    REQUEST("request"),

    /**
     * HTTP response status code
     */
    STATUS("status"),

    /**
     * Number of bytes sent to client
     */
    BODY_BYTES_SENT("body_bytes_sent"),

    /**
     * HTTP referer header
     */
    HTTP_REFERER("http_referer"),

    /**
     * HTTP user agent header
     */
    HTTP_USER_AGENT("http_user_agent"),

    /**
     * X-Forwarded-For header
     */
    HTTP_X_FORWARDED_FOR("http_x_forwarded_for"),

    /**
     * Upstream server address
     */
    UPSTREAM_ADDR("upstream_addr"),

    /**
     * Upstream response time
     */
    UPSTREAM_RESPONSE_TIME("upstream_response_time");


    private final String variableName;

    AccessLogVariable(String variableName) {
        this.variableName = variableName;
    }

    /**
     * Get the variable name with $ prefix for pattern matching
     */
    public String getPatternPlaceholder() {
        return "$" + variableName;
    }

    public int getPlaceholderLength() {
        return getPatternPlaceholder().length();
    }

    /**
     * Static method to get enum by variable name string
     */
    public static AccessLogVariable fromString(String variableName) {
        for (AccessLogVariable variable : values()) {
            if (variable.variableName.equals(variableName)) {
                return variable;
            }
        }
        // For custom variables not in enum, we could return null or a special value
        return null;
    }
}