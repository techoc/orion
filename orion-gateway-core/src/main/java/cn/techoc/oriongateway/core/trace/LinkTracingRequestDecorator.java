package cn.techoc.oriongateway.core.trace;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 请求装饰器类，用于包装原始请求以支持请求体的多次读取和追踪
 * <p>
 * 该类通过缓存请求体数据来确保请求体可以被多次读取，解决了Reactive Streams中
 * DataBuffer只能被消费一次的问题。同时支持请求体的日志记录功能。
 */
public class LinkTracingRequestDecorator extends ServerHttpRequestDecorator {

    private static final Logger log = LoggerFactory.getLogger(LinkTracingRequestDecorator.class);

    // 请求ID，用于日志关联
    private final String requestId;

    // 请求体日志回调函数
    private final Consumer<String> bodyConsumer;

    // 最大请求体大小限制
    private final int maxBodySize;

    // 是否在上游请求离开网关进入下游时记录请求体
    private final boolean traceSuffix;

    // DataBuffer工厂，用于创建新的DataBuffer实例
    private final DataBufferFactory dataBufferFactory;

    // 使用AtomicReference存储缓存的请求体字节数组
    // 通过字节数组缓存确保请求体可以被多次读取
    private final AtomicReference<byte[]> cachedBody = new AtomicReference<>();

    /**
     * 构造函数
     *
     * @param requestId         请求ID，用于日志关联
     * @param delegate          原始ServerHttpRequest对象
     * @param bodyConsumer      请求体日志回调函数
     * @param maxBodySize       最大请求体大小限制
     * @param traceSuffix       是否在请求出口阶段记录请求体
     * @param dataBufferFactory DataBuffer工厂
     */
    public LinkTracingRequestDecorator(
            String requestId,
            ServerHttpRequest delegate,
            Consumer<String> bodyConsumer,
            int maxBodySize,
            boolean traceSuffix,
            DataBufferFactory dataBufferFactory) {
        super(delegate);
        this.requestId = requestId;
        this.bodyConsumer = bodyConsumer;
        this.maxBodySize = maxBodySize;
        this.traceSuffix = traceSuffix;
        this.dataBufferFactory = dataBufferFactory;
    }

    /**
     * 重写getBody方法，支持请求体的多次读取
     * <p>
     * 该方法的执行逻辑如下：
     * 1. 如果请求体已缓存，直接从缓存创建新的DataBuffer返回
     * 2. 如果请求体未缓存，收集原始请求体数据
     * 3. 将请求体数据转换为字节数组并缓存
     * 4. 如果需要记录日志，调用回调函数记录请求体
     * 5. 释放原始DataBuffer资源
     * 6. 从缓存的字节数组创建新的DataBuffer返回
     *
     * @return Flux<DataBuffer> 包含请求体数据的Flux
     */
    @Override
    public Flux<DataBuffer> getBody() {
        // 检查请求体是否已缓存
        byte[] bodyBytes = cachedBody.get();
        if (bodyBytes != null) {
            // 如果已缓存，从缓存的字节数组创建新的DataBuffer
            DataBuffer buffer = dataBufferFactory.allocateBuffer(bodyBytes.length);
            buffer.write(bodyBytes);
            return Flux.just(buffer);
        }

        // 如果未缓存，收集原始请求体数据
        return super.getBody()
                .collectList()
                .map(this::processRequestBody)
                .flatMapMany((Function<Flux<DataBuffer>, Publisher<DataBuffer>>) flux -> flux)
                .doOnError(throwable -> {
                    // 发生错误时清除缓存
                    cachedBody.set(null);
                    log.error("[{}] Error processing request body: {}", requestId, throwable.getMessage(), throwable);
                });
    }

    /**
     * 处理请求体数据
     *
     * @param dataBufferList 包含请求体数据的DataBuffer列表
     * @return Flux<DataBuffer> 处理后的DataBuffer流
     */
    private Flux<DataBuffer> processRequestBody(List<DataBuffer> dataBufferList) {
        // 处理空请求体的情况
        if (dataBufferList.isEmpty()) {
            cachedBody.set(new byte[0]);
            if (traceSuffix) {
                bodyConsumer.accept("[Empty Body]");
            }
            return Flux.empty();
        }

        // 合并所有DataBuffer为一个
        DataBuffer dataBuffer = dataBufferFactory.join(dataBufferList);

        // 将DataBuffer转换为字节数组并缓存
        byte[] bodyBytes = convertBufferToBytes(dataBuffer);
        cachedBody.set(bodyBytes);

        // 如果需要记录日志，调用回调函数记录请求体
        if (traceSuffix) {
            // 创建副本用于日志记录，避免影响原始数据
            byte[] logBodyBytes = new byte[bodyBytes.length];
            System.arraycopy(bodyBytes, 0, logBodyBytes, 0, bodyBytes.length);
            String bodyString = convertBytesToString(logBodyBytes);
            bodyConsumer.accept(bodyString);
        }

        // 释放原始DataBuffer资源
        DataBufferUtils.release(dataBuffer);
        dataBufferList.forEach(DataBufferUtils::release);

        // 从缓存的字节数组创建新的DataBuffer
        DataBuffer responseBuffer = dataBufferFactory.allocateBuffer(bodyBytes.length);
        responseBuffer.write(bodyBytes);
        return Flux.just(responseBuffer);
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
            return "[Empty Body]";
        }

        // 将字节数组转换为字符串
        String bodyString = new String(bytes, StandardCharsets.UTF_8);

        // 根据最大大小限制处理数据
        if (maxBodySize != -1 && bytes.length > maxBodySize) {
            // 如果超过最大大小限制，添加截断信息
            return bodyString + String.format("... [Body truncated, original size: %d bytes]", bytes.length);
        }

        return bodyString;
    }
}
