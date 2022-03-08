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
package com.shulie.instrument.module.register.register.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.pamirs.pradar.*;
import com.pamirs.pradar.common.HttpUtils;
import com.pamirs.pradar.common.HttpUtils.HttpResult;
import com.pamirs.pradar.common.IOUtils;
import com.pamirs.pradar.common.RuntimeUtils;
import com.pamirs.pradar.event.ErrorEvent;
import com.pamirs.pradar.event.Event;
import com.pamirs.pradar.event.PradarSwitchEvent;
import com.pamirs.pradar.exception.PradarException;
import com.pamirs.pradar.pressurement.base.util.PropertyUtil;
import com.shulie.instrument.module.register.NodeRegisterModule;
import com.shulie.instrument.module.register.register.Register;
import com.shulie.instrument.module.register.register.RegisterOptions;
import com.shulie.instrument.module.register.utils.ConfigUtils;
import com.shulie.instrument.module.register.utils.SimulatorStatus;
import com.shulie.instrument.module.register.zk.ZkClient;
import com.shulie.instrument.module.register.zk.ZkHeartbeatNode;
import com.shulie.instrument.module.register.zk.ZkNodeStat;
import com.shulie.instrument.module.register.zk.impl.NetflixCuratorZkClientFactory;
import com.shulie.instrument.module.register.zk.impl.ZkClientSpec;
import com.shulie.instrument.simulator.api.executors.ExecutorServiceFactory;
import com.shulie.instrument.simulator.api.obj.ModuleLoadInfo;
import com.shulie.instrument.simulator.api.obj.ModuleLoadStatusEnum;
import com.shulie.instrument.simulator.api.resource.SimulatorConfig;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @Description
 * @Author xiaobin.zfb
 * @mail xiaobin@shulie.io
 * @Date 2020/8/20 9:55 上午
 */
public class ZookeeperRegister implements Register {

    private final static Logger LOGGER = LoggerFactory.getLogger(ZookeeperRegister.class.getName());
    private String basePath;
    private String appName;
    private String heartbeatPath;
    private ZkClient zkClient;
    private ZkHeartbeatNode heartbeatNode;
    private String md5;
    private SimulatorConfig simulatorConfig;
    private Set<String> jars;
    private AtomicBoolean isStarted = new AtomicBoolean(false);
    private final RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
    private ScheduledFuture scanJarFuture;
    private ScheduledFuture syncStatusFuture;

    private byte[] getHeartbeatDatas() {
        Map<String, String> map = new HashMap<String, String>();
        map.put("address", PradarCoreUtils.getLocalAddress());
        map.put("host", PradarCoreUtils.getHostName());
        map.put("name", RuntimeUtils.getName());
        map.put("pid", String.valueOf(RuntimeUtils.getPid()));
        map.put("agentId", Pradar.AGENT_ID_NOT_CONTAIN_USER_INFO);
        map.put("agentVersion", simulatorConfig.getAgentVersion());
        map.put("simulatorVersion", simulatorConfig.getSimulatorVersion());
        map.put("md5", md5);
        //服务的 url
        String serviceUrl = "http://" + simulatorConfig.getServerAddress().getAddress().getHostAddress() + ":"
            + simulatorConfig.getServerAddress().getPort()
            + "/simulator";
        map.put("service", serviceUrl);
        map.put("port", String.valueOf(simulatorConfig.getServerAddress().getPort()));
        map.put("status", String.valueOf(PradarSwitcher.isClusterTestEnabled()));
        if (PradarSwitcher.isClusterTestEnabled()) {
            map.put("errorCode", "");
            map.put("errorMsg", "");
        } else {
            map.put("errorCode", StringUtils.defaultIfBlank(PradarSwitcher.getErrorCode(), ""));
            map.put("errorMsg", StringUtils.defaultIfBlank(PradarSwitcher.getErrorMsg(), ""));
        }
        map.put("agentLanguage", "JAVA");
        map.put("userId", Pradar.PRADAR_USER_ID);
        map.put("jars", toJarFileString(jars));
        map.put("simulatorFileConfigs", JSON.toJSONString(simulatorConfig.getSimulatorFileConfigs()));
        map.put("agentFileConfigs", JSON.toJSONString(simulatorConfig.getAgentFileConfigs()));

        if (!SimulatorStatus.statusCalculated()){
            boolean moduleLoadResult = getModuleLoadResult();
            if (!moduleLoadResult){
                SimulatorStatus.installFailed(JSON.toJSONString(NodeRegisterModule.moduleLoadInfoManager.getModuleLoadInfos().values()));
            } else {
                SimulatorStatus.installed();
            }
        }
        map.put("agentStatus", SimulatorStatus.getStatus());
        if (SimulatorStatus.isInstallFailed()){
            map.put("errorMsg", "模块加载异常，请查看模块加载详情");
        }
        // 放入当前环境及用户信息
        map.put("tenantAppKey", Pradar.PRADAR_TENANT_KEY);
        map.put("envCode", Pradar.PRADAR_ENV_CODE);
        map.put("moduleLoadResult", String.valueOf(getModuleLoadResult()));
        map.put("moduleLoadDetail",
            JSON.toJSONString(NodeRegisterModule.moduleLoadInfoManager.getModuleLoadInfos().values()));
        //参数比较
        map.put("simulatorFileConfigsCheck", JSON.toJSONString(checkConfigs()));
        String str = JSON.toJSONString(map);
        try {
            return str.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            return str.getBytes();
        }
    }

