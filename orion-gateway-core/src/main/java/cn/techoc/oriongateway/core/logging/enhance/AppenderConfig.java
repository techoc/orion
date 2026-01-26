package cn.techoc.oriongateway.core.logging.enhance;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

/**
 * Appender 配置基类
 */
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ConsoleAppenderConfig.class, name = "console"),
        @JsonSubTypes.Type(value = RollingFileAppenderConfig.class, name = "rolling_file"),
        @JsonSubTypes.Type(value = AsyncAppenderConfig.class, name = "async"),
        @JsonSubTypes.Type(value = FileAppenderConfig.class, name = "file"),
        @JsonSubTypes.Type(value = RandomAccessFileAppenderConfig.class, name = "random_access_file"),
        @JsonSubTypes.Type(value = RollingRandomAccessFileAppenderConfig.class, name = "rolling_random_access_file"),
        @JsonSubTypes.Type(value = SmtpAppenderConfig.class, name = "smtp")
})
public abstract class AppenderConfig {
    /**
     * Appender 名称
     */
    private String name;

    /**
     * 日志格式 （默认为 %d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n）
     */
    private String pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n";

    protected AppenderConfig(String name) {
        this.name = name;
    }

    protected AppenderConfig() {
    }

    /**
     * 获取 Appender 类型
     */
    public abstract AppenderType getType();
}
