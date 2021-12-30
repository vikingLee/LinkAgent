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

import com.pamirs.attach.plugin.common.datasource.redisserver.RedisClientMediator;
import com.pamirs.attach.plugin.lettuce.LettuceConstants;
import com.pamirs.attach.plugin.lettuce.destroy.LettuceDestroy;
import com.pamirs.pradar.Pradar;
import com.pamirs.pradar.interceptor.ParametersWrapperInterceptorAdaptor;
import com.pamirs.pradar.pressurement.ClusterTestUtils;
import com.pamirs.pradar.pressurement.agent.shared.service.GlobalConfig;
import com.shulie.instrument.simulator.api.annotation.Destroyable;
import com.shulie.instrument.simulator.api.listener.ext.Advice;
import io.lettuce.core.MigrateArgs;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 第一个参数是 K类型，第二参数或者第三个参数是 K 数组类型
 */
@Destroyable(LettuceDestroy.class)
public class LettuceMethodMigrateInterceptor extends ParametersWrapperInterceptorAdaptor {

    private volatile Field keysField;

    private void initKeysField() {
        if (keysField != null) {
            return;
        }
        try {
            keysField = MigrateArgs.class.getDeclaredField(LettuceConstants.REFLECT_FIELD_KEYS);
            keysField.setAccessible(true);
        } catch (Throwable e) {
        }
    }

    private List getKeys(Object target) {
        if (keysField == null) {
            return Collections.EMPTY_LIST;
        }
        try {
            return (List) keysField.get(target);
        } catch (Throwable e) {
            return Collections.EMPTY_LIST;
        }
    }

    private void setKeys(Object target, List keys) {
        if (keysField == null) {
            return;
        }
        try {
            keysField.set(target, keys);
        } catch (Throwable e) {
        }
    }


    @Override
    protected Object[] getParameter0(Advice advice) {
        Object[] args = advice.getParameterArray();
        ClusterTestUtils.validateClusterTest();
        if (!Pradar.isClusterTest()) {
            return args;
        }

        /*if (RedisClientMediator.isShadowDb()) {
            return args;
        }*/

        if (args == null || args.length != 5) {
            return args;
        }

        initKeysField();
        if (keysField == null) {
            LOGGER.warn("SIMULATOR: io.lettuce.core.MigrateArgs can't found field " + LettuceConstants.REFLECT_FIELD_KEYS);
            return args;
        }
        MigrateArgs migrateArgs = (MigrateArgs) args[4];
        List keys = getKeys(migrateArgs);
        if (keys == null) {
            return args;
        }
        List newKeys = new ArrayList(keys.size());
        for (Object key : keys) {
            newKeys.add(toClusterTestKey(key));
        }
        setKeys(migrateArgs, newKeys);

        return args;
    }

    /**
     * 此处留一个扩展，因为有方法数组是key,value,key,value...这种组合
     *
     * @param object
     * @param index
     * @return
     */
    public boolean isKeyArrayIndex(Object object, int index) {
        return true;
    }

