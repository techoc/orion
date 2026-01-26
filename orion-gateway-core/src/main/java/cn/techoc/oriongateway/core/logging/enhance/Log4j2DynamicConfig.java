package cn.techoc.oriongateway.core.logging.enhance;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.*;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * Log4j2 动态配置增强工具
 * 用于在运行时动态创建 Appender 和 Logger
 */
public class Log4j2DynamicConfig {

    private static final Logger logger = LogManager.getLogger(Log4j2DynamicConfig.class);

    /**
     * 通用方法：根据配置创建不同类型的 Appender
     *
     * @param config Appender 配置
     * @return 创建的 Appender，失败时返回 null
     */
    public static Appender createAppender(AppenderConfig config) {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        if (ctx == null || !ctx.isStarted()) {
            logger.error("LoggerContext is not available or not started");
            return null;
        }

        final Configuration logConfig = ctx.getConfiguration();
        final AppenderType type = config.getType();

        Appender appender = null;
        switch (type) {
            case ROLLING_FILE:
                appender = createRollingFileAppender(logConfig, (RollingFileAppenderConfig) config);
                break;
            case CONSOLE:
                appender = createConsoleAppender(logConfig, (ConsoleAppenderConfig) config);
                break;
            case ASYNC:
                appender = createAsyncAppender(logConfig, (AsyncAppenderConfig) config);
                break;
            case SMTP:
                appender = createSmtpAppender(logConfig, (SmtpAppenderConfig) config);
                break;
            case ROLLING_RANDOM_ACCESS_FILE:
                appender = createRollingRandomAccessFileAppender(
                        logConfig, (RollingRandomAccessFileAppenderConfig) config);
                break;
            case RANDOM_ACCESS_FILE:
                appender = createRandomAccessFileAppender(logConfig, (RandomAccessFileAppenderConfig) config);
                break;
            case FILE:
                appender = createFileAppender(logConfig, (FileAppenderConfig) config);
                break;
            default:
                logger.error("Unsupported appender type: {}", type);
                return null;
        }

        // 启用并添加到 logConfig 中
        if (appender != null) {
            appender.start();
            logConfig.addAppender(appender);
            logger.info("Successfully created and added Log4j2 Appender: {} of type: {}", appender.getName(), type);
        }

        return appender;
    }

    /**
     * 兼容性方法：保持向后兼容
     *
     * @param type   Appender 类型
     * @param config Appender 配置
     * @return 创建的 Appender，失败时返回 null
     */
    @Deprecated
    public static Appender createAppender(AppenderType type, AppenderConfig config) {
        return createAppender(config);
    }

    /**
     * 动态添加一个 RollingFileAppender
     *
     * @param name        Appender 名称
     * @param fileName    日志文件路径
     * @param filePattern 日志滚动模式
     * @param pattern     日志格式
     * @param sizeLimit   单个文件大小限制
     * @param maxHistory  最大历史文件数
     */
    public static void addRollingFileAppender(
            String name, String fileName, String filePattern, String pattern, String sizeLimit, int maxHistory) {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);

        // 建议添加的校验
        if (ctx == null || !ctx.isStarted()) {
            logger.error("LoggerContext is not available or not started");
            return;
        }

        final Configuration config = ctx.getConfiguration();

        // 1. 创建 Layout
        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern(pattern)
                .withConfiguration(config)
                .build();

        // 2. 创建触发策略和滚动策略
        SizeBasedTriggeringPolicy policy = SizeBasedTriggeringPolicy.createPolicy(sizeLimit);
        DefaultRolloverStrategy strategy = DefaultRolloverStrategy.newBuilder()
                .withMax(String.valueOf(maxHistory))
                .withConfig(config)
                .build();

        // 3. 创建 Appender
        RollingFileAppender appender = RollingFileAppender.newBuilder()
                .setName(name)
                .withFileName(fileName)
                .withFilePattern(filePattern)
                .setLayout(layout)
                .withPolicy(policy)
                .withStrategy(strategy)
                .setConfiguration(config)
                .build();

        appender.start();

        // 4. 将 Appender 添加到配置中
        config.addAppender(appender);
        updateLoggers(config, appender);
        ctx.updateLoggers();

