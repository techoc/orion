package cn.techoc.oriongateway.core.nettyhandler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;

/**
 * URI 预处理器：在解码层将 URI 中的非法字符（如 |）替换为 URL 编码形式，
 * 确保后续 URI.create() 不会抛出 IllegalArgumentException。
 */
public class UriSanitizingHandler extends ChannelInboundHandlerAdapter {

    // 需要编码的非法字符及其编码映射
    private static final String[][] ILLEGAL_CHAR_MAPPINGS = {
            {"|", "%7C"},
            {"{", "%7B"},
            {"}", "%7D"},
            {"\\", "%5C"},
            {"^", "%5E"},
            {"[", "%5B"},
            {"]", "%5D"},
            {"`", "%60"},
    };

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            String uri = request.uri();
            String sanitized = sanitizeUri(uri);
            if (!uri.equals(sanitized)) {
                request.setUri(sanitized);  // 替换 URI
            }
        }
        ctx.fireChannelRead(msg);  // 传递给下一个 Handler
    }

    private String sanitizeUri(String uri) {
        String result = uri;
        for (String[] mapping : ILLEGAL_CHAR_MAPPINGS) {
            if (result.contains(mapping[0])) {
                result = result.replace(mapping[0], mapping[1]);
            }
        }
        return result;
    }
}

