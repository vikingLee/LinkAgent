/**
 * Copyright 2021 Shulie Technology, Co.Ltd
 * Email: shulie@shulie.io
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pamirs.attach.plugin.rabbitmq.consumer;

import java.util.Map;

import com.pamirs.attach.plugin.rabbitmq.common.ConsumerDetail;
import com.pamirs.pradar.Pradar;
import com.pamirs.pradar.exception.PradarException;
import com.rabbitmq.client.Consumer;
import com.shulie.instrument.simulator.api.reflect.Reflect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.listener.BlockingQueueConsumer;

/**
 * @author jirenhe | jirenhe@shulie.io
 * @since 2021/11/25 1:43 下午
 */
public class SpringConsumerMetaDataBuilder implements ConsumerMetaDataBuilder {

    private final static SpringConsumerMetaDataBuilder INSTANCE = new SpringConsumerMetaDataBuilder();

    public static SpringConsumerMetaDataBuilder getInstance() {
        return INSTANCE;
    }

    private final Logger logger = LoggerFactory.getLogger(SpringConsumerMetaDataBuilder.class);

    private SpringConsumerMetaDataBuilder() {}

    @Override
    public ConsumerMetaData tryBuild(ConsumerDetail consumerDetail) {
        Consumer consumer = consumerDetail.getConsumer();
        String consumerTag = consumerDetail.getConsumerTag();
        if (!consumer.getClass().getName().equals(
            "org.springframework.amqp.rabbit.listener.BlockingQueueConsumer$InternalConsumer")) {
            return null;
        }
        try {
            return highVersion(consumer, consumerTag);
        } catch (Throwable e) {
            logger.warn("[RabbitMQ] get ConsumerMetaData from spring rabbitmq fail, try lowVersion");
            try {
                return lowVersion(consumer, consumerTag);
            } catch (Throwable ie) {
                throw new PradarException("spring rabbitmq 版本不支持！", ie);
            }
        }
    }

    private ConsumerMetaData highVersion(Consumer consumer, String consumerTag) {
        BlockingQueueConsumer blockingQueueConsumer = Reflect.on(consumer).get("this$0");
        return new ConsumerMetaData(
            Reflect.on(consumer).<String>get("queueName"),
            consumerTag,
            consumer,
            Reflect.on(blockingQueueConsumer).<Boolean>get("exclusive"),
            Reflect.on(blockingQueueConsumer).<AcknowledgeMode>get("acknowledgeMode").isAutoAck(),
            Reflect.on(blockingQueueConsumer).<Integer>get("prefetchCount"),
            true, true);
    }

    private ConsumerMetaData lowVersion(Consumer consumer, String consumerTag) {
        BlockingQueueConsumer blockingQueueConsumer = Reflect.on(consumer).get("this$0");
        Map<String, String> consumerTags = Reflect.on(blockingQueueConsumer).get("consumerTags");
        String queue = consumerTags.get(consumerTag);
        if (queue == null) {
            throw new RuntimeException("this should never happened!");
        }
        final ConsumerMetaData consumerMetaData = new ConsumerMetaData(
            queue,
            consumerTag,
            consumer,
            Reflect.on(blockingQueueConsumer).<Boolean>get("exclusive"),
            Reflect.on(blockingQueueConsumer).<AcknowledgeMode>get("acknowledgeMode").isAutoAck(),
            Reflect.on(blockingQueueConsumer).<Integer>get("prefetchCount"),
            true);
        consumerTags.put(consumerMetaData.getPtConsumerTag(), consumerMetaData.getPtQueue());
        return consumerMetaData;
    }
}