        logger.info("Successfully added Log4j2 RollingFileAppender: {}", name);
    }

    /**
     * 创建 ConsoleAppender
     */
    private static ConsoleAppender createConsoleAppender(Configuration config, ConsoleAppenderConfig appenderConfig) {
        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern(appenderConfig.getPattern())
                .withConfiguration(config)
                .build();

        return ConsoleAppender.newBuilder()
                .setName(appenderConfig.getName())
                .setLayout(layout)
                .setConfiguration(config)
                .setTarget(
                        appenderConfig.getTarget() != null
                                ? appenderConfig.getTarget()
                                : ConsoleAppender.Target.SYSTEM_OUT)
                .build();
    }

    /**
     * 创建 AsyncAppender
     */
    private static AsyncAppender createAsyncAppender(Configuration config, AsyncAppenderConfig appenderConfig) {
        String[] appenderRefs = appenderConfig.getAppenderRefs();
        if (appenderRefs == null || appenderRefs.length == 0) {
            logger.error("AsyncAppender requires at least one appender reference");
            return null;
        }

        // AsyncAppender 需要使用 AppenderRef 而不是直接添加 Appender
        AppenderRef[] appenderRefArray = new AppenderRef[appenderRefs.length];
        for (int i = 0; i < appenderRefs.length; i++) {
            String appenderRefName = appenderRefs[i];
            Appender refAppender = config.getAppender(appenderRefName);
            if (refAppender != null) {
                appenderRefArray[i] = AppenderRef.createAppenderRef(appenderRefName, null, null);
            } else {
                logger.warn("Referenced appender not found: {}", appenderRefName);
                appenderRefArray[i] = null;
            }
        }

        // 使用 AppenderRef 数组创建 AsyncAppender
        return AsyncAppender.newBuilder()
                .setName(appenderConfig.getName())
                .setConfiguration(config)
                .setBufferSize(appenderConfig.getBufferSize() != null ? appenderConfig.getBufferSize() : 256)
                .setBlocking(appenderConfig.getBlocking() != null ? appenderConfig.getBlocking() : false)
                .setIncludeLocation(
                        appenderConfig.getIncludeLocation() != null ? appenderConfig.getIncludeLocation() : false)
                .setAppenderRefs(appenderRefArray)
                .build();
    }

    /**
     * 创建 SmtpAppender
     */
    private static SmtpAppender createSmtpAppender(Configuration config, SmtpAppenderConfig appenderConfig) {
        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern(appenderConfig.getPattern())
                .withConfiguration(config)
                .build();

        if (appenderConfig.getTo() == null
                || appenderConfig.getFrom() == null
                || appenderConfig.getSmtpHost() == null) {
            logger.error("SMTPAppender requires 'to', 'from', and 'smtpHost' properties");
            return null;
        }

        SmtpAppender.Builder builder = SmtpAppender.newBuilder()
                .setName(appenderConfig.getName())
                .setLayout(layout)
                .setConfiguration(config)
                .setTo(appenderConfig.getTo())
                .setFrom(appenderConfig.getFrom())
                .setSmtpHost(appenderConfig.getSmtpHost());

        if (appenderConfig.getSubject() != null) builder.setSubject(appenderConfig.getSubject());
        if (appenderConfig.getSmtpPort() != null) builder.setSmtpPort(appenderConfig.getSmtpPort());
        if (appenderConfig.getSmtpUsername() != null) builder.setSmtpUsername(appenderConfig.getSmtpUsername());
        if (appenderConfig.getSmtpPassword() != null) builder.setSmtpPassword(appenderConfig.getSmtpPassword());

        return builder.build();
    }

    /**
     * 创建 RollingRandomAccessFileAppender
     */
    private static RollingRandomAccessFileAppender createRollingRandomAccessFileAppender(
            Configuration config, RollingRandomAccessFileAppenderConfig appenderConfig) {
        String fileName = appenderConfig.getFileName();
        String filePattern = appenderConfig.getFilePattern();

        if (fileName == null || filePattern == null) {
            logger.error("RollingRandomAccessFileAppender requires 'fileName' and 'filePattern' properties");
            return null;
        }

        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern(appenderConfig.getPattern())
                .withConfiguration(config)
                .build();

        SizeBasedTriggeringPolicy policy = SizeBasedTriggeringPolicy.createPolicy(
                appenderConfig.getSizeLimit() != null ? appenderConfig.getSizeLimit() : "10MB");
        DefaultRolloverStrategy strategy = DefaultRolloverStrategy.newBuilder()
                .withMax(String.valueOf(appenderConfig.getMaxHistory()))
                .withConfig(config)
                .build();

        return RollingRandomAccessFileAppender.newBuilder()
                .setName(appenderConfig.getName())
                .withFileName(fileName)
                .withFilePattern(filePattern)
                .setLayout(layout)
                .withPolicy(policy)
                .withStrategy(strategy)
                .setConfiguration(config)
                .setImmediateFlush(
                        appenderConfig.getImmediateFlush() != null ? appenderConfig.getImmediateFlush() : true)
                .withAppend(appenderConfig.getAppend())
                .build();
    }

    /**
     * 创建 RandomAccessFileAppender
     */
    private static RandomAccessFileAppender createRandomAccessFileAppender(
            Configuration config, RandomAccessFileAppenderConfig appenderConfig) {
        String fileName = appenderConfig.getFileName();

        if (fileName == null) {
            logger.error("RandomAccessFileAppender requires 'fileName' property");
            return null;
        }

        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern(appenderConfig.getPattern())
                .withConfiguration(config)
                .build();

        return RandomAccessFileAppender.newBuilder()
                .setName(appenderConfig.getName())
                .setFileName(fileName)
                .setLayout(layout)
                .setConfiguration(config)
                .setImmediateFlush(
                        appenderConfig.getImmediateFlush() != null ? appenderConfig.getImmediateFlush() : true)
                .setAppend(appenderConfig.getAppend())
                .build();
    }

    /**
     * 创建 FileAppender
     */
    private static FileAppender createFileAppender(Configuration config, FileAppenderConfig appenderConfig) {
        String fileName = appenderConfig.getFileName();

        if (fileName == null) {
            logger.error("FileAppender requires 'fileName' property");
            return null;
        }

        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern(appenderConfig.getPattern())
                .withConfiguration(config)
                .build();

        return FileAppender.newBuilder()
                .setName(appenderConfig.getName())
                .withFileName(fileName)
                .setLayout(layout)
                .setConfiguration(config)
                .setImmediateFlush(
                        appenderConfig.getImmediateFlush() != null ? appenderConfig.getImmediateFlush() : true)
                .withAppend(appenderConfig.getAppend())
                .build();
    }

    /**
     * 创建 RollingFileAppender（用于通用方法）
     */
    private static RollingFileAppender createRollingFileAppender(
            Configuration config, RollingFileAppenderConfig appenderConfig) {
        String fileName = appenderConfig.getFileName();
        String filePattern = appenderConfig.getFilePattern();

        if (fileName == null || filePattern == null) {
            logger.error("RollingFileAppender requires 'fileName' and 'filePattern' properties");
            return null;
        }

        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern(appenderConfig.getPattern())
                .withConfiguration(config)
                .build();

        SizeBasedTriggeringPolicy policy = SizeBasedTriggeringPolicy.createPolicy(
                appenderConfig.getSizeLimit() != null ? appenderConfig.getSizeLimit() : "10MB");
        DefaultRolloverStrategy strategy = DefaultRolloverStrategy.newBuilder()
                .withMax(String.valueOf(appenderConfig.getMaxHistory()))
                .withConfig(config)
                .build();

        return RollingFileAppender.newBuilder()
                .setName(appenderConfig.getName())
                .withFileName(fileName)
                .withFilePattern(filePattern)
                .setLayout(layout)
                .withPolicy(policy)
                .withStrategy(strategy)
                .withAppend(appenderConfig.getAppend() != null ? appenderConfig.getAppend() : true)
                .setImmediateFlush(
                        appenderConfig.getImmediateFlush() != null ? appenderConfig.getImmediateFlush() : true)
                .setConfiguration(config)
                .build();
    }

    /**
     * 动态添加一个 RollingFileAppender (使用默认滚动策略)
     *
     * @param name        Appender 名称
     * @param fileName    日志文件路径
     * @param filePattern 日志滚动模式
     * @param pattern     日志格式
     */
    public static void addRollingFileAppender(String name, String fileName, String filePattern, String pattern) {
        addRollingFileAppender(name, fileName, filePattern, pattern, "10MB", 7);
    }

    /**
     * 动态添加 ConsoleAppender
     *
     * @param name    Appender 名称
     * @param pattern 日志格式
     * @param target  输出目标 (SYSTEM_OUT 或 SYSTEM_ERR)
     */
    public static void addConsoleAppender(String name, String pattern, ConsoleAppender.Target target) {
        ConsoleAppenderConfig config = new ConsoleAppenderConfig(name, pattern);
        config.setTarget(target);

        Appender appender = createAppender(config);
        if (appender != null) {
            addAppenderToContext(appender);
        }
    }

    /**
     * 动态添加 ConsoleAppender (默认输出到 SYSTEM_OUT)
     *
     * @param name    Appender 名称
     * @param pattern 日志格式
     */
    public static void addConsoleAppender(String name, String pattern) {
        addConsoleAppender(name, pattern, ConsoleAppender.Target.SYSTEM_OUT);
    }

    /**
     * 动态添加 AsyncAppender
     *
     * @param name         Appender 名称
     * @param appenderRefs 要包装的 Appender 名称数组
     * @param bufferSize   缓冲区大小
     * @param blocking     是否阻塞
     */
    public static void addAsyncAppender(String name, String[] appenderRefs, int bufferSize, boolean blocking) {
        AsyncAppenderConfig config =
                new AsyncAppenderConfig(name, "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        config.setAppenderRefs(appenderRefs);
        config.setBufferSize(bufferSize);
        config.setBlocking(blocking);

        Appender appender = createAppender(config);
        if (appender != null) {
            addAppenderToContext(appender);
        }
    }

    /**
     * 动态添加 AsyncAppender (使用默认配置)
     *
     * @param name         Appender 名称
     * @param appenderRefs 要包装的 Appender 名称数组
     */
    public static void addAsyncAppender(String name, String[] appenderRefs) {
        addAsyncAppender(name, appenderRefs, 256, false);
    }

    /**
     * 动态添加 RollingRandomAccessFileAppender
     *
     * @param name           Appender 名称
     * @param fileName       日志文件路径
     * @param filePattern    日志滚动模式
     * @param pattern        日志格式
     * @param sizeLimit      单个文件大小限制
     * @param maxHistory     最大历史文件数
     * @param immediateFlush 是否立即刷新
     * @param append         是否追加模式
     */
    public static void addRollingRandomAccessFileAppender(
            String name,
            String fileName,
            String filePattern,
            String pattern,
            String sizeLimit,
            int maxHistory,
            boolean immediateFlush,
            boolean append) {
        RollingRandomAccessFileAppenderConfig config = new RollingRandomAccessFileAppenderConfig(name, pattern);
        config.setFileName(fileName);
        config.setFilePattern(filePattern);
        config.setSizeLimit(sizeLimit);
        config.setMaxHistory(maxHistory);
        config.setImmediateFlush(immediateFlush);
        config.setAppend(append);

        Appender appender = createAppender(config);
        if (appender != null) {
            addAppenderToContext(appender);
        }
    }

    /**
     * 动态添加 RollingRandomAccessFileAppender (使用默认配置)
     *
     * @param name        Appender 名称
     * @param fileName    日志文件路径
     * @param filePattern 日志滚动模式
     * @param pattern     日志格式
     */
    public static void addRollingRandomAccessFileAppender(
            String name, String fileName, String filePattern, String pattern) {
        addRollingRandomAccessFileAppender(name, fileName, filePattern, pattern, "10MB", 7, false, true);
    }

    /**
     * 动态添加 RandomAccessFileAppender
     *
     * @param name           Appender 名称
     * @param fileName       日志文件路径
     * @param pattern        日志格式
     * @param immediateFlush 是否立即刷新
     * @param append         是否追加模式
     */
    public static void addRandomAccessFileAppender(
            String name, String fileName, String pattern, boolean immediateFlush, boolean append) {
        RandomAccessFileAppenderConfig config = new RandomAccessFileAppenderConfig(name, pattern);
        config.setFileName(fileName);
        config.setImmediateFlush(immediateFlush);
        config.setAppend(append);

        Appender appender = createAppender(config);
        if (appender != null) {
            addAppenderToContext(appender);
        }
    }

    /**
     * 动态添加 RandomAccessFileAppender (使用默认配置)
     *
     * @param name     Appender 名称
     * @param fileName 日志文件路径
     * @param pattern  日志格式
     */
    public static void addRandomAccessFileAppender(String name, String fileName, String pattern) {
        addRandomAccessFileAppender(name, fileName, pattern, false, true);
    }

    /**
     * 动态添加 FileAppender
     *
     * @param name           Appender 名称
     * @param fileName       日志文件路径
     * @param pattern        日志格式
     * @param immediateFlush 是否立即刷新
     * @param append         是否追加模式
     */
    public static void addFileAppender(
            String name, String fileName, String pattern, boolean immediateFlush, boolean append) {
        FileAppenderConfig config = new FileAppenderConfig(name, pattern);
        config.setFileName(fileName);
        config.setImmediateFlush(immediateFlush);
        config.setAppend(append);

        Appender appender = createAppender(config);
        if (appender != null) {
            addAppenderToContext(appender);
        }
    }

    /**
     * 动态添加 FileAppender (使用默认配置)
     *
     * @param name     Appender 名称
     * @param fileName 日志文件路径
     * @param pattern  日志格式
     */
    public static void addFileAppender(String name, String fileName, String pattern) {
        addFileAppender(name, fileName, pattern, true, true);
    }

    /**
     * 动态添加 SMTPAppender
     *
     * @param name         Appender 名称
     * @param pattern      日志格式
     * @param to           收件人邮箱
     * @param from         发件人邮箱
     * @param subject      邮件主题
     * @param smtpHost     SMTP 服务器地址
     * @param smtpPort     SMTP 端口
     * @param smtpUsername SMTP 用户名
     * @param smtpPassword SMTP 密码
     */
    public static void addSmtpAppender(
            String name,
            String pattern,
            String to,
            String from,
            String subject,
            String smtpHost,
            Integer smtpPort,
            String smtpUsername,
            String smtpPassword) {
        SmtpAppenderConfig config = new SmtpAppenderConfig(name, pattern);
        config.setTo(to);
        config.setFrom(from);
        config.setSubject(subject);
        config.setSmtpHost(smtpHost);
        config.setSmtpPort(smtpPort);
        config.setSmtpUsername(smtpUsername);
        config.setSmtpPassword(smtpPassword);

        Appender appender = createAppender(config);
        if (appender != null) {
            addAppenderToContext(appender);
        }
    }

    /**
     * 动态添加一个 Logger 并关联 Appender
     *
     * @param loggerName   Logger 名称（通常是包名或类名）
     * @param appenderName 要关联的 Appender 名称
     * @param level        日志级别
     * @param additivity   是否继承父 Logger 的 Appender
     */
    public static void addLogger(String loggerName, String appenderName, Level level, boolean additivity) {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);

        // 建议添加的校验
        if (ctx == null || !ctx.isStarted()) {
            logger.error("LoggerContext is not available or not started");
            return;
        }

        final Configuration config = ctx.getConfiguration();

        Appender appender = config.getAppender(appenderName);
        if (appender == null) {
            logger.error("Appender {} not found, cannot create logger {}", appenderName, loggerName);
            return;
        }

        AppenderRef ref = AppenderRef.createAppenderRef(appenderName, level, null);
        AppenderRef[] refs = new AppenderRef[]{ref};
        LoggerConfig loggerConfig = LoggerConfig.newBuilder()
                .withLoggerName(loggerName)
                .withConfig(config)
                .withLevel(level)
                .withAdditivity(additivity)
                .withIncludeLocation("true")
                .withRefs(refs)
                .build();
        loggerConfig.addAppender(appender, level, null);

        config.addLogger(loggerName, loggerConfig);
        ctx.updateLoggers();

        logger.info("Successfully added Log4j2 Logger: {} associated with Appender: {}", loggerName, appenderName);
    }

    /**
     * 将 Appender 添加到上下文并更新配置
     * 注意：如果 appender 是通过 createAppender 创建的，则已经启动并添加到配置中
     */
    private static void addAppenderToContext(Appender appender) {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();

        // 检查 appender 是否已经启动，避免重复操作
        if (!appender.isStarted()) {
            appender.start();
            config.addAppender(appender);
            logger.info("Successfully started and added Log4j2 Appender: {}", appender.getName());
        } else {
            logger.debug("Appender {} is already started and added to configuration", appender.getName());
        }

        updateLoggers(config, appender);
        ctx.updateLoggers();
    }

    /**
     * 更新所有已存在的 Logger 配置，将新的 Appender 添加到根 Logger
     */
    private static void updateLoggers(Configuration config, Appender appender) {
        LoggerConfig rootLoggerConfig = config.getRootLogger();
        if (rootLoggerConfig != null) {
            rootLoggerConfig.addAppender(appender, null, null);
        }
    }

    /**
     * 批量创建多个 Appender
     *
     * @param appenderConfigs Appender 配置列表，格式为 Map.Entry<类型, 配置>
     */
    public static void addMultipleAppenders(java.util.Map<AppenderType, AppenderConfig> appenderConfigs) {
        for (java.util.Map.Entry<AppenderType, AppenderConfig> entry : appenderConfigs.entrySet()) {
            Appender appender = createAppender(entry.getKey(), entry.getValue());
            if (appender != null) {
                addAppenderToContext(appender);
            }
        }
    }

    /**
     * 便捷方法：创建一组常用的 Appender 配置
     *
     * @param baseName 基础名称
     * @param logDir   日志目录
     * @param pattern  日志格式
     * @return 包含常用 Appender 配置的 Map
     */
    public static java.util.Map<AppenderType, AppenderConfig> createCommonAppenderConfigs(
            String baseName, String logDir, String pattern) {
        java.util.Map<AppenderType, AppenderConfig> configs = new java.util.HashMap<>();

        // Console Appender
        configs.put(AppenderType.CONSOLE, new ConsoleAppenderConfig(baseName + "Console", pattern));

        // Rolling File Appender (传统实现)
        RollingFileAppenderConfig rollingFileConfig = new RollingFileAppenderConfig(baseName + "RollingFile", pattern);
        rollingFileConfig.setFileName(logDir + "/" + baseName.toLowerCase() + ".log");
        rollingFileConfig.setFilePattern(logDir + "/" + baseName.toLowerCase() + "-%d{yyyy-MM-dd}-%i.log.gz");
        rollingFileConfig.setSizeLimit("10MB");
        rollingFileConfig.setMaxHistory(7);
        configs.put(AppenderType.ROLLING_FILE, rollingFileConfig);

        // Rolling Random Access File Appender (高性能实现)
        RollingRandomAccessFileAppenderConfig rollingRAFConfig =
                new RollingRandomAccessFileAppenderConfig(baseName + "RollingRAF", pattern);
        rollingRAFConfig.setFileName(logDir + "/" + baseName.toLowerCase() + "-raf.log");
        rollingRAFConfig.setFilePattern(logDir + "/" + baseName.toLowerCase() + "-raf-%d{yyyy-MM-dd}-%i.log.gz");
        rollingRAFConfig.setSizeLimit("10MB");
        rollingRAFConfig.setMaxHistory(7);
        rollingRAFConfig.setImmediateFlush(false);
        rollingRAFConfig.setAppend(true);
        configs.put(AppenderType.ROLLING_RANDOM_ACCESS_FILE, rollingRAFConfig);

        // Random Access File Appender (单文件高性能实现)
        RandomAccessFileAppenderConfig rafConfig = new RandomAccessFileAppenderConfig(baseName + "RAF", pattern);
        rafConfig.setFileName(logDir + "/" + baseName.toLowerCase() + "-single.log");
        rafConfig.setImmediateFlush(false);
        rafConfig.setAppend(true);
        configs.put(AppenderType.RANDOM_ACCESS_FILE, rafConfig);

        return configs;
    }

    /**
     * 便捷方法：创建高性能文件 Appender 配置组合
     * 优先使用 RandomAccessFile 系列的 Appender
     *
     * @param baseName 基础名称
     * @param logDir   日志目录
     * @param pattern  日志格式
     * @return 包含高性能 Appender 配置的 Map
     */
    public static java.util.Map<AppenderType, AppenderConfig> createHighPerformanceFileAppenderConfigs(
            String baseName, String logDir, String pattern) {
        java.util.Map<AppenderType, AppenderConfig> configs = new java.util.HashMap<>();

        // Console Appender
        configs.put(AppenderType.CONSOLE, new ConsoleAppenderConfig(baseName + "Console", pattern));

        // Rolling Random Access File Appender (高性能滚动实现)
        RollingRandomAccessFileAppenderConfig rollingRAFConfig =
                new RollingRandomAccessFileAppenderConfig(baseName + "RollingRAF", pattern);
        rollingRAFConfig.setFileName(logDir + "/" + baseName.toLowerCase() + "-raf.log");
        rollingRAFConfig.setFilePattern(logDir + "/" + baseName.toLowerCase() + "-raf-%d{yyyy-MM-dd}-%i.log.gz");
        rollingRAFConfig.setSizeLimit("50MB");
        rollingRAFConfig.setMaxHistory(30);
        rollingRAFConfig.setImmediateFlush(false);
        rollingRAFConfig.setAppend(true);
        configs.put(AppenderType.ROLLING_RANDOM_ACCESS_FILE, rollingRAFConfig);

        // File Appender (简单的文件实现)
        FileAppenderConfig fileConfig = new FileAppenderConfig(baseName + "File", pattern);
        fileConfig.setFileName(logDir + "/" + baseName.toLowerCase() + "-simple.log");
        fileConfig.setImmediateFlush(true);
        fileConfig.setAppend(true);
        configs.put(AppenderType.FILE, fileConfig);

        return configs;
    }
}
