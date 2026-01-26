package cn.techoc.oriongateway.core.logging.enhance;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.logging.log4j.core.appender.ConsoleAppender;

/**
 * Console Appender 配置
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConsoleAppenderConfig extends AppenderConfig {
    /**
     * 控制台输出目标
     */
    private ConsoleAppender.Target target = ConsoleAppender.Target.SYSTEM_OUT;

    public ConsoleAppenderConfig() {
        super();
    }

    public ConsoleAppenderConfig(String name) {
        super(name);
    }

    public ConsoleAppenderConfig(String name, String pattern) {
        super(name);
        setPattern(pattern);
    }

    @Override
    public AppenderType getType() {
        return AppenderType.CONSOLE;
    }
}