    /**
     * 动态参数不需要校验参数是否生效，
     */
    private static final List<String> excludeCheckConfig = new ArrayList<String>(6);

    static {
        excludeCheckConfig.add("pradar.trace.log.version");
        excludeCheckConfig.add("pradar.monitor.log.version");
        excludeCheckConfig.add("pradar.error.log.version");
        excludeCheckConfig.add("is.kafka.message.headers");
        excludeCheckConfig.add("trace.samplingInterval");
        excludeCheckConfig.add("trace.mustsamplingInterval");
        excludeCheckConfig.add("pradar.sampling.interval");
    }

    /**
     * 校验配置文件参数
     *
     * @return
     */
    private Map<String, String> checkConfigs() {
        Map<String, String> result = new HashMap<String, String>(32, 1);
        Map<String, String> allConfigs = new HashMap<String, String>(
            simulatorConfig.getSimulatorFileConfigs().size() + simulatorConfig.getAgentFileConfigs().size(), 1);
        allConfigs.putAll(simulatorConfig.getSimulatorFileConfigs());
        allConfigs.putAll(simulatorConfig.getAgentFileConfigs());
        Map<String, Object> simulatorConfigFromUrl =
            ConfigUtils.getFixedSimulatorConfigFromUrl(PropertyUtil.getTroControlWebUrl(), simulatorConfig.getAppName(),
                simulatorConfig.getAgentVersion());
        if (simulatorConfigFromUrl == null || simulatorConfigFromUrl.get("success") == null || !Boolean.parseBoolean(
            simulatorConfigFromUrl.get("success").toString())) {
            result.put("status", "false");
            result.put("errorMsg", "获取控制台配置信息失败,检查接口服务是否正常");
            return result;
        }
        boolean status = true;
        JSONObject jsonObject = (JSONObject)simulatorConfigFromUrl.get("data");
        StringBuilder unEqualConfigs = new StringBuilder();
        for (Map.Entry<String, String> entry : allConfigs.entrySet()) {
            if (excludeCheckConfig.contains(entry.getKey())) {
                continue;
            }
            String value = (String)jsonObject.get(entry.getKey());
            if (entry.getValue().equals(value)) {
                result.put(entry.getKey(), "true");
            } else {
                status = false;
                result.put(entry.getKey(), "false");
                unEqualConfigs.append("参数key:").append(entry.getKey()).append(",").append("本地参数值:").append(
                        entry.getValue())
                    .append(",").append("远程参数值:").append(value).append(",");
            }
        }
        result.put("status", String.valueOf(result));
        if (!status) {
            result.put("errorMsg", unEqualConfigs.toString());
        }
        return result;

    }

