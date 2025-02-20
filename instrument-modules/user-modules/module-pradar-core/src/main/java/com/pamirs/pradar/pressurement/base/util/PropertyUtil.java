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
package com.pamirs.pradar.pressurement.base.util;

import java.io.File;

import com.pamirs.pradar.AppNameUtils;
import com.pamirs.pradar.Pradar;

public class PropertyUtil {

    private static final String DEFAULT = "default";

    public static String appName() {
        String appName = System.getProperty("simulator.app.name", "default");
        if (DEFAULT.equals(appName)) {
            appName = System.getProperty("app.name", "default");
        }
        return appName;
    }

    public static String getAppPort() {
        String appName = System.getProperty("server.port", "default");
        if (DEFAULT.equals(appName)) {
            appName = System.getProperty("pradar.project.port", "default");
        }
        return appName;
    }

    public static String getTroControlWebUrl() {
        String troControlWebUrl = System.getenv("tro.web.url");
        if (troControlWebUrl == null || troControlWebUrl.isEmpty()) {
            troControlWebUrl = System.getProperty("tro.web.url", "default");
        }
        return troControlWebUrl;
    }

    public static String getEsBlockListFilePath() {
        String str = System.getProperty("simulator.home");
        return str + File.separator + AppNameUtils.appName() + File.separator + "es_white_list";
    }

    public static String getAgentManagerUrl(){
        String url = System.getenv("shulie.agent.manager.url");
        if (url == null || url.isEmpty()) {
            url = System.getProperty("shulie.agent.manager.url", null);
        }
        return url;
    }

    public static String getAgentManagerTenantCode(){
        String code = System.getenv("shulie.agent.tenant.code");
        if (code == null || code.isEmpty()) {
            code = System.getProperty("shulie.agent.tenant.code", null);
        }
        return code;
    }

    public static boolean isShadowPreparationEnabled(){
        String enabled = System.getenv("shadow.preparation.enabled");
        if (enabled == null || enabled.isEmpty()) {
            enabled = System.getProperty("shadow.preparation.enabled", "");
        }
        return "true".equals(enabled);
    }
}
