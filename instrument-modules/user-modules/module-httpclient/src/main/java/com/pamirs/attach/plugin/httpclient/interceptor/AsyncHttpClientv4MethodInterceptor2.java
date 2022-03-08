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
package com.pamirs.attach.plugin.httpclient.interceptor;

import com.alibaba.fastjson.JSONObject;
import com.pamirs.attach.plugin.httpclient.HttpClientConstants;
import com.pamirs.pradar.Pradar;
import com.pamirs.pradar.PradarService;
import com.pamirs.pradar.ResultCode;
import com.pamirs.pradar.common.HeaderMark;
import com.pamirs.pradar.interceptor.AroundInterceptor;
import com.pamirs.pradar.internal.adapter.ExecutionStrategy;
import com.pamirs.pradar.internal.config.ExecutionCall;
import com.pamirs.pradar.internal.config.MatchConfig;
import com.pamirs.pradar.pressurement.ClusterTestUtils;
import com.pamirs.pradar.pressurement.mock.JsonMockStrategy;
import com.pamirs.pradar.utils.InnerWhiteListCheckUtil;
import com.shulie.instrument.simulator.api.ProcessControlException;
import com.shulie.instrument.simulator.api.ProcessController;
import com.shulie.instrument.simulator.api.listener.ext.Advice;
import org.apache.commons.lang.StringUtils;
import org.apache.http.*;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;

import java.net.SocketTimeoutException;
import java.util.Map;

/**
 * Created by xiaobin on 2016/12/15.
 */
public class AsyncHttpClientv4MethodInterceptor2 extends AroundInterceptor {

