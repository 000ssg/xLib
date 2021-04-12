/*
 * The MIT License
 *
 * Copyright 2021 sesidoro.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ssg.lib.http_cs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import ssg.lib.common.CommonTools;
import ssg.lib.common.JSON;
import ssg.lib.common.Refl;
import ssg.lib.common.net.NetTools;
import ssg.lib.http.dp.tokens.TokenVerifier;

/**
 *
 * @author 000ssg
 */
public class HttpCaller {

    String JWT_HEADER_TOKEN = "JWT-Token";

    public static enum CALL_AUTH {
        basic,
        sessionCookie,
        token
    }

    // keep session cookies (if any)
    Map<String, String> authCookies = new HashMap<>();
    // user - token ("Bearer "+token authorization)
    Map<String, String> tokens = new LinkedHashMap<>();

    // json helpers
    Refl jr = new Refl.ReflJSON.ReflJSON();
    JSON.Encoder je = new JSON.Encoder(jr);
    JSON.Decoder jd = new JSON.Decoder(jr);

    public Map<String, String> getAuthCookies() {
        return Collections.unmodifiableMap(authCookies);
    }

    public Map<String, String> getTokens() {
        return Collections.unmodifiableMap(tokens);
    }

    public JSON.Encoder jsonEncoder() {
        return je;
    }

    public JSON.Decoder jsonDecoder() {
        return jd;
    }

    /**
     * Request JWT tokens for user authenticated via Basic HTTP authentication
     * mechanism
     *
     * @param basicAuthURI
     * @param quiet
     * @param userpws "name:pwd" strings
     * @return
     * @throws IOException
     */
    public Collection<String> requestJwtTokens(URI basicAuthURI, boolean quiet, String... userpws) throws IOException {
        Collection<String> r = new ArrayList<>();

        // get login tokens for enumerated users
        for (int i = 0; i < userpws.length; i++) {
            int idx = userpws[i].indexOf(":");
            final String user = userpws[i].substring(0, idx);
            String pwd = userpws[i].substring(idx + 1);
            final URL url = new URL(basicAuthURI.toString().replace("://", "://" + user + ":" + pwd + "@"));
            if (!quiet) {
                System.out.println("--------------------------------------------------------------------\n-- Execute: " + url);
            }
            NetTools.httpGet(url, new HashMap() {
                {
                    if (authCookies.containsKey(user)) {
                        put("Cookie", authCookies.get(user));
                    }
                }
            }, null, new NetTools.HttpResult() {
                @Override
                public void onError(HttpURLConnection conn, long startedNano) throws IOException {
                    System.out.println(conn.getResponseCode() + " " + conn.getResponseMessage() + " Failed to create JWT token for " + user.substring(0, 2) + "..." + user.substring(user.length() - 1));
                    conn.disconnect();
                }

                @Override
                public void onResult(HttpURLConnection conn, long startedNano) throws IOException {
                    String s = conn.getHeaderField("Set-Cookie");
                    if (s != null) {
                        authCookies.put(user, s.substring(0, s.indexOf(";") + 1));
                        r.add(user);
                    }

                    String jt = conn.getHeaderField(JWT_HEADER_TOKEN);
                    if (jt != null) {
                        tokens.put(user, jt);
                        if (!quiet) {
                            System.out.println(conn.getResponseCode() + " " + conn.getResponseMessage() + ": " + JWT_HEADER_TOKEN + "=" + jt);
                        }
                    } else {
                        InputStream is = conn.getInputStream();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buf = new byte[1024];
                        int c = 0;
                        while ((c = is.read(buf)) != -1) {
                            baos.write(buf, 0, c);
                        }
                        is.close();
                        baos.close();
                        Map result = jd.readObject(baos.toString("UTF-8"), Map.class);
                        if (result != null && result.get("result") instanceof String) {
                            tokens.put(user, (String) result.get("result"));
                        }
                        if (!quiet) {
                            System.out.println(conn.getResponseCode() + " " + conn.getResponseMessage() + ": " + result);
                        }
                    }
                }
            });
        }
        return r;
    }

    /**
     * Create any other token.
     *
     * @param tokenVerifier
     * @param user
     * @param domain
     * @param name
     * @param roles
     * @param expires
     * @return created token
     * @throws IOException
     */
    public String registerToken(TokenVerifier tokenVerifier, String user, String domain, String name, String roles, Long expires) throws IOException {
        String token = tokenVerifier.createToken(user, domain, name, roles, expires).getToken_id();
        tokens.put(user, token);
        return token;
    }

    public void doCallAll(String serviceURI, boolean quiet, Map<String, Object> headers, byte[] data, BiFunction<String, Object[], String> uriCorrection, final Consumer<HttpCallResult> consumer) throws IOException {
        doCallAllSessionCookies(serviceURI, quiet, headers, data, uriCorrection, consumer);
        doCallAllTokens(serviceURI, quiet, headers, data, uriCorrection, consumer);
    }

    public void doCallAllTokens(String serviceURI, boolean quiet, Map<String, Object> headers, byte[] data, BiFunction<String, Object[], String> uriCorrection, final Consumer<HttpCallResult> consumer) throws IOException {
        for (String user : tokens.keySet()) {
            try {
                this.doCall(serviceURI, user, CALL_AUTH.token, quiet, headers, data, uriCorrection, consumer);
            } catch (Throwable th) {
                if (consumer != null) try {
                    consumer.accept(new HttpCallResult(user, tokens.get(user), null, null, th));
                } catch (Throwable th1) {
                }
            }
        }
    }

