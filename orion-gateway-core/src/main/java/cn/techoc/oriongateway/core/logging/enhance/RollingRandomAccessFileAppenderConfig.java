package cn.techoc.oriongateway.core.logging.enhance;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 滚动 RandomAccessFile Appender 配置
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RollingRandomAccessFileAppenderConfig extends AbstractFileAppenderConfig {
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

    public RollingRandomAccessFileAppenderConfig() {
        super();
    }

    public RollingRandomAccessFileAppenderConfig(String name) {
        super(name);
    }

    public RollingRandomAccessFileAppenderConfig(String name, String fileName) {
        super(name, fileName);
    }

    public RollingRandomAccessFileAppenderConfig(String name, String fileName, String pattern) {
        super(name, fileName, pattern);
    }

    @Override
    public AppenderType getType() {
        return AppenderType.ROLLING_RANDOM_ACCESS_FILE;
    }
}
