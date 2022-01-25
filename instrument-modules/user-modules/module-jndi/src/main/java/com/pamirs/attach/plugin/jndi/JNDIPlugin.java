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
package com.pamirs.attach.plugin.jndi;

import com.pamirs.attach.plugin.jndi.interceptor.InitialContextLookupInterceptor;
import com.pamirs.pradar.interceptor.Interceptors;
import com.shulie.instrument.simulator.api.ExtensionModule;
import com.shulie.instrument.simulator.api.ModuleInfo;
import com.shulie.instrument.simulator.api.ModuleLifecycleAdapter;
import com.shulie.instrument.simulator.api.instrument.EnhanceCallback;
import com.shulie.instrument.simulator.api.instrument.InstrumentClass;
import com.shulie.instrument.simulator.api.instrument.InstrumentMethod;
import com.shulie.instrument.simulator.api.listener.Listeners;
import com.shulie.instrument.simulator.api.scope.ExecutionPolicy;
import org.kohsuke.MetaInfServices;

/**
 * @author liqiyu@shulie.io
 */
@MetaInfServices(ExtensionModule.class)
@ModuleInfo(id = JNDIConstans.MODULE_NAME, version = "1.0.0", author = "liqiyu@shulie.io",
    description = "jndi datasource")
public class JNDIPlugin extends ModuleLifecycleAdapter implements ExtensionModule {

    @Override
    public boolean onActive() throws Throwable {

        this.enhanceTemplate.enhance(this, "javax.naming.InitialContext", new EnhanceCallback() {
            @Override
            public void doEnhance(InstrumentClass target) {
                final InstrumentMethod lookupMethod = target.getDeclaredMethod("lookup", "java.lang.String");
                lookupMethod.addInterceptor(
                    Listeners.of(InitialContextLookupInterceptor.class, "JNDI_SCOPE", ExecutionPolicy.BOUNDARY,
                        Interceptors.SCOPE_CALLBACK));

                final InstrumentMethod lookupMethod0 = target.getDeclaredMethod("lookup", "javax.naming.Name");
                lookupMethod0.addInterceptor(
                    Listeners.of(InitialContextLookupInterceptor.class, "JNDI_SCOPE", ExecutionPolicy.BOUNDARY,
                        Interceptors.SCOPE_CALLBACK));

            }
        });

        return true;
    }

}
