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
package com.shulie.instrument.simulator.agent.spi;

import com.shulie.instrument.simulator.agent.spi.command.Command;
import com.shulie.instrument.simulator.agent.spi.model.CommandExecuteResponse;

/**
 * 命令执行器
 *
 * @author xiaobin.zfb|xiaobin@shulie.io
 * @since 2020/11/17 7:57 下午
 */
public interface CommandExecutor{

    /**
     * 执行指定的命令，支持 Start/Stop
     *
     * @param command 命令
     */
    CommandExecuteResponse execute(Command command) throws Throwable;

    /**
     * 是否已经安装
     *
     * @return
     */
    boolean isInstalled();

    /**
     * 模块是否已经安装
     *
     * @param moduleId 模块 ID
     * @return
     */
    boolean isModuleInstalled(String moduleId);
}
