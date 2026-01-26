package cn.techoc.oriongateway.core.logging.enhance;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 普通文件 Appender 配置
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileAppenderConfig extends AbstractFileAppenderConfig {
    public FileAppenderConfig() {
        super();
    }

    public FileAppenderConfig(String name) {
        super(name);
    }

    public FileAppenderConfig(String name, String fileName) {
        super(name, fileName);
    }

    public FileAppenderConfig(String name, String fileName, String pattern) {
        super(name, fileName, pattern);
    }

    @Override
    public AppenderType getType() {
        return AppenderType.FILE;
    }
}
