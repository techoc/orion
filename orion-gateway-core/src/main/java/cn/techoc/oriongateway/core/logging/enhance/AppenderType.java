package cn.techoc.oriongateway.core.logging.enhance;

/**
 * 支持的 Appender 类型枚举
 */
public enum AppenderType {
    ROLLING_RANDOM_ACCESS_FILE,
    RANDOM_ACCESS_FILE,
    ROLLING_FILE,
    CONSOLE,
    ASYNC,
    SMTP,
    FILE
}
