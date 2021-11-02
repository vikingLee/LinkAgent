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
package com.pamirs.attach.plugin.lettuce.interceptor;

import com.pamirs.attach.plugin.lettuce.utils.LettuceUtils;
import com.pamirs.pradar.interceptor.AroundInterceptor;
import com.shulie.instrument.simulator.api.listener.ext.Advice;
import io.lettuce.core.RedisURI;
import io.lettuce.core.models.role.RedisNodeDescription;

import java.util.List;

/**
 * @Auther: vernon
 * @Date: 2021/10/19 20:52
 * @Description:
 */
public class SentinelConnectorInterceptor extends AroundInterceptor {
    @Override
    public void doBefore(Advice advice) throws Throwable {
        Object[] arrs = advice.getParameterArray();
        try {
            List<RedisNodeDescription> lists = (List<RedisNodeDescription>) arrs[4];
            for (RedisNodeDescription nodeDescription : lists) {
                RedisURI uri = nodeDescription.getUri();
                LettuceUtils.cacheMasterSlave(uri);
            }
        } catch (Throwable t) {

        }
    }
}