    private boolean getModuleLoadResult() {
        for (Map.Entry<String, ModuleLoadInfo> entry : NodeRegisterModule.moduleLoadInfoManager.getModuleLoadInfos()
            .entrySet()) {
            if (entry.getValue().getStatus() == ModuleLoadStatusEnum.LOAD_FAILED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getName() {
        return "zookeeper";
    }

    private String toJarFileString(Set<String> jars) {
        if (jars == null || jars.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String jar : jars) {
            builder.append(jar).append(';');
        }
        return builder.toString();
    }

    /**
     * 清除过期的节点,防止 zookeeper 低版本时有版本不致的 bug 导致过期的心跳节点删除不掉的问题
     *
     * @param path
     */
    private void cleanExpiredNodes(String path) {
        try {
            List<String> children = this.zkClient.listChildren(path);
            if (children != null) {
                for (String node : children) {
                    ZkNodeStat stat = this.zkClient.getStat(path + '/' + node);
                    if (stat == null) {
                        continue;
                    }
                    if (stat.getEphemeralOwner() == 0) {
                        zkClient.deleteQuietly(path + '/' + node);
                    }
                }
            }
        } catch (Throwable e) {
            LOGGER.warn("clean expired register node error.", e);
        }
    }

    private Set<String> loadAllJars() {

        String classPath = runtimeMXBean.getClassPath();
        String[] files = StringUtils.split(classPath, File.pathSeparator);
        if (files == null || files.length == 0) {
            return Collections.EMPTY_SET;
        }
        Set<String> list = new HashSet<String>();
        String javaHome = System.getProperty("java.home");
        String simulatorHome = simulatorConfig.getSimulatorHome();
        String tmpDir = System.getProperty("java.io.tmpdir");
        for (String file : files) {
            /**
             * 如果是 jdk 的 jar 包，过滤掉
             */
            if (StringUtils.isNotBlank(javaHome) && StringUtils.startsWith(file, javaHome)) {
                continue;
            }
            /**
             * 如果是仿真器的 jar 包，过滤掉
             */
            if (StringUtils.startsWith(file, simulatorHome)) {
                continue;
            }
            /**
             * 如果是监时目录加载的 jar 包，则过滤掉, simulator 所有的扩展 jar 包
             * 都会从临时目录加载
             */
            if (StringUtils.isNotBlank(tmpDir) && StringUtils.startsWith(file, tmpDir)) {
                continue;
            }

            /**
             * 如果 jar包是这一些打头的，也过滤掉
             */
            if (StringUtils.startsWith(file, "pradar-")
                || StringUtils.startsWith(file, "simulator-")
                || StringUtils.startsWith(file, "module-")) {
                continue;
            }

            /**
             * 如果有依赖包则不添加自身作为依赖。
             */
            if (processSpringBootProject(list, file)) {continue;}

            list.add(file);
        }
        return list;
    }

    private boolean processSpringBootProject(Set<String> list, String file) {
        boolean hasDependencies = false;
        if (file.endsWith(".jar") || file.endsWith(".war")) {
            JarFile jarFile = null;
            try {
                jarFile = new JarFile(file);
                final Enumeration<JarEntry> entries = jarFile.entries();
                final String tempPath = System.getProperty("java.io.tmpdir");
                final String randomPath = UUID.randomUUID().toString().replaceAll("-", "");
                final String filePath = (tempPath.endsWith(File.separator) ? tempPath : tempPath + File.separator)
                    + randomPath;
                final File fileDir = new File(filePath);
                if (!fileDir.exists()) {
                    final boolean mkdirs = fileDir.mkdirs();
                    if (!mkdirs) {
                        LOGGER.error("中间件信息上报：创建临时目录失败。");
                        return false;
                    }
                    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                        @Override
                        public void run() {
                            for (File listFile : fileDir.listFiles()) {
                                listFile.delete();
                            }
                            fileDir.delete();
                        }
                    }));
                }
                while (entries.hasMoreElements()) {
                    final JarEntry jarEntry = entries.nextElement();
                    String jarPath = "";
                    if (jarEntry.getName().endsWith(".jar")) {
                        InputStream inputStream = null;
                        FileOutputStream fileOutputStream = null;
                        try {
                            inputStream = jarFile.getInputStream(jarEntry);
                            final String[] split = jarEntry.getName().split(File.separator);
                            jarPath = (filePath.endsWith(File.separator) ? filePath : filePath + File.separator)
                                + split[
                                split.length - 1];
                            fileOutputStream = new FileOutputStream(jarPath);
                            IOUtils.copy(inputStream, fileOutputStream);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            if (inputStream != null) {
                                try {
                                    inputStream.close();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            if (fileOutputStream != null) {
                                try {
                                    fileOutputStream.close();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        list.add(jarPath);
                        hasDependencies = true;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            } finally {
                if (jarFile != null) {
                    try {
                        jarFile.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return hasDependencies;
    }

    @Override
    public void init(RegisterOptions registerOptions) {
        if (registerOptions == null) {
            throw new NullPointerException("RegisterOptions is null");
        }
        this.basePath = registerOptions.getRegisterBasePath();
        this.appName = registerOptions.getAppName();
        this.md5 = registerOptions.getMd5();
        this.simulatorConfig = registerOptions.getSimulatorConfig();
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("[pradar-register] prepare to init register zookeeper node. {}",
                Pradar.AGENT_ID_CONTAIN_USER_INFO);
        }
        String registerBasePath = null;
        if (StringUtils.endsWith(basePath, "/")) {
            registerBasePath = this.basePath + appName;
        } else {
            registerBasePath = this.basePath + '/' + appName;
        }
        try {
            ZkClientSpec zkClientSpec = new ZkClientSpec();
            zkClientSpec.setZkServers(registerOptions.getZkServers());
            zkClientSpec.setConnectionTimeoutMillis(registerOptions.getConnectionTimeoutMillis());
            zkClientSpec.setSessionTimeoutMillis(registerOptions.getSessionTimeoutMillis());
            zkClientSpec.setThreadName("heartbeat");
            this.zkClient = NetflixCuratorZkClientFactory.getInstance().create(zkClientSpec);
        } catch (PradarException e) {
            LOGGER.error("[pradar-register] ZookeeperRegister init error.", e);
            throw e;
        } catch (Throwable e) {
            LOGGER.error("[pradar-register] ZookeeperRegister init error.", e);
            throw new PradarException(e);
        }
        String client = Pradar.AGENT_ID_CONTAIN_USER_INFO;
        try {
            this.zkClient.ensureDirectoryExists(registerBasePath);
        } catch (Throwable e) {
            LOGGER.error("[register] ensureDirectoryExists err:{}", registerBasePath, e);
        }
        this.heartbeatPath = registerBasePath + '/' + client;
        try {
            if (this.zkClient.exists(this.heartbeatPath)) {
                this.zkClient.deleteQuietly(this.heartbeatPath);
            }
        } catch (Throwable e) {
        }
        cleanExpiredNodes(registerBasePath);
        this.heartbeatNode = this.zkClient.createHeartbeatNode(this.heartbeatPath);
        PradarSwitcher.registerListener(new PradarSwitcher.PradarSwitcherListener() {
            @Override
            public void onListen(Event event) {
                if (event instanceof PradarSwitchEvent || event instanceof ErrorEvent) {
                    refresh();
                }
            }
        });
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("[pradar-register] init register zookeeper node successful. {}",
                Pradar.AGENT_ID_CONTAIN_USER_INFO);
        }
    }

    @Override
    public String getPath() {
        return heartbeatPath;
    }

    @Override
    public void start() {
        if (isStarted.get()) {
            return;
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("[pradar-register] prepare to register zookeeper node. {}", Pradar.AGENT_ID_CONTAIN_USER_INFO);
        }
        try {
            this.jars = loadAllJars();
            this.pushMiddlewareJarInfo();
            if (zkClient.exists(heartbeatPath)) {
                zkClient.deleteQuietly(heartbeatPath);
            }
            this.heartbeatNode.start();
            this.heartbeatNode.setData(getHeartbeatDatas());
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("[pradar-register] register zookeeper node successful. {}",
                    Pradar.AGENT_ID_CONTAIN_USER_INFO);
            }
        } catch (Throwable e) {
            LOGGER.error("[pradar-register] register node to zk for heartbeat node err: {}!", heartbeatPath, e);
        }
        //5秒扫描一次当前应用所有加载的jar列表
        scanJarFuture = ExecutorServiceFactory.getFactory().schedule(new Runnable() {
            @Override
            public void run() {
                if (!isStarted.get()) {
                    return;
                }
                /**
                 * 如果还未启动则下次再执行
                 */
                if (!heartbeatNode.isAlive() || !heartbeatNode.isRunning()) {
                    scanJarFuture = ExecutorServiceFactory.getFactory().schedule(this, 5, TimeUnit.SECONDS);
                    return;
                }

                try {
                    if (!zkClient.exists(heartbeatPath)) {
                        zkClient.ensureDirectoryExists(heartbeatPath);
                    }
                } catch (Throwable e) {
                    LOGGER.error("[pradar-register] zk ensureDirectoryExists err: {}!", heartbeatPath);
                }

                try {
                    heartbeatNode.setData(getHeartbeatDatas());
                } catch (Throwable e) {
                    LOGGER.error("[pradar-register] update heartbeat node agent data err: {}!", heartbeatPath, e);
                }
                scanJarFuture = ExecutorServiceFactory.getFactory().schedule(this, 10, TimeUnit.SECONDS);
            }
        }, 5, TimeUnit.SECONDS);
        isStarted.compareAndSet(false, true);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("[pradar-register] start to register zookeeper node successful. {}",
                Pradar.AGENT_ID_CONTAIN_USER_INFO);
        }
    }

    private static final String PUSH_MIDDLEWARE_URL = "/agent/push/application/middleware";

    private void pushMiddlewareJarInfo() {
        if (Pradar.isLite) {
            return;
        }

        String body = "";
        try {
            String troControlWebUrl = PropertyUtil.getTroControlWebUrl();
            LOGGER.info(String.format("中间件管理：jars：%s", jars));
            final Set<String> jarInfoSet = ScanJarPomUtils.scanByJarPaths(jars);
            final ArrayList<MiddlewareRequest> middlewareList = new ArrayList<MiddlewareRequest>();
            for (String jarInfoStr : jarInfoSet) {
                final String[] split = jarInfoStr.split(":");
                middlewareList.add(new MiddlewareRequest(split[1], split[0], split[2]));
            }
            final PushMiddlewareVO pushMiddlewareVO = new PushMiddlewareVO(AppNameUtils.appName(), middlewareList);
            body = JSON.toJSONString(pushMiddlewareVO);
            final HttpResult httpResult = HttpUtils.doPost(troControlWebUrl + PUSH_MIDDLEWARE_URL,
                body);
            if (httpResult.isSuccess()) {
                LOGGER.info(String.format("中间件信息上报成功,body:%s,返回结果：%s", body, httpResult.getResult()));
            } else {
                LOGGER.info(String.format("中间件信息上报失败,body:%s,失败信息：%s", body, httpResult.getResult()));
            }
        } catch (Exception e) {
            LOGGER.error(String.format("中间件信息上报异常。body:%s", body), e);
        }
    }

    public static boolean collectionEquals(Collection source, Collection target) {
        if (source == target) {
            return true;
        }
        if (source == null || target == null) {
            return false;
        }
        return source.equals(target);
    }

    @Override
    public void stop() {
        if (!isStarted.compareAndSet(true, false)) {
            return;
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("[pradar-register] prepare to stop register zookeeper node. {}",
                Pradar.AGENT_ID_CONTAIN_USER_INFO);
        }
        if (scanJarFuture != null && !scanJarFuture.isCancelled() && !scanJarFuture.isDone()) {
            scanJarFuture.cancel(true);
        }
        if (syncStatusFuture != null && !syncStatusFuture.isCancelled() && !syncStatusFuture.isDone()) {
            syncStatusFuture.cancel(true);
        }

        if (this.heartbeatNode != null) {
            try {
                if (this.heartbeatNode.isRunning()) {
                    this.heartbeatNode.stop();
                }
            } catch (Throwable e) {
                LOGGER.error("[pradar-register] unregister node to zk for heartbeat node err: {}!", heartbeatPath, e);
            }
        }
        try {
            this.zkClient.stop();
        } catch (Throwable e) {
            LOGGER.error("[register] stop zkClient failed!", e);
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("[pradar-register] stop register zookeeper node successful. {}",
                Pradar.AGENT_ID_CONTAIN_USER_INFO);
        }
    }

    @Override
    public void refresh() {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("[pradar-register] prepare to refresh register zookeeper node. {}",
                Pradar.AGENT_ID_CONTAIN_USER_INFO);
        }
        if (isStarted.get()) {
            try {
                heartbeatNode.setData(getHeartbeatDatas());
            } catch (Throwable e) {
                LOGGER.error("[pradar-register] refresh node data to zk for heartbeat node err: {}!", heartbeatPath, e);
            }
        } else {
            syncStatusFuture = ExecutorServiceFactory.getFactory().schedule(new Runnable() {
                @Override
                public void run() {
                    try {
                        heartbeatNode.setData(getHeartbeatDatas());
                    } catch (Throwable e) {
                        LOGGER.error("[pradar-register] refresh node data to zk for heartbeat node err: {}!",
                            heartbeatPath, e);
                        syncStatusFuture = ExecutorServiceFactory.getFactory().schedule(this, 5, TimeUnit.SECONDS);
                    }
                }
            }, 0, TimeUnit.SECONDS);
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("[pradar-register] refresh register zookeeper node successful. {}",
                Pradar.AGENT_ID_CONTAIN_USER_INFO);
        }

    }

    private class MiddlewareRequest {
        private final String artifactId;
        private final String groupId;
        private final String version;

        public MiddlewareRequest(String artifactId, String groupId, String version) {
            this.artifactId = artifactId;
            this.groupId = groupId;
            this.version = version;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getVersion() {
            return version;
        }
    }

    private class PushMiddlewareVO {
        private final String applicationName;
        private final List<MiddlewareRequest> middlewareList;

        public PushMiddlewareVO(String applicationName,
            List<MiddlewareRequest> middlewareList) {
            this.applicationName = applicationName;
            this.middlewareList = middlewareList;
        }

        public String getApplicationName() {
            return applicationName;
        }

        public List<MiddlewareRequest> getMiddlewareList() {
            return middlewareList;
        }
    }
}
