package cn.techoc.oriongateway.core.trace;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 响应装饰器类，用于包装原始响应以支持响应体的多次读取和追踪
 * <p>
 * 该类通过缓存响应体数据来确保响应体可以被多次读取，解决了Reactive Streams中
 * DataBuffer只能被消费一次的问题。同时支持响应体在入口和出口阶段的日志记录功能。
 */
public class LinkTracingResponseDecorator extends ServerHttpResponseDecorator {

    // 请求ID，用于日志关联
    private final String requestId;

    // 请求后缀阶段日志回调函数（在响应处理开始前执行）
    private final Runnable requestSuffixCallback;

    // 下游响应进入网关时日志回调函数
    private final Consumer<String> responsePrefixBodyConsumer;

    // 下游响应离开网关时日志回调函数
    private final Consumer<String> responseSuffixBodyConsumer;

    // 最大响应体大小限制
    private final int maxBodySize;

    // 是否在下游响应进入网关时记录响应体
    private final boolean tracePrefix;

    // 是否在下游响应离开网关时记录响应体
    private final boolean traceSuffix;

    // 使用AtomicReference存储缓存的响应体字节数组
    // 通过字节数组缓存确保响应体可以被多次读取
    private final AtomicReference<byte[]> cachedBody = new AtomicReference<>();

    /**
     * 构造一个LinkTracingResponseDecorator实例，用于装饰ServerHttpResponse对象，以支持响应的链路追踪。
     * 该装饰器允许在响应前后添加自定义的行为，并且可以控制是否记录响应体以及其大小限制。
     *
     * @param requestId                  请求ID，用于标识请求
     * @param delegate                   被装饰的ServerHttpResponse对象
     * @param requestSuffixCallback      在请求后执行的回调
     * @param responsePrefixBodyConsumer 响应前体数据消费者
     * @param responseSuffixBodyConsumer 响应后体数据消费者
     * @param maxBodySize                允许记录的最大响应体大小（字节），超过此值将被截断
     * @param tracePrefix                是否在响应前进行追踪
     * @param traceSuffix                是否在响应后进行追踪
     */
    public LinkTracingResponseDecorator(
            String requestId,
            ServerHttpResponse delegate,
            Runnable requestSuffixCallback,
            Consumer<String> responsePrefixBodyConsumer,
            Consumer<String> responseSuffixBodyConsumer,
            int maxBodySize,
            boolean tracePrefix,
            boolean traceSuffix) {
        super(delegate);
        this.requestId = requestId;
        this.requestSuffixCallback = requestSuffixCallback;
        this.responsePrefixBodyConsumer = responsePrefixBodyConsumer;
        this.responseSuffixBodyConsumer = responseSuffixBodyConsumer;
        this.maxBodySize = maxBodySize;
        this.tracePrefix = tracePrefix;
        this.traceSuffix = traceSuffix;
    }

    /**
     * 重写writeWith方法，支持响应体的多次读取和追踪
     * <p>
     * 该方法的执行逻辑如下：
     * 1. 收集整个响应体数据
     * 2. 将响应体数据转换为字节数组并缓存
     * 3. 在记录日志之前复制响应数据
     * 4. 记录下游响应进入网关时日志
     * 5. 创建新的DataBuffer用于发送给客户端
     * 6. 释放原始DataBuffer资源
     * 7. 发送响应给客户端
     * 8. 记录下游响应离开网关时日志
     * 9. 释放响应缓冲区资源
     *
     * @param body 原始响应体数据
     * @return Mono<Void> 异步处理结果
     */
    @Override
    public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
        // 在开始处理响应之前，先记录 Request_suffix 阶段
        if (requestSuffixCallback != null) {
            requestSuffixCallback.run();
        }

