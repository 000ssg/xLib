/*
 * The MIT License
 *
 * Copyright 2020 Sergey Sidorov/000ssg@gmail.com
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
package ssg.lib.oauth;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import ssg.lib.common.JSON;
import ssg.lib.oauth.OAuthAuthorizationServer.TokenResponse;

/**
 *
 * @author 000ssg
 */
public interface OAuthClient {

    public static final String AT_AUTHORIZE = "authorization_code";
    public static final String AT_REFRESH = "refresh_token";

    OAuthContext createContext(String redirectId) throws IOException;

    String authorize(String clientId, String redirectId, String scope, String state) throws IOException;

    /**
     * Code authorization grant.
     *
     * @param code
     * @param redirectId
     * @param client_id
     * @return
     * @throws IOException
     */
    TokenResponse getToken(String code, String redirectId, String client_id) throws IOException;

    /**
     * Implicit code authorization grant
     *
     * @param clientId
     * @param redirectId
     * @param scope
     * @param state
     * @return
     * @throws IOException
     */
    TokenResponse getToken(String clientId, String redirectId, String scope, String state) throws IOException;

    /**
     * Password credentials grant
     *
     * @param username
     * @param password
     * @return
     * @throws IOException
     */
    TokenResponse getToken(String username, String password) throws IOException;

    /**
     * Client credentials grant
     *
     * @param scope
     * @return
     * @throws IOException
     */
    TokenResponse getToken(String scope) throws IOException;

    /**
     * Extension grant
     *
     * @param extension
     * @return
     * @throws IOException
     */
    TokenResponse getTokenExtension(String extension) throws IOException;

    /**
     * Refresh token
     *
     * @param refresh_token
     * @param scope
     * @return
     * @throws IOException
     */
    TokenResponse getRefreshedToken(String refresh_token, String scope) throws IOException;

    /**
     * Represents OAuth info
     */
    public static interface OAuthContext {

        /**
         * Authentication domain name to distinguish oauth-only accounts.
         *
         * @return
         */
        String domain();

        String title();

        // owner
        <A extends OAuthClient> A getOAuth();

        <U extends OAuthUserInfo> U getOAuthUserInfo() throws IOException;

        // Authentication URL builder
        URL getAuthURL() throws IOException;

        /**
         * Check if given authData are related to this authentication
         * session/context.
         *
         * @param authData
         * @return
         * @throws IOException
         */
        boolean checkAuthData(Map<String, Object> authData) throws IOException;

        /**
         * Request authorization token
         *
         * @param code
         * @param authData
         * @return
         * @throws IOException
         */
        String getAuthToken(String code, Map<String, Object> authData) throws IOException;

        /**
         * Do logout...
         *
         * @return
         * @throws IOException
         */
        boolean revokeAuthToken() throws IOException;

        // token/auth properties
        String code();

        String idToken();

        String accessToken();

        String refreshToken();

        long expiresAt();

        String tokenType();

        String[] scope();

        void setProperty(String name, Object value);

        <T> T getProperty(String name);

        void reset();
    }

    public static interface OAuthUserInfo {

        String id();

        String name();

        String email();

        String image();

        Map<String, Object> getProperties();
    }

    public static class OAuthUserInfoBase implements OAuthUserInfo {

        String id;
        String name;
        String email;
        String image;
        Map<String, Object> properties = new LinkedHashMap<String, Object>();

        public OAuthUserInfoBase(String id, String name, String email, String image) {
            this.id = id;
            this.name = name;
            this.email = (email != null) ? email.toLowerCase() : email;
            this.image = image;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String email() {
            return email;
        }

        @Override
        public String image() {
            return image;
        }

        public Map<String, Object> getProperties() {
            return properties;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(getClass().getSimpleName());
            sb.append('{');
            sb.append("\n  id=" + id);
            sb.append("\n  name=" + name);
            sb.append("\n  email=" + email);
            sb.append("\n  image=" + image);
            sb.append("\n  properties[" + properties.size() + "]=");
            for (String key : properties.keySet()) {
                Object val = properties.get(key);
                sb.append("\n    " + key + ": ");
                sb.append(("" + val).replace("\n", "\n      "));
            }
            sb.append('}');
            return sb.toString();
        }

    }

    public static abstract class OAuthContextBase implements OAuthContext {

        public boolean DEBUG = false;

        String code;
        String idToken;
        String accessToken;
        String refreshToken;
        long expiresAt;
        String tokenType;
        Map<String, Object> props = new LinkedHashMap<>();

        @Override
        public String code() {
            return code;
        }

        @Override
        public String idToken() {
            return idToken;
        }

        @Override
        public String accessToken() {
            return accessToken;
        }

        @Override
        public String refreshToken() {
            return refreshToken;
        }

        @Override
        public long expiresAt() {
            return expiresAt;
        }

        @Override
        public String tokenType() {
            return tokenType;
        }

        @Override
        public void setProperty(String name, Object value) {
            if (name == null) {
                return;
            }
            if (value == null && props.containsKey(name)) {
                props.remove(name);
            } else {
                props.put(name, value);
            }
        }

        @Override
        public <T> T getProperty(String name) {
            return (name != null) ? (T) props.get(name) : null;
        }

