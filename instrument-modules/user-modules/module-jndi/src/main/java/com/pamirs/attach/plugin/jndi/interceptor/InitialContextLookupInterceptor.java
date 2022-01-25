package com.pamirs.attach.plugin.jndi.interceptor;

import java.util.Arrays;
import java.util.Map.Entry;

import javax.naming.Context;
import javax.sql.DataSource;

import com.pamirs.attach.plugin.common.datasource.WrappedDbMediatorDataSource;
import com.pamirs.pradar.interceptor.ResultInterceptorAdaptor;
import com.pamirs.pradar.internal.config.ShadowDatabaseConfig;
import com.pamirs.pradar.pressurement.agent.shared.service.GlobalConfig;
import com.pamirs.pradar.pressurement.datasource.DbMediatorDataSource;
import com.shulie.druid.util.StringUtils;
import com.shulie.instrument.simulator.api.listener.ext.Advice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Description
 * @Author xiaobin.zfb
 * @mail xiaobin@shulie.io
 * @Date 2020/7/29 7:38 下午
 */
public class InitialContextLookupInterceptor extends ResultInterceptorAdaptor {
    private final static Logger LOGGER = LoggerFactory.getLogger(InitialContextLookupInterceptor.class.getName());

    public DataSource initDataSource(DataSource dataSource, DataSource dataSourcePerformanceTest,
        final String jndiName) {
        WrappedDbMediatorDataSource dbMediatorDataSource = new WrappedDbMediatorDataSource() {

            @Override
            public String getUsername(DataSource datasource) {
                return null;
            }

            @Override
            public String getUrl(DataSource datasource) {
                return null;
            }

            @Override
            public String getDriverClassName(DataSource datasource) {
                return null;
            }

            @Override
            protected String getJndiName() {
                return jndiName;
            }

            @Override
            protected boolean isJndi() {
                return true;
            }
        };
        dbMediatorDataSource.setDataSourceBusiness(dataSource);
        dbMediatorDataSource.setDataSourcePerformanceTest(dataSourcePerformanceTest);
        return dbMediatorDataSource;
    }

    @Override
    protected Object getResult0(Advice advice) {
        final Object result = advice.getReturnObj();
        final Object[] args = advice.getParameterArray();
        /**
         * 如果不是数据源则直接略过
         */
        if (!(result instanceof DataSource)) {
            return result;
        }
        /**
         * 如果是已经包装过了，则不需要再进行包装了
         */
        if (result instanceof DbMediatorDataSource) {
            return result;
        }

        String jndiName = null;
        try {
            /**
             * 需要兼容两种参数
             */

            if (args[0] instanceof String) {
                jndiName = (String)args[0];
            } else {
                jndiName = args[0] == null ? null : args[0].toString();
            }

            String pressureJndiName = getPressureJndiName(jndiName);
            if (StringUtils.isEmpty(pressureJndiName)) {
                LOGGER.error(String.format("[JNDI]ptjndi name is null!,jndiName is [%s],shadowDatabaseConfig:key [%s],value: [%s]", jndiName,
                    Arrays.toString(GlobalConfig.getInstance().getShadowDatasourceConfigs().keySet().toArray()),
                    Arrays.toString(GlobalConfig.getInstance().getShadowDatasourceConfigs().values().toArray())));
                return initDataSource((DataSource)result, null, jndiName);
            }
            Context context = (Context)advice.getTarget();
            DataSource ptDataSource = getPressureJndiDataSource(context, pressureJndiName);

            if (ptDataSource != null) {
                // 获取Jndi DataSource名称
                DataSource bsDataSource = (DataSource)result;
                return initDataSource(bsDataSource, ptDataSource, jndiName);
            } else {
                LOGGER.error("Pradar pressurement Datasource is not found:{}", pressureJndiName);
            }
        } catch (Throwable e) {
            LOGGER.error("init Pradar pressurement Datasource err!", e);
        }
        return initDataSource((DataSource)result, null, jndiName);
    }

    /**
     * process jndiName, try to find shadowDatasource config.
     */
    private String getPressureJndiName(String jndiName) {
        for (Entry<String, ShadowDatabaseConfig> entity : GlobalConfig.getInstance()
            .getShadowDatasourceConfigs().entrySet()) {
            // trunc qqq|"", only left jndiName, drop the userName.
            final String key = entity.getKey().split("\\|")[0];
            // first try to find whole jndiName
            if (key.equals(jndiName)) {
                return entity.getValue().getShadowUrl();
            }
            // then find if jndiName contains : , like jndi:xxx、JNDI:xxx
            if (jndiName.contains(":")) {
                final String[] jndiNameSplit = jndiName.split(":");
                // if not stand jndi name, do not handle
                if (jndiNameSplit.length != 2) {
                    continue;
                }
                if (key.equals(jndiNameSplit[1])) {
                    return entity.getValue().getShadowUrl();
                }
                if(key.contains(":")){
                    final String[] keySplit = key.split(":");
                    if(keySplit.length != 2) {
                        continue;
                    }
                    if(keySplit[1].equals(jndiNameSplit[1])){
                        return entity.getValue().getShadowUrl();
                    }
                }
            }
            // second find if key contains : , like jndi:xxx、JNDI:xxx
            if(key.contains(":")){
                final String[] keySplit = key.split(":");
                if(keySplit.length != 2) {
                    continue;
                }
                if(keySplit[1].equals(jndiName)){
                    return entity.getValue().getShadowUrl();
                }
                if (jndiName.contains(":")) {
                    final String[] jndiNameSplit = jndiName.split(":");
                    // if not stand jndi name, do not handle
                    if (jndiNameSplit.length != 2) {
                        continue;
                    }
                    if (keySplit[1].equals(jndiNameSplit[1])) {
                        return entity.getValue().getShadowUrl();
                    }
                }
            }
        }
        // find nothing, return null.
        return null;
    }

    /**
     * process shadow url , try to find pressure jndi Datasource.
     */
    private DataSource getPressureJndiDataSource(Context context, String pressureJndiName) {
        Object lookup = null;
        try {
            lookup = context.lookup(pressureJndiName);
        } catch (Throwable e) { /* do nothing */ }

        if (lookup == null) {
            if (pressureJndiName.contains(":")) {
                final String[] split = pressureJndiName.split(":");
                if (split.length == 2) {
                    try {
                        lookup = context.lookup(split[1]);
                    } catch (Throwable e) { /* do nothing */}
                }
            }
        }
        return (DataSource)lookup;
    }

}
