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
package com.pamirs.attach.plugin.alibaba.druid.obj;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import com.pamirs.attach.plugin.alibaba.druid.util.DruidDatasourceUtils;
import com.pamirs.attach.plugin.common.datasource.WrappedDbMediatorDataSource;
import com.pamirs.pradar.ErrorTypeEnum;
import com.pamirs.pradar.Pradar;
import com.pamirs.pradar.exception.PressureMeasureError;
import com.pamirs.pradar.pressurement.agent.shared.service.ErrorReporter;
import com.pamirs.pradar.pressurement.agent.shared.service.GlobalConfig;
import com.shulie.druid.util.JdbcUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DbDruidMediatorDataSource extends WrappedDbMediatorDataSource<DruidDataSource> implements DataSource {
    private final static Logger LOGGER = LoggerFactory.getLogger(DbDruidMediatorDataSource.class.getName());

    @Override
    public String getUsername(DruidDataSource datasource) {
        return datasource.getUsername();
    }

    @Override
    public String getUrl(DruidDataSource datasource) {
        return datasource.getUrl();
    }

    @Override
    public String getDriverClassName(DruidDataSource datasource) {
        return datasource.getDriverClassName();
    }

    @Override
    protected String getMidType() {
        return "druid";
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (Pradar.isClusterTest()) {
            try {
                init();
                if (useTable) {
                    //影子表
                    if (dataSourceBusiness == null) {
                        throw new PressureMeasureError("Business dataSource is null.");
                    }
                    DruidPooledConnection druidPooledConnection = dataSourceBusiness.getConnection();
                    return new DruidPooledNormalConnection(druidPooledConnection,
                            dbConnectionKey, url, username, this.dbType, getMidType(), sqlMetaData);
                } else {
                    //影子库
                    if (dataSourcePerformanceTest == null) {
                        synchronized (this) {
                            if (dataSourcePerformanceTest == null) {
                                dataSourcePerformanceTest = DruidDatasourceUtils.generateDatasourceFromConfiguration(
                                        dataSourceBusiness, GlobalConfig.getInstance().getShadowDatasourceConfigs());
                            }
                        }
                        if (dataSourcePerformanceTest == null) {
                            throw new PressureMeasureError(
                                    "Performance dataSource is null. can't get shadow datasource config with business "
                                            + "config. bizUrl="
                                            + dataSourceBusiness.getUrl() + ", bizUser=" + dataSourceBusiness.getUsername());
                        }
                    }
                    DruidPooledConnection druidPooledConnection = dataSourcePerformanceTest.getConnection();
                    return new DruidPooledPressureConnection(druidPooledConnection,
                            dbConnectionKey,
                            dataSourcePerformanceTest.getUrl(), dataSourcePerformanceTest.getUsername(), this.dbType, sqlMetaData);
                }
            } catch (Throwable e) {
                ErrorReporter.Error error = ErrorReporter.buildError()
                        .setErrorType(ErrorTypeEnum.DataSource)
                        .setErrorCode("datasource-0001")
                        .setMessage("数据源获取链接失败！" + ((Pradar.isClusterTest() ? "(压测流量)" : "") + ", url="
                                + (dataSourceBusiness == null ? null : dataSourceBusiness.getUrl())
                                + ", username=" + (dataSourceBusiness == null ? null : dataSourceBusiness.getUsername())))
                        .setDetail("get connection failed by dbMediatorDataSource, url="
                                + (dataSourceBusiness == null ? null : dataSourceBusiness.getUrl()) +
                                ", username=" + (dataSourceBusiness == null ? null : dataSourceBusiness.getUsername())
                                + "message: " + e.getMessage() + "\r\n" + printStackTrace(e));
                //                error.closePradar(ConfigNames.SHADOW_DATABASE_CONFIGS);
                error.report();
                throw new PressureMeasureError("get connection failed by dbMediatorDataSource. url="
                        + (dataSourceBusiness == null ? null : dataSourceBusiness.getUrl())
                        + ", username=" + (dataSourceBusiness == null ? null : dataSourceBusiness.getUsername()), e);
            }
        } else {
            initBiz();
            final DruidPooledConnection connection = dataSourceBusiness.getConnection();
            return new BizConnection(connection, dataSourceBusiness.getUrl(), dataSourceBusiness.getUsername(), bizDbType, null);
        }
    }

    private void initBiz() throws SQLException {
        if (StringUtils.isBlank(bizDbType)) {
            String driverClassName = getDriverClassName(dataSourceBusiness);
            if (driverClassName == null) {
                driverClassName = JdbcUtils.getDriverClassName(dataSourceBusiness.getUrl());
            }
            //cobar用的mysql协议
            String url = getUrl(dataSourceBusiness);
            if (url.startsWith("jdbc:cobar_cluster")) {
                this.bizDbType = "mysql";
            } else {
                this.bizDbType = JdbcUtils.getDbType(dataSourceBusiness.getUrl(), driverClassName);
            }
        }
    }

    @Override
    public void close() {
        if (dataSourcePerformanceTest != null) {
            try {
                dataSourcePerformanceTest.close();
            } catch (Throwable e) {
                LOGGER.error("[druid] close performance test datasource err!", e);
            }
        }
    }

}
