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
package com.shulie.instrument.module.register;

import com.pamirs.pradar.AppNameUtils;
import com.pamirs.pradar.JvmUtils;
import com.shulie.instrument.module.register.register.Register;
import com.shulie.instrument.module.register.register.RegisterFactory;
import com.shulie.instrument.module.register.register.RegisterOptions;
import com.shulie.instrument.module.register.utils.SimulatorStatus;
import com.shulie.instrument.module.register.zk.impl.NetflixCuratorZkClientFactory;
import com.shulie.instrument.simulator.api.CommandResponse;
import com.shulie.instrument.simulator.api.ExtensionModule;
import com.shulie.instrument.simulator.api.ModuleInfo;
import com.shulie.instrument.simulator.api.ModuleLifecycleAdapter;
import com.shulie.instrument.simulator.api.annotation.Command;
import com.shulie.instrument.simulator.api.executors.ExecutorServiceFactory;
import com.shulie.instrument.simulator.api.guard.SimulatorGuard;
import com.shulie.instrument.simulator.api.resource.ModuleLoadInfoManager;
import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author xiaobin.zfb|xiaobin@shulie.io
 * @since 2020/10/1 1:19 上午
 */
@MetaInfServices(ExtensionModule.class)
@ModuleInfo(id = "pradar-register", version = "1.0.0", author = "xiaobin@shulie.io", description = "节点注册，负责将当前探针信息注册到注册中心上")
public class NodeRegisterModule extends ModuleLifecycleAdapter implements ExtensionModule {
    private final static Logger logger = LoggerFactory.getLogger(NodeRegisterModule.class);

    private Register register;
    private volatile boolean isActive;


    @Command("getSimulatorStatus")
    public CommandResponse info(Map<String, String> args) {
        CommandResponse<String> commandResponse = new CommandResponse();
        commandResponse.setSuccess(true);
        commandResponse.setResult(SimulatorStatus.getStatus());
        commandResponse.setMessage(SimulatorStatus.getErrorMsg());
        return commandResponse;
    }

    @Resource
    public static ModuleLoadInfoManager moduleLoadInfoManager;

    @Override
    public boolean onActive() throws Throwable {
        isActive = true;
        /**
         * 激活时初始化
         */
        JvmUtils.writePidFile();
        ExecutorServiceFactory.getFactory().schedule(new Runnable() {
            @Override
            public void run() {
                if (!isActive) {
                    return;
                }
                try {
                    RegisterOptions registerOptions = buildRegisterOptions();
                    register = SimulatorGuard.getInstance().doGuard(Register.class, RegisterFactory.getRegister(registerOptions.getRegisterName()));
                    register.init(registerOptions);
                    register.start();
                    if (logger.isInfoEnabled()) {
                        logger.info("SIMULATOR: Register start successful. register to {}", register.getPath());
                    }
                } catch (Throwable e) {
                    logger.warn("SIMULATOR: Register start failed. ", e);
                    ExecutorServiceFactory.getFactory().schedule(this, 5, TimeUnit.SECONDS);
                }
            }
        }, 0, TimeUnit.SECONDS);
        return true;
    }

    private RegisterOptions buildRegisterOptions() {
        RegisterOptions registerOptions = new RegisterOptions();
        registerOptions.setMd5(simulatorConfig.getSimulatorMd5());
        registerOptions.setSimulatorConfig(simulatorConfig);
        registerOptions.setAppName(AppNameUtils.appName());
        registerOptions.setRegisterBasePath(simulatorConfig.getHeartbeatPath());
        registerOptions.setRegisterName("zookeeper");
        registerOptions.setZkServers(simulatorConfig.getZkServers());
        registerOptions.setConnectionTimeoutMillis(simulatorConfig.getZkConnectionTimeout());
        registerOptions.setSessionTimeoutMillis(simulatorConfig.getZkSessionTimeout());
        return registerOptions;
    }

    @Override
    public void onFrozen() throws Throwable {
        isActive = false;
        if (register != null) {
            try {
                register.stop();
            } catch (Throwable e) {
                logger.error("[register] Register stop failed.");
            }
        }
    }

    @Override
    public void onUnload() throws Throwable {
        RegisterFactory.release();
        NetflixCuratorZkClientFactory.release();
    }
}
