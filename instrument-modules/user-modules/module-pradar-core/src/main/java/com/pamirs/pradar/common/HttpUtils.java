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
package com.pamirs.pradar.common;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.pamirs.pradar.Throwables;
import com.shulie.instrument.simulator.api.util.StringUtil;
import org.apache.commons.lang.StringUtils;

public abstract class HttpUtils {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    public static HttpResult doGet(String url) {
        HostPort hostPort = getHostPortUrlFromUrl(url);
        return doGet(hostPort.host, hostPort.port, hostPort.url);
    }

    public static HttpResult doGet(String host, int port, String url) {
        InputStream input = null;
        OutputStream output = null;
        Socket socket = null;
        try {
            SocketAddress address = new InetSocketAddress(host, port);

            StringBuilder request = new StringBuilder("GET ").append(url).append(" HTTP/1.1\r\n")
                    .append("Host: ").append(host).append(":").append(port).append("\r\n")
                    .append("Connection: Keep-Alive\r\n");

            Map<String, String> mustHeaders = getHttpMustHeaders();
            if (!mustHeaders.isEmpty()) {
                for (Map.Entry<String, String> entry : mustHeaders.entrySet()) {
                    if (!StringUtils.isBlank(entry.getValue())) {
                        request.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
                    }
                }
            }
            request.append("\r\n");

            socket = new Socket();
            // 设置建立连接超时时间 5s
            socket.connect(address, 5000);
            // 设置读取数据超时时间 10s
            socket.setSoTimeout(10000);
            output = socket.getOutputStream();
            output.write(request.toString().getBytes(UTF_8));
            output.flush();
            input = socket.getInputStream();
            String statusStr = StringUtils.trim(readLine(input));
            String[] statusArr = StringUtils.split(statusStr, ' ');
            int status = 500;
            try {
                status = Integer.parseInt(statusArr[1]);
            } catch (Throwable e) {
                // ignore
            }

            Map<String, List<String>> headers = readHeaders(input);
            input = wrapperInput(headers, input);
            String result = toString(input);
            return HttpResult.result(status, result);
        } catch (Throwable e) {
            return HttpResult.result(500, Throwables.getStackTraceAsString(e));
        } finally {
            closeQuietly(input);
            closeQuietly(output);

            // JDK 1.6 Socket没有实现Closeable接口
            if (socket != null) {
                try {
                    socket.close();
                } catch (final IOException ioe) {
                    // ignore
                }
            }
        }
    }

    public static HttpResult doPost(String url, String body) {
        HostPort hostPort = getHostPortUrlFromUrl(url);
        return doPost(hostPort.host, hostPort.port, hostPort.url, body);
    }

    public static HttpResult doPost(String host, int port, String url, String body) {
        InputStream input = null;
        OutputStream output = null;
        Socket socket = null;
        try {
            SocketAddress address = new InetSocketAddress(host, port);
            socket = new Socket();
            socket.connect(address, 1000); // 设置建立连接超时时间 1s
            socket.setSoTimeout(5000); // 设置读取数据超时时间 5s
            output = socket.getOutputStream();

            StringBuilder request = new StringBuilder("POST ").append(url).append(" HTTP/1.1\r\n")
                    .append("Host: ").append(host).append(":").append(port).append("\r\n")
                    .append("Connection: Keep-Alive\r\n");

            Map<String, String> mustHeaders = getHttpMustHeaders();
            if (!mustHeaders.isEmpty()) {
                for (Map.Entry<String, String> entry : mustHeaders.entrySet()) {
                    if (!StringUtils.isBlank(entry.getValue())) {
                        request.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
                    }
                }
            }

            if (body != null && !body.isEmpty()) {
                request.append("Content-Length: ").append(body.getBytes().length).append("\r\n")
                        .append("Content-Type: application/json\r\n");
            }

            request.append("\r\n");
            output.write(request.toString().getBytes(UTF_8));

            if (body != null && !body.isEmpty()) {
                output.write(body.getBytes(UTF_8));
            }
            output.flush();

            input = socket.getInputStream();
            String statusStr = StringUtils.trim(readLine(input));
            String[] statusArr = StringUtils.split(statusStr, ' ');
            int status = 500;
            try {
                status = Integer.parseInt(statusArr[1]);
            } catch (Throwable e) {
                // ignore
            }
            Map<String, List<String>> headers = readHeaders(input);
            input = wrapperInput(headers, input);
            String result = toString(input);
            return HttpResult.result(status, result);
        } catch (IOException e) {
            return HttpResult.result(500, Throwables.getStackTraceAsString(e));
        } finally {
            closeQuietly(input);
            closeQuietly(output);

            // JDK 1.6 Socket没有实现Closeable接口
            if (socket != null) {
                try {
                    socket.close();
                } catch (final IOException ioe) {
                    // ignore
                }
            }
        }
    }