        @Override
        public void reset() {
            code = null;
            idToken = null;
            accessToken = null;
            refreshToken = null;
            expiresAt = 0;
            tokenType = null;
            props.clear();
        }

        @Override
        public boolean checkAuthData(Map<String, Object> authData) throws IOException {
            return authData != null && !authData.containsKey("error");
        }

        ////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////
        public void setCode(String code) {
            this.code = code;
        }

        public void setIdToken(String token) {
            this.idToken = token;
        }

        public void setAccessToken(String token) {
            this.accessToken = token;
        }

        public void setRefreshToken(String token) {
            this.refreshToken = token;
        }

        public void setTokenType(String tokenType) {
            this.tokenType = tokenType;
        }

        public void setExpiresAt(long time) {
            this.expiresAt = time;
        }

        ////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////
        /**
         * Invoke tokenURL with given form data and return data as map.
         *
         * @param tokenURL
         * @param formData
         * @return
         * @throws IOException
         */
        public Map<String, Object> tokenize(URL tokenURL, String formData) throws IOException {
            HttpURLConnection conn = (HttpURLConnection) tokenURL.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.connect();
            conn.getOutputStream().write(formData.getBytes("ISO-8859-1"));
            conn.getOutputStream().close();

            if (conn.getContentType() != null) {
                String ct = conn.getContentType();
                String ce = conn.getContentEncoding();

                Object obj = null;
                try {
                    obj = conn.getContent();
                } catch (IOException ioex) {
                    obj = conn.getErrorStream();
                }
                if (obj instanceof InputStream) {
                    InputStream is = (InputStream) obj;
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    byte[] buf = new byte[1024];
                    int c = 0;
                    while ((c = is.read(buf)) != -1) {
                        os.write(buf, 0, c);
                    }
                    obj = os.toByteArray();
                    is.close();
                }

                if (ct != null && (ct.toLowerCase().contains("text") || ct.toLowerCase().contains("json")) && obj instanceof byte[]) {
                    obj = new String((byte[]) obj, (ce != null) ? ce : "ISO-8859-1");

                    JSON.Decoder jsond = new JSON.Decoder();
                    Map map = jsond.readObject((String) obj, Map.class);
                    String idToken = (String) map.get("id_token");
                    if (idToken != null) {
                        Map m = jsond.readObject(new ByteArrayInputStream(Base64.getUrlDecoder().decode(idToken.split("\\.")[1])), Map.class);
                        map.put("expires_at", ((Number) m.get("exp")).longValue() * 1000);
                    }
                    return map;
                }
            }
            throw new IOException("Invalid tokenizer URL/response");
        }

        public <T> T doGet(URL url, Map<String, String> headers, Object body) throws IOException {
            return this.doHttp(url, "GET", headers, body);
        }

        public <T> T doPut(URL url, Map<String, String> headers, Object body) throws IOException {
            return this.doHttp(url, "PUT", headers, body);
        }

        public <T> T doPost(URL url, Map<String, String> headers, Object body) throws IOException {
            return this.doHttp(url, "POST", headers, body);
        }

        public <T> T doDelete(URL url, Map<String, String> headers, Object body) throws IOException {
            return this.doHttp(url, "DELETE", headers, body);
        }

        public <T> T doHttp(URL url, String method, Map<String, String> headers, Object body) throws IOException {
            if (DEBUG) {
                System.out.println("doHttp.BEGIN[" + method + "] " + url + "\n  headers: " + headers + "\n  body: " + ("" + body).replace("\n", "\n    "));
            }
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod(method);
            if (headers != null) {
                for (Entry<String, String> header : headers.entrySet()) {
                    conn.setRequestProperty(header.getKey(), header.getValue());
                }
            }
            conn.setRequestProperty("Authorization", tokenType() + " " + accessToken());

            conn.setDoInput(true);
            conn.setDoOutput(body != null);
            conn.connect();

            Object obj = null;
            try {
                try {
                    obj = conn.getContent();
                } catch (IOException ioex) {
                    obj = conn.getErrorStream();
                }
                if (obj instanceof InputStream) {
                    if (obj instanceof InputStream) {
                        String ce = conn.getContentEncoding();
                        InputStream is = (InputStream) obj;
                        if (ce != null && ce.contains("gzip")) {
                            is = new GZIPInputStream(is);
                        }
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        byte[] buf = new byte[1024];
                        int c = 0;
                        while ((c = is.read(buf)) != -1) {
                            os.write(buf, 0, c);
                        }
                        obj = os.toByteArray();
                        is.close();
                    }
                }
                if (obj instanceof byte[]) {
                    String ct = conn.getContentType();
                    String ce = conn.getContentEncoding();
                    String[] cts = ct.split(";");
                    for (String s : cts) {
                        if (s.startsWith("charset")) {
                            ce = s.substring(s.indexOf("=") + 1);
                        }
                    }
                    obj = new String((byte[]) obj, (ce != null) ? ce : "ISO-8859-1");
                }
            } catch (Throwable th) {
                throw new IOException("Error in " + method + " " + url, th);
            }
            if (DEBUG) {
                System.out.println("doHttp.END  [" + method + "] " + url + "\n  result: " + ("" + obj).replace("\n", "\n    "));
            }
            return (T) obj;
        }

    }
}
