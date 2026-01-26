package cn.techoc.oriongateway.core.logging.enhance;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Log4j2DynamicConfigTest {

    @BeforeEach
    void setUp() {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        if (!ctx.isStarted()) {
            ctx.start();
        }
    }

    @Test
    void testDynamicConfig() {
        String appenderName = "DynamicAppender";
        String loggerName = "cn.techoc.dynamic.test";
        String fileName = "logs/dynamic_test.log";
        String filePattern = "logs/dynamic_test-%d{yyyy-MM-dd}-%i.log";
        String pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n";

        // 1. 使用新配置结构添加 Appender
        RollingFileAppenderConfig fileConfig = new RollingFileAppenderConfig(appenderName, pattern);
        fileConfig.setFileName(fileName);
        fileConfig.setFilePattern(filePattern);

        Log4j2DynamicConfig.createAppender(fileConfig);

        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        assertNotNull(config.getAppender(appenderName), "Appender should be added to configuration");

        // 2. 添加 Logger
        Log4j2DynamicConfig.addLogger(loggerName, appenderName, Level.INFO, false);
        assertNotNull(config.getLoggerConfig(loggerName), "Logger should be added to configuration");

        // 3. 测试记录日志
        Logger dynamicLogger = LogManager.getLogger(loggerName);
        dynamicLogger.info("This is a dynamic log message");

        // 4. 验证文件是否创建
        File logFile = new File(fileName);
        assertTrue(logFile.exists(), "Log file should be created");

        // 清理
        if (logFile.exists()) {
            // logFile.delete(); // 通常在测试结束时清理，但 Log4j2 可能会占用文件锁
            logFile.deleteOnExit();
        }
    }

    @Test
    void testConsoleAppender() {
        String appenderName = "TestConsoleAppender";
        String pattern = "%d{HH:mm:ss} %-5level - %msg%n";

        // 使用新配置结构创建 Console Appender
        ConsoleAppenderConfig consoleConfig = new ConsoleAppenderConfig(appenderName, pattern);
        consoleConfig.setTarget(ConsoleAppender.Target.SYSTEM_ERR);
        Log4j2DynamicConfig.createAppender(consoleConfig);

        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        assertNotNull(config.getAppender(appenderName), "Console Appender should be added to configuration");
    }

    @Test
    void testAsyncAppender() {
        // 先创建一个 Console Appender 作为基础
        String baseAppenderName = "BaseConsoleAppender";
        ConsoleAppenderConfig baseConfig =
                new ConsoleAppenderConfig(baseAppenderName, "%d{HH:mm:ss} %-5level - %msg%n");
        Log4j2DynamicConfig.createAppender(baseConfig);

        // 创建 Async Appender 配置
        String asyncAppenderName = "TestAsyncAppender";
        AsyncAppenderConfig asyncConfig = new AsyncAppenderConfig(asyncAppenderName);
        asyncConfig.setAppenderRefs(new String[]{baseAppenderName});
        Log4j2DynamicConfig.createAppender(asyncConfig);

        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        assertNotNull(config.getAppender(asyncAppenderName), "Async Appender should be added to configuration");
    }

    @Test
    void testAppenderConfig() {
        // 测试使用 AppenderConfig 创建 Console Appender
        ConsoleAppenderConfig consoleConfig =
                new ConsoleAppenderConfig("ConfigConsole", "%d{HH:mm:ss} %-5level - %msg%n");
        consoleConfig.setTarget(ConsoleAppender.Target.SYSTEM_OUT);

        RollingFileAppenderConfig fileConfig =
                new RollingFileAppenderConfig("ConfigFile", "%d{yyyy-MM-dd HH:mm:ss} %-5level - %msg%n");
        fileConfig.setFileName("logs/config_test.log");
        fileConfig.setFilePattern("logs/config_test-%d{yyyy-MM-dd}-%i.log.gz");
        fileConfig.setSizeLimit("5MB");
        fileConfig.setMaxHistory(3);

        Log4j2DynamicConfig.createAppender(consoleConfig);
        Log4j2DynamicConfig.createAppender(fileConfig);

        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        assertNotNull(config.getAppender("ConfigConsole"), "Config Console Appender should be added");
        assertNotNull(config.getAppender("ConfigFile"), "Config File Appender should be added");
    }

    @Test
    void testMultipleAppenders() {
        // 测试批量创建 Appender
        ConsoleAppenderConfig consoleConfig =
                new ConsoleAppenderConfig("BatchConsole", "%d{HH:mm:ss} %-5level - %msg%n");

        RollingFileAppenderConfig fileConfig =
                new RollingFileAppenderConfig("BatchFile", "%d{yyyy-MM-dd HH:mm:ss} %-5level - %msg%n");
        fileConfig.setFileName("logs/batch_test.log");
        fileConfig.setFilePattern("logs/batch_test-%d{yyyy-MM-dd}-%i.log.gz");
        fileConfig.setSizeLimit("10MB");
        fileConfig.setMaxHistory(5);

        Map<AppenderType, AppenderConfig> configs = Map.of(
                AppenderType.CONSOLE, consoleConfig,
                AppenderType.ROLLING_FILE, fileConfig);

        Log4j2DynamicConfig.addMultipleAppenders(configs);

        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        assertNotNull(config.getAppender("BatchConsole"), "Batch Console Appender should be added");
        assertNotNull(config.getAppender("BatchFile"), "Batch File Appender should be added");
    }

    @Test
    void testCommonAppenderConfigs() {
        // 测试常用配置创建
        Map<AppenderType, AppenderConfig> commonConfigs = Log4j2DynamicConfig.createCommonAppenderConfigs(
                "TestApp", "/logs", "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level - %msg%n");

        assertTrue(commonConfigs.containsKey(AppenderType.CONSOLE), "Should contain Console appender");
        assertTrue(commonConfigs.containsKey(AppenderType.ROLLING_FILE), "Should contain Rolling File appender");
        assertTrue(
                commonConfigs.containsKey(AppenderType.ROLLING_RANDOM_ACCESS_FILE),
                "Should contain Rolling RAF appender");
        assertTrue(commonConfigs.containsKey(AppenderType.RANDOM_ACCESS_FILE), "Should contain RAF appender");
    }
}
