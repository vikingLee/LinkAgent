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
package com.shulie.instrument.simulator.api;

import com.shulie.instrument.simulator.api.instrument.EnhanceTemplate;
import com.shulie.instrument.simulator.api.resource.ModuleController;
import com.shulie.instrument.simulator.api.resource.ModuleEventWatcher;
import com.shulie.instrument.simulator.api.resource.ReleaseResource;
import com.shulie.instrument.simulator.api.resource.SimulatorConfig;

import javax.annotation.Resource;

/**
 * 模块生命周期适配器，用于简化接口实现
 *
 * @author xiaobin.zfb|xiaobin@shulie.io
 * @since 2020/10/23 10:45 下午
 */
public class ModuleLifecycleAdapter implements ModuleLifecycle {
    @Resource
    protected SimulatorConfig simulatorConfig;
    @Resource
    protected ModuleEventWatcher moduleEventWatcher;
    @Resource
    protected EnhanceTemplate enhanceTemplate;
    @Resource
    protected ModuleController moduleController;

    protected String moduleName;

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public void addReleaseResource(ReleaseResource releaseResource) {
        this.moduleController.addReleaseResource(releaseResource);
    }

    /**
     * 插件的初始化逻辑会先调用{@link #onLoad()}
     * 后调用{@link #onActive()}，最后再调用
     * {@link #loadCompleted()}
     * 在执行 {@link #onLoad()}时模块的状态还是未加载状态,只有执行完成
     * {@link #onLoad()} 模块状态才会标记为已加载状态
     * 在执行完成 {@link #onActive()} 之后才会激活所有注册的拦截器，并且将模块注册到框架中，此时
     * 模块才是真正加载完成，最后再调用 {@link #loadCompleted()}
     *
     * @throws Throwable
     */
    @Override
    public void onLoad() throws Throwable {
    }

    /**
     * 此时可能处理逻辑还会使用到本插件中的一些类会导致在清理资源时加载不到类
     * 需要注意的是：清理资源时调用的类需要避免调用到的类依赖业务类的情况，因为
     * 依赖的业务类是通过拦截器运行中捕获到的业务类加载器，在执行插件卸载时是无法引用到
     * 业务类加载器的，所以这种情况会导致类加载不到异常
     * {@link #onFrozen()} 和 {@link #onUnload()} ()} 都可以用来做资源清理，
     * 在卸载时会先执行 onFrozen，然后再执行 onUnload
     * 这两个方法都会在插件卸载之前执行
     *
     * @throws Throwable
     */
    @Override
    public void onUnload() throws Throwable {

    }

    /**
     * 插件的初始化逻辑会先调用{@link #onLoad()}
     * 后调用{@link #onActive()}，最后再调用
     * {@link #loadCompleted()}
     * 在执行 {@link #onLoad()}时模块的状态还是未加载状态,只有执行完成
     * {@link #onLoad()} 模块状态才会标记为已加载状态
     * 在执行完成 {@link #onActive()} 之后才会激活所有注册的拦截器，并且将模块注册到框架中，此时
     * 模块才是真正加载完成，最后再调用 {@link #loadCompleted()}
     *
     * @throws Throwable
     */
    @Override
    public boolean onActive() throws Throwable {
        return false;
    }

    /**
     * 此时可能处理逻辑还会使用到本插件中的一些类会导致在清理资源时加载不到类
     * 需要注意的是：清理资源时调用的类需要避免调用到的类依赖业务类的情况，因为
     * 依赖的业务类是通过拦截器运行中捕获到的业务类加载器，在执行插件卸载时是无法引用到
     * 业务类加载器的，所以这种情况会导致类加载不到异常
     * {@link #onFrozen()} 和 {@link #onUnload()} ()} 都可以用来做资源清理，
     * 在卸载时会先执行 onFrozen，然后再执行 onUnload
     * 这两个方法都会在插件卸载之前执行
     *
     * @throws Throwable
     */
    @Override
    public void onFrozen() throws Throwable {
    }

    @Override
    public void loadCompleted() {
    }

    @Override
    public void executeCommand(){

    }

}
