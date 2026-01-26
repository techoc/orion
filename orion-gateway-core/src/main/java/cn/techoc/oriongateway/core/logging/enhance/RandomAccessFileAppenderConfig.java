package cn.techoc.oriongateway.core.logging.enhance;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * RandomAccessFile Appender 配置
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RandomAccessFileAppenderConfig extends AbstractFileAppenderConfig {
    public RandomAccessFileAppenderConfig() {
        super();
    }

    public RandomAccessFileAppenderConfig(String name) {
        super(name);
    }

    public RandomAccessFileAppenderConfig(String name, String fileName) {
        super(name, fileName);
    }

    public RandomAccessFileAppenderConfig(String name, String fileName, String pattern) {
        super(name, fileName, pattern);
    }

    @Override
    public AppenderType getType() {
        return AppenderType.RANDOM_ACCESS_FILE;
    }
}
