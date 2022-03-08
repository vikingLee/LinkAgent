/**
 * Copyright 2021 Shulie Technology, Co.Ltd
 * Email: shulie@shulie.io
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pamirs.pradar;

import com.alibaba.fastjson.JSON;
import com.pamirs.attach.plugin.dynamic.Attachment;
import com.pamirs.attach.plugin.dynamic.Converter;
import com.pamirs.attach.plugin.dynamic.ResourceManager;
import com.pamirs.pradar.json.ResultSerializer;
import com.shulie.instrument.simulator.api.util.StringUtil;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;

/**
 * @Auther: vernon
 * @Date: 2021/1/18 10:28
 * @Description:
 */

/**
 * traceId|startTime|agentId|invokeId|invokeType|appName|cost|middlewareName|serviceName|methodName|resultCode
 * |request|response|flags|callbackMsg|#samplingInterval|@attributes|@localAttributes
 */
public abstract class TraceEncoder {
    public abstract void encode(BaseContext ctx, PradarAppender appender) throws IOException;
}

/**
 * Pradar RPC 日志的输出
 */
class TraceInvokeContextEncoder extends TraceEncoder {

    private int DEFAULT_BUFFER_SIZE = 256;
    private StringBuilder buffer = new StringBuilder(DEFAULT_BUFFER_SIZE);

    @Override
    public void encode(BaseContext base, PradarAppender eea) throws IOException {
        AbstractContext ctx;
        if (base instanceof AbstractContext) {
            ctx = (AbstractContext) base;
        } else {
            return;
        }
        attachment(ctx);
        //Pradar TODO
        StringBuilder buffer = this.buffer;
        buffer.delete(0, buffer.length());
        buffer.append(ctx.getTraceId() == null ? "" : ctx.getTraceId()).append('|')
                .append(ctx.getStartTime()).append('|');
        // 新版本兼容老版本的控制台和大数据
        if (StringUtils.isNotBlank(Pradar.PRADAR_ENV_CODE)) {
            buffer.append(StringUtils.isBlank(Pradar.PRADAR_TENANT_KEY) ? "" : Pradar.PRADAR_TENANT_KEY).append('|')
                    .append(StringUtils.isBlank(Pradar.PRADAR_ENV_CODE) ? "" : Pradar.PRADAR_ENV_CODE).append('|')
                    .append(StringUtils.isBlank(Pradar.PRADAR_USER_ID) ? "" : Pradar.PRADAR_USER_ID).append('|');
        }
        buffer.append(Pradar.AGENT_ID_NOT_CONTAIN_USER_INFO).append('|')
                .append(ctx.getInvokeId() == null ? "" : ctx.getInvokeId()).append('|')
                .append(ctx.getInvokeType()).append('|')
                .append(PradarCoreUtils.makeLogSafe(AppNameUtils.appName())).append('|')
                .append(ctx.getLogTime() - ctx.getStartTime()).append('|')
                .append(PradarCoreUtils.makeLogSafe(ctx.getMiddlewareName() == null ? "" : ctx.getMiddlewareName())).append(
                '|')
                .append(PradarCoreUtils.makeLogSafe(ctx.getServiceName() == null ? "" : ctx.getServiceName())).append('|')
                .append(PradarCoreUtils.makeLogSafe(ctx.getMethodName() == null ? "" : ctx.getMethodName())).append('|')
                .append(ctx.getResultCode() == null ? "" : ctx.getResultCode()).append('|')
                .append(PradarCoreUtils.makeLogSafe(
                        ResultSerializer.serializeRequest(ctx.getRequest() == null ? "" : ctx.getRequest(),
                                Pradar.getPluginRequestSize()))).append('|')
                .append(PradarCoreUtils.makeLogSafe(
                        ResultSerializer.serializeRequest(ctx.getResponse() == null ? "" : ctx.getResponse(),
                                Pradar.getPluginRequestSize()))).append('|')
                .append(TraceCoreUtils.combineString(ctx.isClusterTest(), ctx.isDebug(),
                        "0".equals(ctx.invokeId),
                        TraceCoreUtils.isServer(ctx)))
                .append("|")
                .append(PradarCoreUtils.makeLogSafe(ctx.getCallBackMsg() == null ? "" : ctx.getCallBackMsg()));
        int samplingInterval;
        if (ctx.isClusterTest()){
            samplingInterval = PradarSwitcher.getClusterTestSamplingInterval();
        } else {
            samplingInterval = PradarSwitcher.getSamplingInterval();
        }
        buffer.append("|#").append(samplingInterval);
        buffer.append("|@").append(TraceCoreUtils.attributes(ctx.traceAppName, ctx.traceServiceName, ctx.traceMethod))
                .append("|@")
                .append(TraceCoreUtils.localAttributes(
                        ctx.upAppName, ctx.remoteIp, ctx.getPort(), ctx.requestSize, ctx.responseSize))
                .append("|")
                .append(ctx.ext == null ? "" : ctx.ext);
        ctx.logContextData(buffer);
        buffer.append(PradarCoreUtils.NEWLINE);
        eea.append(buffer.toString());
        ctx.destroy();
    }

