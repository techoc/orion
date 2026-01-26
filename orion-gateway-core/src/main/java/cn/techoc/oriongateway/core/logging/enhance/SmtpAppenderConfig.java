package cn.techoc.oriongateway.core.logging.enhance;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SMTP Appender 配置
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SmtpAppenderConfig extends AppenderConfig {
    /**
     * 收件人邮箱
     */
    private String to;

    /**
     * 发件人邮箱
     */
    private String from;

    /**
     * 邮件主题
     */
    private String subject;

    /**
     * SMTP 服务器地址
     */
    private String smtpHost;

    /**
     * SMTP 端口
     */
    private Integer smtpPort;

    /**
     * SMTP 用户名
     */
    private String smtpUsername;

    /**
     * SMTP 密码
     */
    private String smtpPassword;

    public SmtpAppenderConfig() {
        super();
    }

    public SmtpAppenderConfig(String name) {
        super(name);
    }

    public SmtpAppenderConfig(String name, String pattern) {
        super(name);
        setPattern(pattern);
    }

    @Override
    public AppenderType getType() {
        return AppenderType.SMTP;
    }
}
