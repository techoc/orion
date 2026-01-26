package cn.techoc.oriongateway.core.logging.enhance;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 滚动文件 Appender 配置
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RollingFileAppenderConfig extends AbstractFileAppenderConfig {
    /**
     * 文件滚动模式
     */
    private String filePattern;

    /**
     * 单个文件大小限制
     */
    private String sizeLimit = "10MB";

    /**
     * 最大历史文件数
     */
    private int maxHistory = 7;

    public RollingFileAppenderConfig() {
        super();
    }

    public RollingFileAppenderConfig(String name) {
        super(name);
    }

    public RollingFileAppenderConfig(String name, String fileName) {
        super(name, fileName);
    }

    public RollingFileAppenderConfig(String name, String fileName, String pattern) {
        super(name, fileName, pattern);
    }

    @Override
    public AppenderType getType() {
        return AppenderType.ROLLING_FILE;
    }
}