    void attachment(AbstractContext ctx) {
        if (ctx.isClusterTest) {
            return;
        }
        try {
            if (ctx.ext != null) {
                Object t = ctx.ext;
                if (Attachment.class.isAssignableFrom(t.getClass())) {
                    Attachment attachment = (Attachment) t;
                    Object key = Converter.TemplateConverter
                            .ofClass(
                                    attachment.getExt().getClass()
                            ).getKey();
                    ctx.ext = key + "@##" + JSON.toJSONString(attachment);
                }
                return;
            }

            String middleware = PradarCoreUtils.makeLogSafe(ctx.getMiddlewareName()
                    == null
                    ? "" : ctx.getMiddlewareName());
            if (StringUtil.isEmpty(middleware)) {
                return;
            }

            Object t = ResourceManager.get(ctx.index, middleware);
            if (t == null) {
                String serviceName = PradarCoreUtils.makeLogSafe(
                        ctx.getServiceName() == null ? "" : ctx.getServiceName());

                t = ResourceManager.get(serviceName, middleware);

                if (t == null) {
                    String methodName = PradarCoreUtils.makeLogSafe(
                            ctx.getMethodName() == null ? "" : ctx.getMethodName());

                    t = ResourceManager.get(serviceName + methodName, middleware);
                    if (t == null) {
                        return;
                    }
                }
            }
            ctx.ext = t;
            if (ctx.ext instanceof Attachment) {
                Attachment attachment = (Attachment) ctx.ext;
                Object key = Converter.TemplateConverter
                        .ofClass(
                                attachment.getExt().getClass()
                        ).getKey();
                ctx.ext = key + "@##" + JSON.toJSONString(ctx.ext);
            }
        } catch (Throwable t) {

        }

    }
}

/**
 * 业务跟踪日志的输出
 */
class TraceTraceEncoder extends TraceEncoder {

    private static final int DEFAULT_BUFFER_SIZE = 256;

    /**
     * 需要做换行和分隔符过滤
     */
    static final int REQUIRED_LINE_FEED_ESCAPE = 1;

    private final char entryDelimiter;
    private StringBuilder buffer = new StringBuilder(DEFAULT_BUFFER_SIZE);

    TraceTraceEncoder(char entryDelimiter) {
        this.entryDelimiter = entryDelimiter;
    }

    @Override
    public void encode(BaseContext ctx, PradarAppender eea) throws IOException {
        final char entryDelimiter = this.entryDelimiter;
        StringBuilder buffer = this.buffer;
        buffer.delete(0, buffer.length());
        buffer.append(ctx.getTraceId()).append(entryDelimiter)// traceId
                .append(ctx.getTraceAppName()).append(entryDelimiter)
                .append(ctx.getUpAppName()).append(entryDelimiter)
                .append(ctx.getLogTime()).append(entryDelimiter)
                .append(ctx.getInvokeId()).append(entryDelimiter)// rpcId
                .append(ctx.getServiceName()).append(entryDelimiter)// bizKey
                .append(ctx.getMethodName()).append(entryDelimiter)// queryKey
                .append(ctx.getLogType()).append(entryDelimiter)// clusterTest
                .append(ctx.traceName).append(entryDelimiter)// bizType and bizValue
                .append(ctx.isClusterTest() ? '1' : '0').append(entryDelimiter);// bizType and bizValue

        // logContent
        if (ctx.getInvokeType() == REQUIRED_LINE_FEED_ESCAPE) {
            PradarCoreUtils.appendLog(ctx.callBackMsg, buffer, '\0');
        } else {
            buffer.append(ctx.callBackMsg);
        }
        buffer.append(PradarCoreUtils.NEWLINE);
        eea.append(buffer.toString());
    }
}
