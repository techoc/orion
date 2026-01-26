package cn.techoc.oriongatewaystarter;

import cn.techoc.oriongateway.core.logging.enhance.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
@EnableConfigurationProperties(Log4j2DynamicProperties.class)
@ConditionalOnProperty(prefix = "orion.logging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class Log4j2DynamicAutoConfiguration {

    private static final Logger log = LogManager.getLogger(Log4j2DynamicAutoConfiguration.class);

    private final Log4j2DynamicProperties properties;

    public Log4j2DynamicAutoConfiguration(Log4j2DynamicProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            return;
        }

        if ((properties.getAppenders() == null || properties.getAppenders().isEmpty())
                && (properties.getLoggers() == null || properties.getLoggers().isEmpty())) {
            return;
        }

        log.info("Initializing dynamic Log4j2 configurations from properties...");

        // Initialize Appenders
        if (properties.getAppenders() != null) {
            for (AppenderConfig appenderConfig : properties.getAppenders()) {
                switch (appenderConfig.getType()) {
                    case ROLLING_FILE:
                        initRollingFileAppender(appenderConfig);
                        break;
                    case CONSOLE:
                        initConsoleAppender(appenderConfig);
                        break;
                    case ASYNC:
                        initAsyncAppender(appenderConfig);
                        break;
                    case SMTP:
                        initSmtpAppender(appenderConfig);
                        break;
                    case ROLLING_RANDOM_ACCESS_FILE:
                        initRollingRandomAccessFileAppender(appenderConfig);
                        break;
                    case RANDOM_ACCESS_FILE:
                        initRandomAccessFileAppender(appenderConfig);
                        break;
                    case FILE:
                        initFileAppender(appenderConfig);
                        break;
                    default:
                        log.warn("Unsupported appender type: {}", appenderConfig.getType());
                        break;
                }
            }
        }

        // Initialize Loggers
        if (properties.getLoggers() != null) {
            for (LoggerConfig loggerConfig : properties.getLoggers()) {
                Log4j2DynamicConfig.addLogger(
                        loggerConfig.getName(),
                        loggerConfig.getAppenderName(),
                        loggerConfig.getLevel(),
                        loggerConfig.isAdditivity());
            }
        }
    }

    private void initRollingFileAppender(AppenderConfig config) {
        if (config instanceof RollingFileAppenderConfig) {
            RollingFileAppenderConfig rollingConfig = (RollingFileAppenderConfig) config;
            Log4j2DynamicConfig.addRollingFileAppender(
                    config.getName(),
                    rollingConfig.getFileName(),
                    rollingConfig.getFilePattern(),
                    rollingConfig.getPattern(),
                    rollingConfig.getSizeLimit(),
                    rollingConfig.getMaxHistory());
        } else {
            log.warn("RollingFileAppender configuration must be of type RollingFileAppenderConfig");
        }
    }

    private void initConsoleAppender(AppenderConfig config) {
        if (config instanceof ConsoleAppenderConfig) {
            ConsoleAppenderConfig consoleConfig = (ConsoleAppenderConfig) config;
            if (consoleConfig.getTarget() != null) {
                Log4j2DynamicConfig.addConsoleAppender(
                        config.getName(), config.getPattern(), consoleConfig.getTarget());
            } else {
                Log4j2DynamicConfig.addConsoleAppender(config.getName(), config.getPattern());
            }
        } else {
            log.warn("ConsoleAppender configuration must be of type ConsoleAppenderConfig");
        }
    }

    private void initAsyncAppender(AppenderConfig config) {
        if (config instanceof AsyncAppenderConfig) {
            AsyncAppenderConfig asyncConfig = (AsyncAppenderConfig) config;
            if (asyncConfig.getAppenderRefs() != null) {
                if (asyncConfig.getBufferSize() != null && asyncConfig.getBlocking() != null) {
                    Log4j2DynamicConfig.addAsyncAppender(
                            config.getName(),
                            asyncConfig.getAppenderRefs(),
                            asyncConfig.getBufferSize(),
                            asyncConfig.getBlocking());
                } else {
                    Log4j2DynamicConfig.addAsyncAppender(config.getName(), asyncConfig.getAppenderRefs());
                }
            } else {
                log.warn("AsyncAppender '{}' requires appenderRefs property", config.getName());
            }
        } else {
            log.warn("AsyncAppender configuration must be of type AsyncAppenderConfig");
        }
    }

    private void initSmtpAppender(AppenderConfig config) {
        if (config instanceof SmtpAppenderConfig) {
            SmtpAppenderConfig smtpConfig = (SmtpAppenderConfig) config;
            if (smtpConfig.getTo() != null && smtpConfig.getFrom() != null && smtpConfig.getSmtpHost() != null) {
                Log4j2DynamicConfig.addSmtpAppender(
                        config.getName(),
                        smtpConfig.getPattern(),
                        smtpConfig.getTo(),
                        smtpConfig.getFrom(),
                        smtpConfig.getSubject(),
                        smtpConfig.getSmtpHost(),
                        smtpConfig.getSmtpPort(),
                        smtpConfig.getSmtpUsername(),
                        smtpConfig.getSmtpPassword());
            } else {
                log.warn("SMTPAppender '{}' requires to, from, and smtpHost properties", config.getName());
            }
        } else {
            log.warn("SMTPAppender configuration must be of type SmtpAppenderConfig");
        }
    }

    private void initRollingRandomAccessFileAppender(AppenderConfig config) {
        if (config instanceof RollingRandomAccessFileAppenderConfig) {
            RollingRandomAccessFileAppenderConfig rollingConfig = (RollingRandomAccessFileAppenderConfig) config;
            Log4j2DynamicConfig.addRollingRandomAccessFileAppender(
                    config.getName(),
                    rollingConfig.getFileName(),
                    rollingConfig.getFilePattern(),
                    rollingConfig.getPattern(),
                    rollingConfig.getSizeLimit(),
                    rollingConfig.getMaxHistory(),
                    rollingConfig.getImmediateFlush() != null ? rollingConfig.getImmediateFlush() : false,
                    rollingConfig.getAppend() != null ? rollingConfig.getAppend() : true);
        } else {
            log.warn(
                    "RollingRandomAccessFileAppender configuration must be of type RollingRandomAccessFileAppenderConfig");
        }
    }

    private void initRandomAccessFileAppender(AppenderConfig config) {
        if (config instanceof RandomAccessFileAppenderConfig) {
            RandomAccessFileAppenderConfig rafConfig = (RandomAccessFileAppenderConfig) config;
            Log4j2DynamicConfig.addRandomAccessFileAppender(
                    config.getName(),
                    rafConfig.getFileName(),
                    rafConfig.getPattern(),
                    rafConfig.getImmediateFlush() != null ? rafConfig.getImmediateFlush() : false,
                    rafConfig.getAppend() != null ? rafConfig.getAppend() : true);
        } else {
            log.warn("RandomAccessFileAppender configuration must be of type RandomAccessFileAppenderConfig");
        }
    }

    private void initFileAppender(AppenderConfig config) {
        if (config instanceof FileAppenderConfig) {
            FileAppenderConfig fileConfig = (FileAppenderConfig) config;
            Log4j2DynamicConfig.addFileAppender(
                    config.getName(),
                    fileConfig.getFileName(),
                    fileConfig.getPattern(),
                    fileConfig.getImmediateFlush() != null ? fileConfig.getImmediateFlush() : true,
                    fileConfig.getAppend() != null ? fileConfig.getAppend() : true);
        } else {
            log.warn("FileAppender configuration must be of type FileAppenderConfig");
        }
    }
}
