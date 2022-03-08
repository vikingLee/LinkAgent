/*
 * *
 *  * Copyright 2021 Shulie Technology, Co.Ltd
 *  * Email: shulie@shulie.io
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.shulie.instrument.simulator.agent.spi.command.impl;

import com.shulie.instrument.simulator.agent.spi.command.Command;

/**
 * @author angju
 * @date 2021/11/18 17:35
 */
public class HeartCommand<T> implements Command<T> {
    private final T packet;

    public HeartCommand(T packet) {
        this.packet = packet;
    }

    @Override
    public T getPacket() {
        return packet;
    }
}
