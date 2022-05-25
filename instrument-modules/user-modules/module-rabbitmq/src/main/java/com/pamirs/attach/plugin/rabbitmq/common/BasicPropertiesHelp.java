package com.pamirs.attach.plugin.rabbitmq.common;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.MessageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author Licey
 * @date 2022/4/28
 */
public class BasicPropertiesHelp {
    private static final Logger logger = LoggerFactory.getLogger(BasicPropertiesHelp.class);

    public static void main(String[] args) throws IOException {
        long l = System.nanoTime();
        AMQP.BasicProperties plain = MessageProperties.PERSISTENT_TEXT_PLAIN;
        int time = 100000;
        for (int i = 0; i < time; i++) {
//            copyUseStream(plain);
            copy(plain);
        }
        System.out.println((System.nanoTime()-l)/time);
    }

    public static AMQP.BasicProperties copy(AMQP.BasicProperties props) {
        try {
            return copyUseBuilder(props);
        } catch (Throwable e) {
            logger.error("不兼容的 rabbitmq 版本 AMQP.BasicProperties。使用 cotyUseJava 替代");
            return copyUseJava(props);
        }
    }

    private static AMQP.BasicProperties copyUseBuilder(AMQP.BasicProperties props) throws IOException {
        return props == null ? null : props.builder().build();
    }

    private static AMQP.BasicProperties copyUseJava(AMQP.BasicProperties props){
            AMQP.BasicProperties source = props;
            if (source == null) {
                return null;
            }

            AMQP.BasicProperties.Builder builder = new AMQP.BasicProperties.Builder();
            builder.contentType(source.getContentType());
            builder.contentEncoding(source.getContentEncoding());
            builder.headers(source.getHeaders());
            builder.deliveryMode(source.getDeliveryMode());
            builder.priority(source.getPriority());
            builder.correlationId(source.getCorrelationId());
            builder.replyTo(source.getReplyTo());
            builder.expiration(source.getExpiration());
            builder.messageId(source.getMessageId());
            builder.timestamp(source.getTimestamp());
            builder.type(source.getType());
            builder.userId(source.getUserId());
            builder.appId(source.getAppId());
            builder.clusterId(source.getClusterId());
            return builder.build();

    }
}
