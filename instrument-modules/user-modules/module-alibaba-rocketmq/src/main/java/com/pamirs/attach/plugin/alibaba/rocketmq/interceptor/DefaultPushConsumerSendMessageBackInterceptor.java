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
package com.pamirs.attach.plugin.alibaba.rocketmq.interceptor;

import com.alibaba.rocketmq.client.consumer.DefaultMQPushConsumer;
import com.alibaba.rocketmq.common.message.MessageExt;

import com.pamirs.attach.plugin.alibaba.rocketmq.destroy.MqDestroy;
import com.pamirs.pradar.CutOffResult;
import com.pamirs.pradar.exception.PressureMeasureError;
import com.shulie.instrument.simulator.api.annotation.Destroyable;
import com.shulie.instrument.simulator.api.listener.ext.Advice;

/**
 * @author xiaobin.zfb|xiaobin@shulie.io
 * @since 2020/11/30 4:20 下午
 */
@Destroyable(MqDestroy.class)
public class DefaultPushConsumerSendMessageBackInterceptor extends AbstractUseShadowConsumerReplaceInterceptor {

    @Override
    protected CutOffResult execute(DefaultMQPushConsumer consumer, Advice advice) {
        Object[] args = advice.getParameterArray();
        if (args.length == 2) {
            try {
                consumer.sendMessageBack((MessageExt)args[0], (Integer)args[1]);
                return CutOffResult.cutoff(null);
            } catch (Throwable e) {
                logger.error("Alibaba-RocketMQ: sendMessageBack err, msg: {}, delayLevel: {}", args[0], args[1], e);
                throw new PressureMeasureError(e);
            }
        } else if (args.length == 3) {
            try {
                consumer.sendMessageBack((MessageExt)args[0], (Integer)args[1], (String)args[2]);
                return CutOffResult.cutoff(null);
            } catch (Throwable e) {
                logger.error("Alibaba-RocketMQ: sendMessageBack err, msg: {}, delayLevel: {}, brokerName: {}", args[0],
                    args[1], args[2], e);
                throw new PressureMeasureError(e);
            }
        } else {
            logger.error("Apache-RocketMQ: unsupported sendMessageBack method,args length: {}, args: {}", args.length, args);
            throw new PressureMeasureError(
                "Apache-RocketMQ: unsupported sendMessageBack method,args length: " + args.length + ", args: " + args);
        }
    }
}
