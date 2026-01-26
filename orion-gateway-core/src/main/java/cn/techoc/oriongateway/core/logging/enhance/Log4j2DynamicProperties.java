package cn.techoc.oriongateway.core.logging.enhance;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "orion.logging")
public class Log4j2DynamicProperties {

    /**
     * 是否启用动态日志配置
     */
    private boolean enabled = true;

    /**
     * 动态 Appender 配置列表
     */
    private List<AppenderConfig> appenders = new ArrayList<>();

    /**
     * 动态 Logger 配置列表
     */
    private List<LoggerConfig> loggers = new ArrayList<>();
}
