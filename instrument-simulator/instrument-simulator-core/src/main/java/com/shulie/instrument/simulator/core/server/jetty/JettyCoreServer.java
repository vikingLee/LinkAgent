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
package com.shulie.instrument.simulator.core.server.jetty;

import com.shulie.instrument.simulator.api.LoadMode;
import com.shulie.instrument.simulator.api.spi.DeploymentManager;
import com.shulie.instrument.simulator.core.CoreConfigure;
import com.shulie.instrument.simulator.core.Simulator;
import com.shulie.instrument.simulator.core.manager.impl.DefaultDeploymentManager;
import com.shulie.instrument.simulator.core.server.CoreServer;
import com.shulie.instrument.simulator.core.server.jetty.servlet.ModuleHttpServlet;
import com.shulie.instrument.simulator.core.util.Initializer;
import com.shulie.instrument.simulator.core.util.LogbackUtils;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.shulie.instrument.simulator.core.util.NetworkUtils.isPortInUsing;
import static java.lang.String.format;
import static org.eclipse.jetty.servlet.ServletContextHandler.NO_SESSIONS;

/**
 * Jetty实现的Http服务器
 */
public class JettyCoreServer implements CoreServer {

    private static volatile CoreServer coreServer;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private Initializer initializer;

    private Server httpServer;
    private CoreConfigure config;
    private Simulator simulator;
    private ModuleHttpServlet moduleHttpServlet;
    // is destroyed already
    private AtomicBoolean isDestroyed = new AtomicBoolean(false);

    public JettyCoreServer() {
        initializer = new Initializer(true);
    }

    /**
     * 单例
     *
     * @return CoreServer单例
     */
    public static CoreServer getInstance() {
        if (null == coreServer) {
            synchronized (CoreServer.class) {
                if (null == coreServer) {
                    coreServer = new JettyCoreServer();
                }
            }
        }
        return coreServer;
    }

    @Override
    public boolean isBind() {
        return initializer.isInitialized();
    }

    @Override
    public void unbind() throws IOException {
        try {

            initializer.destroyProcess(new Initializer.Processor() {
                @Override
                public void process() throws Throwable {

                    if (null != httpServer) {

                        // stop http server
                        if (logger.isInfoEnabled()) {
                            logger.info("SIMULATOR: {} is stopping", JettyCoreServer.this);
                        }
                        httpServer.stop();
                        if (logger.isInfoEnabled()) {
                            logger.info("SIMULATOR: {} is stopped", JettyCoreServer.this);
                        }
                    }

                }
            });

            // destroy http server
            if (logger.isInfoEnabled()) {
                logger.info("SIMULATOR: {} is destroying", this);
            }
            while (!httpServer.isStopped()) {
            }
            httpServer.destroy();
            if (logger.isInfoEnabled()) {
                logger.info("SIMULATOR: {} is destroyed", this);
            }

        } catch (Throwable cause) {
            logger.warn("SIMULATOR: {} unBind failed.", this, cause);
            throw new IOException("unBind failed.", cause);
        }
    }

    @Override
    public InetSocketAddress getLocal() throws IOException {
        if (!isBind()
                || null == httpServer) {
            throw new IOException("server was not bind yet.");
        }

        SelectChannelConnector scc = null;
        final Connector[] connectorArray = httpServer.getConnectors();
        if (null != connectorArray) {
            for (final Connector connector : connectorArray) {
                if (connector instanceof SelectChannelConnector) {
                    scc = (SelectChannelConnector) connector;
                    break;
                }
            }
        }

        if (null == scc) {
            throw new IllegalStateException("not found SelectChannelConnector");
        }

        return new InetSocketAddress(
                scc.getHost(),
                scc.getLocalPort()
        );
    }

