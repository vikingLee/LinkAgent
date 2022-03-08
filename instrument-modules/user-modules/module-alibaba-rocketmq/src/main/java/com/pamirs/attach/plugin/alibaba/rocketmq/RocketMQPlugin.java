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
package com.pamirs.attach.plugin.alibaba.rocketmq;

import com.pamirs.attach.plugin.alibaba.rocketmq.interceptor.DefaultMQPushConsumerImplHasHookListener;
import com.pamirs.attach.plugin.alibaba.rocketmq.interceptor.DefaultPushConsumerCreateTopicInterceptor;
import com.pamirs.attach.plugin.alibaba.rocketmq.interceptor.DefaultPushConsumerEarliestMsgStoreTimeInterceptor;
import com.pamirs.attach.plugin.alibaba.rocketmq.interceptor.DefaultPushConsumerFetchSubscribeMessageQueuesInterceptor;
import com.pamirs.attach.plugin.alibaba.rocketmq.interceptor.DefaultPushConsumerMaxOffsetInterceptor;
import com.pamirs.attach.plugin.alibaba.rocketmq.interceptor.DefaultPushConsumerMinOffsetInterceptor;
import com.pamirs.attach.plugin.alibaba.rocketmq.interceptor.DefaultPushConsumerQueryMessageInterceptor;
import com.pamirs.attach.plugin.alibaba.rocketmq.interceptor.DefaultPushConsumerSearchOffsetInterceptor;
import com.pamirs.attach.plugin.alibaba.rocketmq.interceptor.DefaultPushConsumerSendMessageBackInterceptor;
import com.pamirs.attach.plugin.alibaba.rocketmq.interceptor.DefaultPushConsumerShutdownInterceptor;
import com.pamirs.attach.plugin.alibaba.rocketmq.interceptor.DefaultPushConsumerViewMessageInterceptor;
import com.pamirs.attach.plugin.alibaba.rocketmq.interceptor.MQProducerInterceptor;
import com.pamirs.attach.plugin.alibaba.rocketmq.interceptor.MQProducerSendInterceptor;
import com.pamirs.attach.plugin.alibaba.rocketmq.interceptor.OrderlyTraceAfterInterceptor;
import com.pamirs.attach.plugin.alibaba.rocketmq.interceptor.OrderlyTraceBeforeInterceptor;
import com.pamirs.attach.plugin.alibaba.rocketmq.interceptor.OrderlyTraceContextInterceptor;
import com.pamirs.attach.plugin.alibaba.rocketmq.interceptor.PullConsumerInterceptor;
import com.pamirs.attach.plugin.alibaba.rocketmq.interceptor.ConcurrentlyTraceInterceptor;
import com.pamirs.attach.plugin.alibaba.rocketmq.interceptor.TransactionCheckInterceptor;
import com.shulie.instrument.simulator.api.ExtensionModule;
import com.shulie.instrument.simulator.api.ModuleInfo;
import com.shulie.instrument.simulator.api.ModuleLifecycleAdapter;
import com.shulie.instrument.simulator.api.instrument.EnhanceCallback;
import com.shulie.instrument.simulator.api.instrument.InstrumentClass;
import com.shulie.instrument.simulator.api.instrument.InstrumentMethod;
import com.shulie.instrument.simulator.api.listener.Listeners;
import org.kohsuke.MetaInfServices;

/**
 * @author vincent
 */
@MetaInfServices(ExtensionModule.class)
@ModuleInfo(id = RocketmqConstants.MODULE_NAME, version = "1.0.0", author = "xiaobin@shulie.io", description = "阿里巴巴rocketmq消息中间件")
public class RocketMQPlugin extends ModuleLifecycleAdapter implements ExtensionModule {

    @Override
    public boolean onActive() throws Throwable {
        return addHookRegisterInterceptor();
    }