    public static String toString(InputStream input) throws IOException {
        ByteArrayOutputStream content = null;
        try {
            content = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = input.read(buffer)) > 0) {
                content.write(buffer, 0, len);
            }
            return new String(content.toByteArray(), UTF_8);
        } finally {
            closeQuietly(content);
        }
    }

    public static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream bufdata = new ByteArrayOutputStream();
        int ch;
        while ((ch = input.read()) >= 0) {
            bufdata.write(ch);
            if (ch == '\n') {
                break;
            }
        }
        if (bufdata.size() == 0) {
            return null;
        }
        byte[] rawdata = bufdata.toByteArray();
        int len = rawdata.length;
        int offset = 0;
        if (len > 0) {
            if (rawdata[len - 1] == '\n') {
                offset++;
                if (len > 1) {
                    if (rawdata[len - 2] == '\r') {
                        offset++;
                    }
                }
            }
        }
        return new String(rawdata, 0, len - offset, UTF_8);
    }

    public static InputStream wrapperInput(Map<String, List<String>> headers, InputStream input) {
        List<String> transferEncodings = headers.get("Transfer-Encoding");
        if (transferEncodings != null && !transferEncodings.isEmpty()) {
            String encodings = transferEncodings.get(0);
            String[] elements = StringUtils.split(encodings, ';');
            int len = elements.length;
            if (len > 0 && ("chunked".equals(elements[len - 1]) || "CHUNKED".equals(elements[len - 1]))) {
                return new ChunkedInputStream(input);
            }
            return input;
        }
        List<String> contentLengths = headers.get("Content-Length");
        if (contentLengths != null && !contentLengths.isEmpty()) {
            long length = -1;
            for (String contentLength : contentLengths) {
                try {
                    length = Long.parseLong(contentLength);
                    break;
                } catch (final NumberFormatException ignore) {
                    // ignored
                }
            }
            if (length >= 0) {
                return new ContentLengthInputStream(input, length);
            }
        }
        return input;
    }

    public static Map<String, List<String>> readHeaders(InputStream input)
            throws IOException {
        Map<String, List<String>> headers = new HashMap<String, List<String>>();
        String line = readLine(input);
        while (line != null && !line.isEmpty()) {
            String[] headerPair = StringUtils.split(line, ':');
            String name = headerPair[0].trim();
            String value = headerPair[1].trim();
            List<String> values = headers.get(name);
            if (values == null) {
                values = new ArrayList<String>();
                headers.put(name, values);
            }
            values.add(value);
            line = readLine(input);
        }
        return headers;
    }

    public static void exhaustInputStream(InputStream inStream)
            throws IOException {
        byte buffer[] = new byte[1024];
        while (inStream.read(buffer) >= 0) {
        }
    }

    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (final IOException ioe) {
                // ignore
            }
        }
    }

    private static Pattern URL_PATTERN = Pattern.compile("^(([^:/?#]+):)?(//([^/?#]*))?", Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) {
        getHostPortUrlFromUrl("http://127.0.0.1/tro-web/api/link/ds/configs/pull?appName=test-druid");
    }

    private static HostPort getHostPortUrlFromUrl(String url) {
        String domain = url;
        String restUrl = url;
        Matcher matcher = URL_PATTERN.matcher(url);
        if (matcher.find()) {
            String group = matcher.group();
            domain = group.substring(group.indexOf("//") + 2);
            restUrl = url.substring(url.indexOf(group) + group.length());
        }

        HostPort hostPort = new HostPort();
        hostPort.url = restUrl;
        int indexOfColon = domain.indexOf(":");
        if (indexOfColon == -1) {
            hostPort.host = domain;
            hostPort.port = 80;
        } else {
            hostPort.host = domain.substring(0, indexOfColon);
            hostPort.port = Integer.parseInt(domain.substring(indexOfColon + 1));
        }
        return hostPort;
    }

    private static class HostPort {
        public String host;
        public int port;
        public String url;

        @Override
        public String toString() {
            return "HostPort{" +
                    "host='" + host + '\'' +
                    ", port=" + port +
                    ", url='" + url + '\'' +
                    '}';
        }
    }

    private final static String TENANT_APP_KEY_STR = "tenant.app.key";

    private final static String PRADAR_USER_ID_STR = "pradar.user.id";

    private final static String PRADAR_ENV_CODE_STR = "pradar.env.code";

    private final static String PRADAR_USER_APP_KEY = "user.app.key";

    private static String getProperty(String key) {
        String val = System.getProperty(key);
        if (StringUtil.isEmpty(val)) {
            val = System.getenv(key);
        }
        if (val == null || val.isEmpty()) {
            return null;
        } else {
            return val;
        }
    }

    static private String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        return value == null ? defaultValue : value;
    }

    private static Map<String, String> getHttpMustHeaders() {
        Map<String, String> headers = new HashMap<String, String>();
        String envCode = getProperty(PRADAR_ENV_CODE_STR);
        // 新探针兼容老版本的控制台，所以userAppKey和tenantAppKey都传
        headers.put("userAppKey", getProperty(PRADAR_USER_APP_KEY, getProperty(TENANT_APP_KEY_STR)));
        headers.put("tenantAppKey", getProperty(TENANT_APP_KEY_STR, getProperty(PRADAR_USER_APP_KEY)));
        headers.put("userId", getProperty(PRADAR_USER_ID_STR));
        headers.put("envCode", envCode);
        return headers;
    }

    public static class HttpResult {
        /**
         * 是否成功
         */
        private int status;
        /**
         * 结果
         */
        private String result;

        public static HttpResult result(int status, String result) {
            HttpResult httpResult = new HttpResult();
            httpResult.setStatus(status);
            httpResult.setResult(result);
            return httpResult;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public boolean isSuccess() {
            return status == 200;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }
    }
}