    /**
     * 初始化Jetty's ContextHandler
     */
    private void initHttpContextHandler() {
        final ServletContextHandler context = new ServletContextHandler(NO_SESSIONS);

        final String contextPath = "/simulator";
        context.setContextPath(contextPath);
        context.setClassLoader(getClass().getClassLoader());

        // module-http-servlet
        final String pathSpec = "/*";
        if (logger.isInfoEnabled()) {
            logger.info("SIMULATOR: initializing http-handler. path={}", contextPath + "/*");
        }
        DeploymentManager deploymentManager = new DefaultDeploymentManager(config.getAgentLauncherClass());
        this.moduleHttpServlet = new ModuleHttpServlet(config, simulator.getCoreModuleManager(), deploymentManager);
        context.addServlet(
                new ServletHolder(this.moduleHttpServlet),
                pathSpec
        );

        httpServer.setHandler(context);
    }

    private void initHttpServer() {


        final String serverIp = config.getServerIp();
        final int serverPort = config.getServerPort();

        // 防止端口可重用导致端口占用, server 还是能正常启动, 需要判断一下
        if (isPortInUsing(serverIp, serverPort)) {
            throw new IllegalStateException(format("address[%s:%s] already in using, server bind failed.",
                    serverIp,
                    serverPort
            ));
        }

        int maxThreads = Math.max(Runtime.getRuntime().availableProcessors(), 8);
        int minThreads = Math.max(maxThreads / 2, 1);
        QueuedThreadPool qtp = new QueuedThreadPool(maxThreads);
        qtp.setMinThreads(minThreads);
        /**
         * jetty线程设置为daemon，防止应用启动失败进程无法正常退出
         */
        qtp.setDaemon(true);
        qtp.setName("simulator-jetty-qtp-" + qtp.hashCode());
        httpServer = new Server(new InetSocketAddress(serverIp, serverPort));
        httpServer.setThreadPool(qtp);
    }

    @Override
    public synchronized void bind(final CoreConfigure config, final Instrumentation inst) throws IOException {
        this.config = config;
        try {
            initializer.initProcess(new Initializer.Processor() {
                @Override
                public void process() throws Throwable {
                    LogbackUtils.init(
                            config.getConfigLibPath() + File.separator + "simulator-logback.xml"
                    );
                    if (logger.isInfoEnabled()) {
                        logger.info("SIMULATOR: initializing server. config={}", config);
                    }
                    simulator = new Simulator(config, inst);
                    initHttpServer();
                    initHttpContextHandler();
                    httpServer.start();
                }
            });
            config.setSocketAddress(getLocal());
            simulator.getCoreModuleManager().reset();

            final InetSocketAddress local = getLocal();
            if (logger.isInfoEnabled()) {
                System.out.println(String.format("SIMULATOR: initialized server. actual bind to %s:%s", local.getHostName(), local.getPort()));
                logger.info("SIMULATOR: initialized server. actual bind to {}:{}",
                        local.getHostName(),
                        local.getPort()
                );
            }

        } catch (Throwable cause) {
            // 这里会抛出到目标应用层，所以在这里留下错误信息
            logger.error("SIMULATOR: initialize server failed.", cause);

            if (config.getLaunchMode() == LoadMode.ATTACH) {
                // 对外抛出到目标应用中
                throw new IOException("server bind failed.", cause);
            }
            //AGENT模式直接忽略，不让上层应用感知，只需要将日志打印出来即可
            return;
        }

        if (logger.isInfoEnabled()) {
            logger.info("SIMULATOR: {} bind success.", this);
        }
    }

    @Override
    public void destroy() {
        // avoid multi invoke
        if (!isDestroyed.compareAndSet(false, true)) {
            return;
        }
        /**
         * 先标记一下对外 http server 正在销毁中，防止请求时出现资源再一次加载
         */
        moduleHttpServlet.prepareDestroy();
        // 关闭JVM-SIMULATOR
        if (null != simulator) {
            simulator.destroy();
        }

        // 关闭HTTP服务器
        if (isBind()) {
            try {
                unbind();
            } catch (IOException e) {
                logger.warn("SIMULATOR: {} unBind failed when destroy.", this, e);
            }
        }

        // 关闭LOGBACK
        LogbackUtils.destroy();
        this.httpServer = null;
        this.config = null;
        this.simulator = null;
        this.moduleHttpServlet = null;
        coreServer = null;
    }

    @Override
    public String toString() {
        return format("server[%s:%s]", config.getServerIp(), config.getServerPort());
    }
}