    private Object toClusterTestKey(Object key) {
        if (key == null) {
            return null;
        }
        if (key instanceof byte[]) {
            return getBytes((byte[]) key);
        }

        if (key instanceof byte[][]) {
            return getBytesArray((byte[][]) key);
        }

        if (key instanceof char[]) {
            return getChars((char[]) key);
        }

        if (key instanceof char[][]) {
            return getCharsArray((char[][]) key);
        }

        if (key instanceof String) {
            return getString((String) key);
        }

        if (key instanceof List) {
            List list = new ArrayList();
            for (Object k : (List) key) {
                list.add(toClusterTestKey(k));
            }
            return list;
        }

        if (key instanceof Set) {
            Set set = new HashSet();
            for (Object k : (Set) key) {
                set.add(toClusterTestKey(k));
            }
            return set;
        }

        if (key instanceof Iterable) {
            List list = new ArrayList();
            Iterator it = ((Iterable) key).iterator();
            while (it.hasNext()) {
                Object k = it.next();
                list.add(toClusterTestKey(k));
            }
            return list;
        }

        if (key instanceof Map) {
            Map map = (Map) key;
            Map result = new HashMap();

            final Set<Map.Entry<Object, Object>> set = map.entrySet();
            for (Map.Entry<Object, Object> entry : set) {
                Object k = entry.getKey();
                result.put(toClusterTestKey(k), entry.getValue());
            }
            return result;
        }

        if (key instanceof Iterator) {
            List list = new ArrayList();
            Iterator it = (Iterator) key;
            while (it.hasNext()) {
                Object k = it.next();
                list.add(toClusterTestKey(k));
            }
            return list;
        }

        if (key instanceof Object[]) {
            Object[] keys = (Object[]) key;
            for (int i = 0, len = keys.length; i < len; i++) {
                boolean isKeyArrayIndex = isKeyArrayIndex(keys[i], i);
                if (isKeyArrayIndex) {
                    Object k = toClusterTestKey(keys[i]);
                    keys[i] = k;
                }
            }
            return keys;
        }

        String str = key.toString();
        Collection<String> whiteList = GlobalConfig.getInstance().getCacheKeyWhiteList();
        if (ignore(whiteList, str)) {
            return str;
        }
        if (!Pradar.isClusterTestPrefix(str)) {
            str = Pradar.addClusterTestPrefix(str);
        }
        return str;

    }

    private String getString(String key) {
        Collection<String> whiteList = GlobalConfig.getInstance().getCacheKeyWhiteList();
        if (ignore(whiteList, key)) {
            return key;
        }
        String str = key;
        if (!Pradar.isClusterTestPrefix(str)) {
            str = Pradar.addClusterTestPrefix(str);
        }
        return str;
    }

    private char[][] getCharsArray(char[][] key) {
        char[][] datas = key;
        for (int i = 0, len = datas.length; i < len; i++) {
            String str = new String(datas[i]);
            Collection<String> whiteList = GlobalConfig.getInstance().getCacheKeyWhiteList();
            if (ignore(whiteList, str)) {
                continue;
            }
            if (!Pradar.isClusterTestPrefix(str)) {
                str = Pradar.addClusterTestPrefix(str);
            }
            datas[i] = str.toCharArray();
        }
        return datas;
    }

    private char[] getChars(char[] key) {
        String str = new String(key);
        Collection<String> whiteList = GlobalConfig.getInstance().getCacheKeyWhiteList();
        if (ignore(whiteList, str)) {
            return key;
        }
        if (!Pradar.isClusterTestPrefix(str)) {
            str = Pradar.addClusterTestPrefix(str);
        }
        return str.toCharArray();
    }

    private byte[][] getBytesArray(byte[][] key) {
        Collection<String> whiteList = GlobalConfig.getInstance().getCacheKeyWhiteList();
        byte[][] datas = key;
        for (int i = 0, len = datas.length; i < len; i++) {
            String str = new String(datas[i]);
            if (ignore(whiteList, str)) {
                continue;
            }
            if (!Pradar.isClusterTestPrefix(str)) {
                str = Pradar.addClusterTestPrefix(str);
            }
            datas[i] = str.getBytes();
        }
        return datas;
    }

    private boolean ignore(Collection<String> whiteList, String key) {
        //白名单 忽略
        for (String white : whiteList) {
            if (key.startsWith(white)) {
                return true;
            }
        }
        return false;
    }

    private byte[] getBytes(byte[] key) {
        String str = new String(key);

        Collection<String> whiteList = GlobalConfig.getInstance().getCacheKeyWhiteList();
        if (ignore(whiteList, str)) {
            return key;
        }

        if (!Pradar.isClusterTestPrefix(str)) {
            str = Pradar.addClusterTestPrefix(str);
        }
        return str.getBytes();
    }
}
