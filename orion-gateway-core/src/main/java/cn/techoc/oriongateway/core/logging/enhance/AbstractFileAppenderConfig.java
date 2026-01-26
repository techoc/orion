package cn.techoc.oriongateway.core.logging.enhance;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件 Appender 配置基类
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractFileAppenderConfig extends AppenderConfig {
    /**
     * 文件路径
     */
    private String fileName;

    /**
     * 是否追加模式
     */
    private Boolean append = true;

    /**
     * 是否立即刷新
     */
    private Boolean immediateFlush;

    public AbstractFileAppenderConfig() {
        super();
    }

    public AbstractFileAppenderConfig(String name) {
        super(name);
    }

    public AbstractFileAppenderConfig(String name, String fileName) {
        super(name);
        this.fileName = fileName;
    }

    public AbstractFileAppenderConfig(String name, String fileName, String pattern) {
        super(name);
        this.fileName = fileName;
        setPattern(pattern);
    }
}