    public void doCallAllSessionCookies(String serviceURI, boolean quiet, Map<String, Object> headers, byte[] data, BiFunction<String, Object[], String> uriCorrection, final Consumer<HttpCallResult> consumer) throws IOException {
        for (String user : authCookies.keySet()) {
            try {
                this.doCall(serviceURI, user, CALL_AUTH.sessionCookie, quiet, headers, data, uriCorrection, consumer);
            } catch (Throwable th) {
                if (consumer != null) try {
                    consumer.accept(new HttpCallResult(user, null, null, null, th));
                } catch (Throwable th1) {
                }
            }
        }
    }

    public void doCall(String serviceURI, String userName, CALL_AUTH callAuth, boolean quiet, final Map<String, Object> headers, byte[] data, BiFunction<String, Object[], String> uriCorrection, final Consumer<HttpCallResult> consumer) throws IOException {
        if (uriCorrection != null) {
            serviceURI = uriCorrection.apply(serviceURI, new Object[]{userName, callAuth, headers, data});
        }
        if (CALL_AUTH.basic.equals(callAuth)) {
            serviceURI = serviceURI.replace("://", "://"+userName + "@");
            if (userName.contains(":")) {
                userName = userName.substring(0, userName.indexOf(":"));
            }
        }
        final String user = userName;
        final String token = CALL_AUTH.token.equals(callAuth) ? tokens.get(user) : null;
        final String cookie = CALL_AUTH.sessionCookie.equals(callAuth) ? authCookies.get(user) : null;
        URL url = new URL(serviceURI);
        System.out.println("CALL[" + user + "]: " + url);
        NetTools.httpGet(url, new HashMap() {
            {
                if (headers != null) {
                    putAll(headers);
                }
                if (token != null) {
                    put("Authorization", "Bearer " + token);
                }
                if (cookie != null) {
                    put("Cookie", cookie);
                }
            }
        }, data, new NetTools.HttpResult() {
            @Override
            public void onResult(HttpURLConnection conn, long startedNano) throws IOException {
                byte[] buf = null;
                try {
                    InputStream is = conn.getInputStream();
                    buf = CommonTools.loadInputStream(is);
                    if (!quiet) try {
                        Map result = jd.readObject(new String(buf, "UTF-8"), Map.class);
                        System.out.println(conn.getResponseCode() + " " + conn.getResponseMessage() + ": " + result);
                    } catch (Throwable th) {
                        try {
                            System.out.println(conn.getResponseCode() + " " + conn.getResponseMessage() + (buf != null ? ": " + new String(buf, "UTF-8") : ""));
                        } catch (Throwable th1) {
                        }
                    }
                } finally {
                    if (consumer != null) {
                        consumer.accept(new HttpCallResult(user, token, conn, buf, null));
                    }
                    conn.disconnect();
                }
            }

            @Override
            public void onError(HttpURLConnection conn, long startedNano) throws IOException {
                System.out.println(conn.getResponseCode() + " " + conn.getResponseMessage());
                byte[] buf = null;
                try {
                    try ( InputStream is = conn.getErrorStream();) {
                        buf = CommonTools.loadInputStream(is);
                        if (!quiet) {
                            System.out.println("  " + new String(buf, "UTF-8").replace("\n", "\n  "));
                        }
                    }
                } finally {
                    if (consumer != null) {
                        consumer.accept(new HttpCallResult(user, token, conn, buf, null));
                    }
                    conn.disconnect();
                }
            }
        });
    }

    public static String uriWithParameters(String uri, int off, String... params) throws IOException {
        if (params != null) {
            Map<String, Object> ps = new LinkedHashMap<>();
            for (int i = off; i < params.length; i += 2) {
                String name = params[i];
                String value = i + 1 < params.length ? params[i + 1] : null;
                ps.put(name, value);
            }
            return uriWithParameters(uri, ps);
        } else {
            return uri;
        }
    }

    /**
     * Add parameter to URI string.
     *
     * @param uri
     * @param params
     * @return
     * @throws IOException
     */
    public static String uriWithParameters(String uri, Map<String, Object> params) throws IOException {
        String query = "";
        if (params != null && !params.isEmpty()) {
            boolean first = !uri.contains("?");
            for (Entry<String, Object> e : params.entrySet()) {
                if (first) {
                    query += "?";
                    first = false;
                } else {
                    query += "&";
                }
                if (e.getValue() != null) {
                    Object v = e.getValue();
                    if (v.getClass().isArray()) {
                        boolean first2 = true;
                        for (int j = 0; j < Array.getLength(v); j++) {
                            if (Array.get(v, j) == null) {
                                continue;
                            }
                            if (first2) {
                                first2 = false;
                            } else {
                                query += "&";
                            }
                            query += URLEncoder.encode(e.getKey(), "UTF-8");
                            query += "=" + URLEncoder.encode(e.getValue().toString(), "UTF-8");
                        }
                    } else {
                        query += URLEncoder.encode(e.getKey(), "UTF-8");
                        query += "=" + URLEncoder.encode(e.getValue().toString(), "UTF-8");
                    }
                } else {
                    query += URLEncoder.encode(e.getKey(), "UTF-8");
                }
            }
        }
        return uri + query;
    }

    public class HttpCallResult {

        public String user;
        public String token;
        public int code;
        public String message;
        public byte[] data;
        public String contentType;
        public Map<String, List<String>> headers;
        public Throwable error;

        public HttpCallResult(
                String user,
                String token,
                HttpURLConnection conn,
                byte[] data,
                Throwable error
        ) throws IOException {
            this.user = user;
            this.token = token;
            this.data = data;
            this.error = error;
            if (conn != null) try {
                this.code = conn.getResponseCode();
                this.message = conn.getResponseMessage();
                contentType = conn.getHeaderField("Content-Type");
                headers = conn.getHeaderFields();
            } catch (Throwable th) {
            }
        }
    }
}
