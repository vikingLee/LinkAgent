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
package com.pamirs.attach.plugin.rabbitmq.interceptor;

import com.pamirs.attach.plugin.rabbitmq.RabbitmqConstants;
import com.pamirs.attach.plugin.rabbitmq.destroy.RabbitmqDestroy;
import com.pamirs.pradar.Pradar;
import com.pamirs.pradar.PradarService;
import com.pamirs.pradar.PradarSwitcher;
import com.pamirs.pradar.ResultCode;
import com.pamirs.pradar.exception.PressureMeasureError;
import com.pamirs.pradar.interceptor.SpanRecord;
import com.pamirs.pradar.interceptor.TraceInterceptorAdaptor;
import com.pamirs.pradar.pressurement.ClusterTestUtils;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import com.shulie.instrument.simulator.api.annotation.Destroyable;
import com.shulie.instrument.simulator.api.listener.ext.Advice;
import com.shulie.instrument.simulator.api.reflect.Reflect;
import com.shulie.instrument.simulator.api.reflect.ReflectException;
import com.shulie.instrument.simulator.api.util.StringUtil;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;


/**
 * @Author: guohz
 * @ClassName: BlockingQueueConsumerDeliveryInterceptorV2
 * @Package: com.pamirs.attach.plugin.rabbitmq.interceptor
 * @Date: 2019-07-25 14:33
 * @Description: 兼容低版本客户Jar包
 */
@Destroyable(RabbitmqDestroy.class)
public class SpringBlockingQueueConsumerDeliveryInterceptorV2 extends TraceInterceptorAdaptor {
    private final static Logger LOGGER = LoggerFactory.getLogger(SpringBlockingQueueConsumerDeliveryInterceptorV2.class.getName());

    private Class deliveryClass = null;
    private Field envelopeField = null;
    private Field bodyField = null;
    private Field propertiesField = null;

    @Override
    public void beforeFirst(Advice advice) {
        Object[] args = advice.getParameterArray();
        if (args == null || args[0] == null) {
            return;
        }
        initReflectionProps(advice.getTarget());
    }

    @Override
    public void beforeLast(Advice advice) {
        Object[] args = advice.getParameterArray();
        if (args == null || args[0] == null) {
            return;
        }
        initReflectionProps(advice.getTarget());
        Object deliveryObj = args[0];
        Envelope envelope = Reflect.on(deliveryObj).get(envelopeField);
        AMQP.BasicProperties properties = Reflect.on(deliveryObj).get(propertiesField);
        validatePressureMeasurement(envelope, properties);
    }

    @Override
    public SpanRecord beforeTrace(Advice advice) {
        Object[] args = advice.getParameterArray();
        if (args == null || args[0] == null) {
            return null;
        }

        initReflectionProps(advice.getTarget());
        Object deliveryObj = args[0];

        SpanRecord record = new SpanRecord();
        try {
            Envelope envelope = Reflect.on(deliveryObj).get(envelopeField);
            record.setService(envelope.getExchange());
            record.setMethod(envelope.getRoutingKey());
        } catch (ReflectException e) {
        }
        try {
            AMQP.BasicProperties properties = Reflect.on(deliveryObj).get(propertiesField);
            final Map<String, Object> headers = properties.getHeaders();
            if (headers != null) {
                Map<String, String> rpcContext = new HashMap<String, String>();
                for (String key : Pradar.getInvokeContextTransformKeys()) {
                    String value = (String) headers.get(key);
                    if (!StringUtil.isEmpty(value)) {
                        rpcContext.put(key, value);
                    }
                }
                record.setContext(rpcContext);
            }

        } catch (ReflectException e) {
        }
        try {
            byte[] body = Reflect.on(deliveryObj).get(bodyField);
            record.setRequestSize(body.length);
            record.setRequest(body);
        } catch (ReflectException e) {
        }

        return record;
    }


    @Override
    public SpanRecord afterTrace(Advice advice) {
        Object[] args = advice.getParameterArray();
        if (args == null || args[0] == null) {
            return null;
        }
        SpanRecord record = new SpanRecord();
        return record;
    }

    @Override
    public SpanRecord exceptionTrace(Advice advice) {
        Object[] args = advice.getParameterArray();
        if (args == null || args[0] == null) {
            return null;
        }
        SpanRecord record = new SpanRecord();
        record.setResultCode(ResultCode.INVOKE_RESULT_FAILED);
        record.setResponse(advice.getThrowable());
        return record;
    }

    void initReflectionProps(Object target) {
        Class deliveryClass = initDeliveryClass(target);

        initEnvelopeField(deliveryClass);
        initBodyField(deliveryClass);
        initPropertiesField(deliveryClass);
    }

    void initPropertiesField(Class deliveryClass) {
        if (propertiesField != null) {
            return;
        }
        try {
            propertiesField = deliveryClass.getDeclaredField("properties");
            propertiesField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ReflectException(e);
        }
    }

    private void initBodyField(Class deliveryClass) {
        if (propertiesField != null) {
            return;
        }
        try {
            bodyField = deliveryClass.getDeclaredField("body");
            bodyField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ReflectException(e);
        }
    }

    private void initEnvelopeField(Class deliveryClass) {
        if (propertiesField != null) {
            return;
        }
        try {
            envelopeField = deliveryClass.getDeclaredField("envelope");
            envelopeField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ReflectException(e);
        }
    }

    private Class initDeliveryClass(Object target) {
        if (deliveryClass != null) {
            return deliveryClass;
        }

        Class<?>[] classes = target.getClass().getDeclaredClasses();
        Class deliveryClass = null;
        for (Class classfile : classes) {
            if (classfile.getName().equalsIgnoreCase("org.springframework.amqp.rabbit.listener.BlockingQueueConsumer$Delivery")) {
                deliveryClass = classfile;
            }
        }
        return deliveryClass;
    }

    private void validatePressureMeasurement(Envelope envelope, AMQP.BasicProperties properties) {
        try {
            Pradar.setClusterTest(false);
            if (envelope == null) {
                return;
            }

            String exchange = envelope.getExchange();
            exchange = StringUtils.trimToEmpty(exchange);
            String routingKey = envelope.getRoutingKey();
            if (exchange != null
                    && Pradar.isClusterTestPrefix(exchange)) {
                Pradar.setClusterTest(true);
            } else if (PradarSwitcher.isRabbitmqRoutingkeyEnabled()
                    && routingKey != null
                    && Pradar.isClusterTestPrefix(routingKey)) {
                Pradar.setClusterTest(true);
            } else if (null != properties.getHeaders() && ClusterTestUtils.isClusterTestRequest(ObjectUtils.toString(properties.getHeaders().get(PradarService.PRADAR_CLUSTER_TEST_KEY)))) {
                Pradar.setClusterTest(true);
            }
        } catch (Throwable e) {
            LOGGER.error("", e);
            if (Pradar.isClusterTest()) {
                throw new PressureMeasureError(e);
            }
        }
    }

    @Override
    public String getPluginName() {
        return RabbitmqConstants.PLUGIN_NAME;
    }

    @Override
    public int getPluginType() {
        return RabbitmqConstants.PLUGIN_TYPE;
    }
}