    private static String getService(String schema, String host, int port, String path) {
        String url = schema + "://" + host;
        if (port != -1 && port != 80) {
            url = url + ':' + port;
        }
        return url + path;
    }
    private static ExecutionStrategy fixJsonStrategy =
            new JsonMockStrategy() {
                @Override
                public Object processBlock(Class returnType, ClassLoader classLoader, Object params) throws ProcessControlException {

                    MatchConfig config = (MatchConfig) params;
                    if (config.getScriptContent().contains("return")) {
                        return null;
                    }
                    if (null == config.getArgs().get("futureCallback")){
                        return null;
                    }
                    //现在先暂时注释掉因为只有jdk8以上才能用
                    FutureCallback<HttpResponse> futureCallback = (FutureCallback<HttpResponse>) config.getArgs().get("futureCallback");
                    StatusLine statusline = new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "");
                    try {
                        HttpEntity entity = null;
                        entity = new StringEntity(config.getScriptContent());

                        BasicHttpResponse response = new BasicHttpResponse(statusline);
                        response.setEntity(entity);
                        java.util.concurrent.CompletableFuture future = new java.util.concurrent.CompletableFuture();
                        future.complete(response);
                        futureCallback.completed(response);
                        ProcessController.returnImmediately(returnType, future);
                    } catch (ProcessControlException pe) {
                        throw pe;
                    } catch (Exception e) {
                    }
                    return null;
                }
            };

    @Override
    public void doBefore(final Advice advice) throws ProcessControlException {
        Object[] args = advice.getParameterArray();
        HttpAsyncRequestProducer httpAsyncRequestProducer = (HttpAsyncRequestProducer) args[0];

        HttpHost httpHost = httpAsyncRequestProducer.getTarget();
        HttpRequest request = null;
        try {
            request = httpAsyncRequestProducer.generateRequest();
        } catch (Throwable e) {
            LOGGER.error("AsyncHttpClient org.apache.http.impl.nio.client.CloseableHttpAsyncClient.execute(org.apache.http.nio.protocol.HttpAsyncRequestProducer, org.apache.http.nio.protocol.HttpAsyncResponseConsumer<T>, org.apache.http.concurrent.FutureCallback<T>) generateRequest error. ignore it", e);
        }

        if (httpHost == null) {
            return;
        }
        InnerWhiteListCheckUtil.check();
        String host = httpHost.getHostName();
        int port = httpHost.getPort();
        String path = httpHost.getHostName();
        String reqStr = request.toString();
        String method = StringUtils.upperCase(reqStr.substring(0, reqStr.indexOf(" ")));
        if (request instanceof HttpUriRequest) {
            path = ((HttpUriRequest) request).getURI().getPath();
            method = ((HttpUriRequest) request).getMethod();
        }

        //判断是否在白名单中
        String url = getService(httpHost.getSchemeName(), host, port, path);
        final MatchConfig config = ClusterTestUtils.httpClusterTest(url);
        Header[] wHeaders = request.getHeaders(PradarService.PRADAR_WHITE_LIST_CHECK);
        if (wHeaders != null && wHeaders.length > 0) {
            config.addArgs(PradarService.PRADAR_WHITE_LIST_CHECK, wHeaders[0].getValue());
        }
        config.addArgs("url", url);

        config.addArgs("request", request);
        config.addArgs("method", "uri");
        config.addArgs("isInterface", Boolean.FALSE);
        if (args.length == 3){
            config.addArgs("futureCallback", args[2]);
        }
        if (config.getStrategy() instanceof JsonMockStrategy){
            fixJsonStrategy.processBlock(advice.getBehavior().getReturnType(), advice.getClassLoader(), config);
        }
        config.getStrategy().processBlock(advice.getBehavior().getReturnType(), advice.getClassLoader(), config, new ExecutionCall() {
            @Override
            public Object call(Object param) {
                if (null == config.getArgs().get("futureCallback")){
                    return null;
                }
                FutureCallback<HttpResponse> futureCallback = (FutureCallback<HttpResponse>) config.getArgs().get("futureCallback");
                StatusLine statusline = new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "");
                try {
                    HttpEntity entity = null;
                    if (param instanceof String) {
                        entity = new StringEntity(String.valueOf(param));
                    } else {
                        entity = new ByteArrayEntity(JSONObject.toJSONBytes(param));
                    }
                    BasicHttpResponse response = new BasicHttpResponse(statusline);
                    response.setEntity(entity);
                    futureCallback.completed(response);
                    java.util.concurrent.CompletableFuture future = new java.util.concurrent.CompletableFuture();
                    future.complete(response);
                    return future;
                } catch (Exception e) {
                }
                return null;
            }
        });

        Pradar.startClientInvoke(path, method);
        Pradar.remoteIp(host);
        Pradar.remotePort(port);
        Pradar.middlewareName(HttpClientConstants.HTTP_CLIENT_NAME_4X);
        Header[] headers = request.getHeaders("content-length");
        if (headers != null && headers.length != 0) {
            try {
                Header header = headers[0];
                Pradar.requestSize(Integer.valueOf(header.getValue()));
            } catch (NumberFormatException e) {
            }
        }
        final Map<String, String> context = Pradar.getInvokeContextMap();
        for (Map.Entry<String, String> entry : context.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (request.getHeaders(HeaderMark.DONT_MODIFY_HEADER) == null ||
                    request.getHeaders(HeaderMark.DONT_MODIFY_HEADER).length == 0) {
                request.setHeader(key, value);
            }
        }
        Pradar.popInvokeContext();

        final Object future = args[args.length - 1];
        if (!(future instanceof FutureCallback)) {
            return;
        }
        final HttpRequest finalRequest = request;
        advice.changeParameter(args.length - 1, new FutureCallback() {
            @Override
            public void completed(Object result) {
                Pradar.setInvokeContext(context);
                ((FutureCallback)future).completed(result);
                try {
                    if (result instanceof HttpResponse) {
                        afterTrace(finalRequest, (HttpResponse) result);
                    } else {
                        afterTrace(finalRequest, null);
                    }
                } catch (Throwable e) {
                    LOGGER.error("AsyncHttpClient execute future endTrace error.", e);
                    Pradar.endClientInvoke("200", HttpClientConstants.PLUGIN_TYPE);
                }
            }

            @Override
            public void failed(Exception ex) {
                Pradar.setInvokeContext(context);
                ((FutureCallback)future).failed(ex);
                try {
                    exceptionTrace(finalRequest, ex);
                } catch (Throwable e) {
                    LOGGER.error("AsyncHttpClient execute future endTrace error.", e);
                    Pradar.endClientInvoke("200", HttpClientConstants.PLUGIN_TYPE);
                }
            }

            @Override
            public void cancelled() {
                Pradar.setInvokeContext(context);
                ((FutureCallback)future).cancelled();
                try {
                    exceptionTrace(finalRequest, null);
                } catch (Throwable e) {
                    LOGGER.error("AsyncHttpClient execute future endTrace error.", e);
                    Pradar.endClientInvoke("200", HttpClientConstants.PLUGIN_TYPE);
                }
            }
        });

    }

    public void afterTrace(HttpRequest request, HttpResponse response) {
        try {
            Pradar.responseSize(response == null ? 0 : response.getEntity().getContentLength());
        } catch (Throwable e) {
            Pradar.responseSize(0);
        }
        Pradar.request(request.getParams());
        InnerWhiteListCheckUtil.check();
        int code = response == null ? 200 : response.getStatusLine().getStatusCode();
        Pradar.endClientInvoke(code + "", HttpClientConstants.PLUGIN_TYPE);

    }


    public void exceptionTrace(HttpRequest request, Throwable throwable) {
        Pradar.request(request.getParams());
        Pradar.response(throwable);
        InnerWhiteListCheckUtil.check();
        if (throwable != null && (throwable instanceof SocketTimeoutException)) {
            Pradar.endClientInvoke(ResultCode.INVOKE_RESULT_TIMEOUT, HttpClientConstants.PLUGIN_TYPE);
        } else {
            Pradar.endClientInvoke(ResultCode.INVOKE_RESULT_FAILED, HttpClientConstants.PLUGIN_TYPE);
        }
    }

}
