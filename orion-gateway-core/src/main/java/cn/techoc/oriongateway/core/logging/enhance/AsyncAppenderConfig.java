package cn.techoc.oriongateway.core.logging.enhance;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Async Appender 配置
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AsyncAppenderConfig extends AppenderConfig {
    /**
     * 要包装的 Appender 名称数组
     */
    private String[] appenderRefs;

    /**
     * 缓冲区大小
     */
    private Integer bufferSize = 256;

    /**
     * 是否阻塞
     */
    private Boolean blocking = false;

    /**
     * 是否包含位置信息
     */
    private Boolean includeLocation = false;

    public AsyncAppenderConfig() {
        super();
    }

    public AsyncAppenderConfig(String name) {
        super(name);
    }

    public AsyncAppenderConfig(String name, String pattern) {
        super(name);
        setPattern(pattern);
    }

    @Override
    public AppenderType getType() {
        return AppenderType.ASYNC;
    }
}
