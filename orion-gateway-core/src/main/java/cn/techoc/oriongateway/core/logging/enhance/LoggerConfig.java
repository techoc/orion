package cn.techoc.oriongateway.core.logging.enhance;

import lombok.Data;
import org.apache.logging.log4j.Level;

@Data
public class LoggerConfig {
    private String name;
    private String appenderName;
    private Level level = Level.INFO;
    private boolean additivity = false;
}