    private boolean addHookRegisterInterceptor() {
        this.enhanceTemplate.enhance(this,
                "com.alibaba.rocketmq.client.consumer.DefaultMQPushConsumer", new EnhanceCallback() {
                    @Override
                    public void doEnhance(InstrumentClass target) {
                        final InstrumentMethod createTopicMethod = target.getDeclaredMethods("createTopic");
                        createTopicMethod.addInterceptor(Listeners.of(DefaultPushConsumerCreateTopicInterceptor.class));

                        final InstrumentMethod earliestMsgStoreTimeMethod = target.getDeclaredMethods("earliestMsgStore");
                        earliestMsgStoreTimeMethod.addInterceptor(Listeners.of(DefaultPushConsumerEarliestMsgStoreTimeInterceptor.class));

                        final InstrumentMethod searchOffsetMethod = target.getDeclaredMethods("searchOffset");
                        searchOffsetMethod.addInterceptor(Listeners.of(DefaultPushConsumerSearchOffsetInterceptor.class));

                        final InstrumentMethod maxOffsetMethod = target.getDeclaredMethods("maxOffset");
                        maxOffsetMethod.addInterceptor(Listeners.of(DefaultPushConsumerMaxOffsetInterceptor.class));

                        final InstrumentMethod minOffsetMethod = target.getDeclaredMethods("minOffset");
                        minOffsetMethod.addInterceptor(Listeners.of(DefaultPushConsumerMinOffsetInterceptor.class));

                        final InstrumentMethod viewMessageMethod = target.getDeclaredMethods("viewMessage");
                        viewMessageMethod.addInterceptor(Listeners.of(DefaultPushConsumerViewMessageInterceptor.class));

                        final InstrumentMethod queryMessageMethod = target.getDeclaredMethods("queryMessage");
                        queryMessageMethod.addInterceptor(Listeners.of(DefaultPushConsumerQueryMessageInterceptor.class));

                        final InstrumentMethod fetchSubscribeMessageQueuesMethod = target.getDeclaredMethods("fetchSubscribeMessageQueues");
                        fetchSubscribeMessageQueuesMethod.addInterceptor(Listeners.of(DefaultPushConsumerFetchSubscribeMessageQueuesInterceptor.class));

                        final InstrumentMethod sendMessageBackMethod = target.getDeclaredMethods("sendMessageBack");
                        sendMessageBackMethod.addInterceptor(Listeners.of(DefaultPushConsumerSendMessageBackInterceptor.class));

                        final InstrumentMethod shutdownMethod = target.getDeclaredMethod("shutdown");
                        shutdownMethod.addInterceptor(Listeners.of(DefaultPushConsumerShutdownInterceptor.class));
                    }
                });

        this.enhanceTemplate.enhance(this, "com.alibaba.rocketmq.client.impl.consumer.DefaultMQPushConsumerImpl", new EnhanceCallback() {
            @Override
            public void doEnhance(InstrumentClass target) {
                final InstrumentMethod hasHookMethod = target.getDeclaredMethod("hasHook");
                hasHookMethod.addInterceptor(Listeners.of(DefaultMQPushConsumerImplHasHookListener.class));
            }
        });


        this.enhanceTemplate.enhance(this,
                "com.alibaba.rocketmq.client.impl.consumer.DefaultMQPullConsumerImpl", new EnhanceCallback() {
                    @Override
                    public void doEnhance(InstrumentClass target) {

                        target.getDeclaredMethod("pullSyncImpl", "com.alibaba.rocketmq.common.message.MessageQueue", "java.lang.String", "long", "int", "boolean", "long")
                                .addInterceptor(Listeners.of(PullConsumerInterceptor.class));

                    }
                });

        this.enhanceTemplate.enhance(this, "com.alibaba.rocketmq.client.producer.DefaultMQProducer", new EnhanceCallback() {
            @Override
            public void doEnhance(InstrumentClass target) {

                target.getDeclaredMethod("start")
                        .addInterceptor(Listeners.of(MQProducerInterceptor.class));

                //压测代码
                InstrumentMethod sendMethod = target.getDeclaredMethods("send*");
                sendMethod.addInterceptor(Listeners.of(MQProducerSendInterceptor.class));

            }
        });

        this.enhanceTemplate.enhance(this, "com.alibaba.rocketmq.client.impl.producer.DefaultMQProducerImpl", new EnhanceCallback() {
            @Override
            public void doEnhance(InstrumentClass target) {
                //压测代码
                InstrumentMethod method = target.getDeclaredMethods("sendDefaultImpl");
                method.addInterceptor(Listeners.of(MQProducerSendInterceptor.class));

                InstrumentMethod sendMessageInTransactionMethod = target.getDeclaredMethods("sendMessageInTransaction");
                sendMessageInTransactionMethod.addInterceptor(Listeners.of(MQProducerSendInterceptor.class));

                //压测代码 end
            }
        });


        this.enhanceTemplate.enhance(this, "com.alibaba.rocketmq.client.impl.producer.DefaultMQProducerImpl", new EnhanceCallback() {
            @Override
            public void doEnhance(InstrumentClass target) {
                InstrumentMethod enhanceMethod = target.getDeclaredMethods("checkTransactionState*");
                enhanceMethod.addInterceptor(Listeners.of(TransactionCheckInterceptor.class));
            }
        });

        this.enhanceTemplate.enhance(this,
            "com.alibaba.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyService$ConsumeRequest",
            new EnhanceCallback() {
                @Override
                public void doEnhance(InstrumentClass target) {
                    InstrumentMethod enhanceMethod = target.getDeclaredMethods("run");
                    enhanceMethod.addInterceptor(Listeners.of(ConcurrentlyTraceInterceptor.class));
                }
            });

        //--for orderly
        this.enhanceTemplate.enhance(this,
            "com.alibaba.rocketmq.client.impl.consumer.ConsumeMessageOrderlyService$ConsumeRequest", new EnhanceCallback() {
                @Override
                public void doEnhance(InstrumentClass target) {
                    InstrumentMethod enhanceMethod = target.getDeclaredMethods("run");
                    enhanceMethod.addInterceptor(Listeners.of(OrderlyTraceContextInterceptor.class));
                }
            });

        this.enhanceTemplate.enhance(this,
            "com.alibaba.rocketmq.client.impl.consumer.ProcessQueue", new EnhanceCallback() {
                @Override
                public void doEnhance(InstrumentClass target) {
                    InstrumentMethod enhanceMethod = target.getDeclaredMethods("takeMessags");
                    enhanceMethod.addInterceptor(Listeners.of(OrderlyTraceBeforeInterceptor.class));
                }
            });

        this.enhanceTemplate.enhance(this,
            "com.alibaba.rocketmq.client.impl.consumer.ConsumeMessageOrderlyService", new EnhanceCallback() {
                @Override
                public void doEnhance(InstrumentClass target) {
                    InstrumentMethod enhanceMethod = target.getDeclaredMethods("processConsumeResult");
                    enhanceMethod.addInterceptor(Listeners.of(OrderlyTraceAfterInterceptor.class));
                }
            });

        //--for orderly

        return true;
    }

}
