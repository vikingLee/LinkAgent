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

package com.shulie.instrument.module.config.fetcher.config;

/**
 * @author angju
 * @date 2021/12/6 15:40
 */
public class SimulatorDetail {
    /**
     * 是否静默
     */
    private int isSilent;

    public int getIsSilent() {
        return isSilent;
    }

    public void setIsSilent(int isSilent) {
        this.isSilent = isSilent;
    }
}