        // 只有当需要追踪响应体时才进行处理
        if (tracePrefix || traceSuffix) {
            // 先收集整个响应体
            return DataBufferUtils.join(body).flatMap(dataBuffer -> {
                // 将响应体转换为字节数组并缓存
                byte[] bodyBytes = convertBufferToBytes(dataBuffer);
                cachedBody.set(bodyBytes.clone());

                // 获取响应体字符串表示
                String bodyString = convertBytesToString(bodyBytes);

                // 在记录日志之前复制整个响应
                byte[] logBodyBytes = new byte[bodyBytes.length];
                System.arraycopy(bodyBytes, 0, logBodyBytes, 0, bodyBytes.length);

                // 记录下游响应进入网关时日志
                if (tracePrefix) {
                    String logBodyString = convertBytesToString(logBodyBytes);
                    responsePrefixBodyConsumer.accept(logBodyString);
                }

                // 创建一个新的DataBuffer用于发送给客户端
                DataBuffer responseBuffer = this.bufferFactory().allocateBuffer(bodyBytes.length);
                responseBuffer.write(bodyBytes);

                // 释放原始的DataBuffer
                DataBufferUtils.release(dataBuffer);

                // 发送响应给客户端
                return super.writeWith(Mono.just(responseBuffer))
                        .doOnSuccess(aVoid -> {
                            // 记录下游响应离开网关时日志
                            if (traceSuffix) {
                                // 再次复制用于出口日志
                                byte[] exitLogBodyBytes = new byte[bodyBytes.length];
                                System.arraycopy(bodyBytes, 0, exitLogBodyBytes, 0, bodyBytes.length);
                                String exitLogBodyString = convertBytesToString(exitLogBodyBytes);
                                responseSuffixBodyConsumer.accept(exitLogBodyString);
                            }
                        })
                        .doFinally(signalType -> DataBufferUtils.release(responseBuffer)); // 释放响应缓冲区
            });
        }

        // 如果不需要追踪响应体，直接调用父类方法
        return super.writeWith(body);
    }

    /**
     * 重写writeAndFlushWith方法，处理分块响应
     *
     * @param body 分块响应体数据
     * @return Mono<Void> 异步处理结果
     */
    @Override
    public Mono<Void> writeAndFlushWith(
            org.reactivestreams.Publisher<? extends org.reactivestreams.Publisher<? extends DataBuffer>> body) {
        // 对于分块响应，实现更复杂。此实现专注于非分块响应。
        // 更健壮的分块体追踪解决方案需要拦截每个块。
        // 为简单起见，如果分块则直接委托。
        // 如果需要完整的分块体追踪，需要在每个内部发布者上应用DataBufferUtils.join()。
        return writeWith(Flux.from(body).flatMap(p -> p));
    }

    /**
     * 将DataBuffer转换为字节数组
     *
     * @param dataBuffer 要转换的DataBuffer
     * @return byte[] 转换后的字节数组
     */
    private byte[] convertBufferToBytes(DataBuffer dataBuffer) {
        // 处理空DataBuffer的情况
        if (dataBuffer == null || dataBuffer.readableByteCount() == 0) {
            return new byte[0];
        }

        // 获取DataBuffer的容量
        int capacity = dataBuffer.readableByteCount();

        // 将DataBuffer转换为ByteBuffer
        ByteBuffer byteBuffer = dataBuffer.asByteBuffer();

        byte[] bytes;
        // 根据最大大小限制处理数据
        if (maxBodySize != -1 && capacity > maxBodySize) {
            // 如果超过最大大小限制，截取指定长度的数据
            bytes = new byte[maxBodySize];
            byteBuffer.get(bytes, 0, maxBodySize);
        } else {
            // 如果未超过最大大小限制，获取所有数据
            bytes = new byte[capacity];
            byteBuffer.get(bytes);
        }

        return bytes;
    }

    /**
     * 将字节数组转换为字符串
     *
     * @param bytes 要转换的字节数组
     * @return String 转换后的字符串
     */
    private String convertBytesToString(byte[] bytes) {
        // 处理空字节数组的情况
        if (bytes == null || bytes.length == 0) {
            String phase = this.tracePrefix ? "Response_prefix" : "Response_suffix";
            return "[" + phase + " Empty Body]";
        }

        // 将字节数组转换为字符串
        String bodyString = new String(bytes, StandardCharsets.UTF_8);

        // 将所有换行符替换为空格，确保输出在一行中
        bodyString = bodyString.replaceAll("\\r?\\n", " ");

        // 根据最大大小限制处理数据
        if (maxBodySize != -1 && bytes.length > maxBodySize) {
            String phase = this.tracePrefix ? "Response_prefix" : "Response_suffix";
            return bodyString + String.format("... [%s Body truncated, original size: %d bytes]", phase, bytes.length);
        }

        return bodyString;
    }
}
