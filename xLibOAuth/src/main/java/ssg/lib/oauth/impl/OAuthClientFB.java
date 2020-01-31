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
package ssg.lib.oauth.impl;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.common.JSON;
import ssg.lib.oauth.OAuthAuthorizationServer;
import ssg.lib.oauth.OAuthClient;

/**
 *
 * @author 000ssg
 */
public class OAuthClientFB implements OAuthClient {

    static String authURL = "https://www.facebook.com/v4.0/dialog/oauth";
    static String graphURL = "https://graph.facebook.com";
    static String tokenURL = graphURL + "/v4.0/oauth/access_token";
    static String checkURL = graphURL + "/debug_token";
    static String userInfoURL = graphURL + "/${user_id}/";
    static String permissionsURL = graphURL + "/${user_id}/permissions";
    static String logoutURL = permissionsURL;

    static String[] defaultScope = new String[]{
        "email", //"openid"
    };

    String clientId;
    String clientSecret;
    String[] scope;

    public OAuthClientFB() {
    }

    public OAuthClientFB(
            String clientId,
            String clientSecret,
            String... scope
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
    }

    @Override
    public OAuthContext createContext(String redirectId) throws IOException {
        return new OAuthContextFB(redirectId);
    }

    @Override
    public String authorize(String clientId, String redirectId, String scope, String state) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public OAuthAuthorizationServer.TokenResponse getToken(String code, String redirectId, String client_id) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public OAuthAuthorizationServer.TokenResponse getToken(String clientId, String redirectId, String scope, String state) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public OAuthAuthorizationServer.TokenResponse getToken(String username, String password) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public OAuthAuthorizationServer.TokenResponse getToken(String scope) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public OAuthAuthorizationServer.TokenResponse getTokenExtension(String extension) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public OAuthAuthorizationServer.TokenResponse getRefreshedToken(String refresh_token, String scope) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public class OAuthContextFB extends OAuthContextBase {

        String redirect;
        String uid;
        String email;
        OAuthUserInfoFB userInfo;

        public OAuthContextFB() {
        }

        public OAuthContextFB(String redirect) {
            this.redirect = redirect;
        }

        @Override
        public String domain() {
            return "facebook";
        }

        @Override
        public String title() {
            return "Facebook";
        }

        @Override
        public <A extends OAuthClient> A getOAuth() {
            return (A) OAuthClientFB.this;
        }

        @Override
        public <U extends OAuthUserInfo> U getOAuthUserInfo() throws IOException {
            if (userInfo == null && accessToken() != null) {
                JSON.Decoder jsond = new JSON.Decoder();

                URL url = null;
                String me = "me"; // user_id;
                String s = null;

                Map<String, Object> map0 = null;
                Map<String, Object> mapP = null;
                Map<String, Object> mapI = null;

//                try {
//                    url = new URL(checkURL + "?input_token=" + accessToken() + "&access_token=" + accessToken());
//                    s = doGet(url, null, null);
//                    map0 = jsond.readObject(s, Map.class);
//                    map0 = (Map) map0.get("data");
//                    String user_id = (String) map0.get("user_id");
//                    uid = user_id;
//                } catch (Throwable th) {
//                    int a = 0;
//                }
//                try {
//                    url = new URL(permissionsURL.replace("${user_id}", me) + "?access_token=" + accessToken());
//                    s = doGet(url, null, null);
//                    mapP = jsond.readObject(s, Map.class);
//                } catch (Throwable th) {
//                    int a = 0;
//                }
                try {
                    url = new URL(userInfoURL.replace("${user_id}", me) + "picture?access_token=" + accessToken());
                    Object obj = doGet(url, null, null);
                    if (obj != null && "URLImageSource".equals(obj.getClass().getName())) { //instanceof sun.awt.image.URLImageSource) {
                        Field fld = obj.getClass().getDeclaredField("url");
                        obj = "" + fld.get(obj);
                    }
                    mapI = new LinkedHashMap<>();
                    mapI.put("picture", obj);
                } catch (Throwable th) {
                    int a = 0;
                }

                url = new URL(userInfoURL.replace("${user_id}", me) + "?access_token=" + accessToken());
                s = doGet(url, null, null);
                Map<String, Object> map = jsond.readObject(s, Map.class);
                if (map.containsKey("response")) {
                    map = (Map) (((List) map.get("response")).get(0));
                }
                String id = "" + map.get("id");
                String name = "" + map.get("name");
                String email = (String) map.get("email");
                String image = (String) map.get("picture");
                if (email == null) {
                    email = this.email;
                }
                userInfo = new OAuthUserInfoFB(id, name, email, image);

                for (Map m : new Map[]{map0, mapP, mapI}) {
                    if (m != null) {
                        map.putAll(m);
                    }
                }

                userInfo.getProperties().putAll(map);
            }
            return (U) userInfo;
        }

        @Override
        public URL getAuthURL() throws IOException {
            StringBuilder uri = new StringBuilder()
                    .append(authURL)
                    .append("?redirect_uri=")
                    .append(URLEncoder.encode(redirect, "UTF-8"))
                    //.append("&prompt=consent")
                    .append("&response_type=code")
                    .append("&client_id=")
                    .append(URLEncoder.encode(clientId, "UTF-8"));

            String[] ss = scope;//(scope != null && scope.trim().length() > 0) ? scope.split(",") : new String[0];
            for (String s : defaultScope) {
                boolean found = false;
                for (String si : ss) {
                    if (s.equals(si)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    ss = Arrays.copyOf(ss, ss.length + 1);
                    ss[ss.length - 1] = s;
                }
            }
            uri.append("&scope=");
            for (int i = 0; i < ss.length; i++) {
                if (i > 0) {
                    uri.append('+');
                }
                uri.append(URLEncoder.encode(ss[i], "UTF-8"));
            }

            URL url = new URL(uri.toString());
            return url;
        }

//        @Override
//        public boolean checkAuthData(Map<String, Object> authData) throws IOException {
//            return authData!=null && !authData.containsKey("error");
//        }
        @Override
        public String getAuthToken(String code, Map<String, Object> authData) throws IOException {
            /*
                code=4%2FrQESuO-0nBUMk3_biMm5zao1gWvUJ45MZcmoNx3DiFzsnihEsoG5jIG_0ZsgTbnmRcH5N_qGO_lWD_uoriv_xjU
                &redirect_uri=https%3A%2F%2Fdevelopers.google.com%2Foauthplayground
                &client_id=407408718192.apps.googleusercontent.com
                &client_secret=************
                &scope=
                &grant_type=authorization_code
             */

            String type = (String) authData.get("token_type");

            URL url = new URL(tokenURL);

            StringBuilder body = new StringBuilder();
            //body.append("grant_type=");
            //body.append((type != null) ? type : AT_AUTHORIZE);
            body.append((type != null && AT_REFRESH.equals(type)) ? "refresh_token=" : "code=");
            body.append(code);
            body.append("&redirect_uri=");
            body.append(URLEncoder.encode(redirect, "UTF-8"));
            body.append("&client_id=");
            body.append(URLEncoder.encode(clientId, "UTF-8"));
            body.append("&client_secret=");
            body.append(URLEncoder.encode(clientSecret, "UTF-8"));

            String[] ss = scope();
            body.append("&scope=");
            for (int i = 0; i < ss.length; i++) {
                if (i > 0) {
                    body.append('+');
                }
                body.append(URLEncoder.encode(ss[i], "UTF-8"));
            }

            Map<String, Object> tt = tokenize(url, body.toString());
            if (tt != null && !tt.containsKey("error")) {
                setTokenType((String) tt.get("token_type"));
                setAccessToken((String) tt.get("access_token"));
                setRefreshToken((String) tt.get("refresh_token"));
                setIdToken((String) tt.get("id_token"));
                this.setExpiresAt(((Number) tt.get("expires_in")).longValue());
                uid = "" + tt.get("user_id");
                //email = "" + tt.get("email");
                return accessToken();
            } else {
                throw new IOException("Failed to get token: " + tt);
            }
        }

        @Override
        public boolean revokeAuthToken() throws IOException {
            String me = "me"; // uid;
            Object obj = doHttp(new URL(logoutURL.replace("${user_id}", me) + "?access_token=" + accessToken()), "DELETE", null, null);
            Map map = null;
            if (obj instanceof Map) {
                map = (Map) obj;
            } else if (obj instanceof String && ((String) obj).startsWith("{")) {
                JSON.Decoder jsond = new JSON.Decoder();
                map = jsond.readObject((String) obj, Map.class);
            }
            boolean r = map != null && Boolean.TRUE.equals(map.get("success"));
            if (r) {
                reset();
            }
            return r;
        }

        @Override
        public String[] scope() {
            String[] ss = (scope != null) ? scope : new String[0];
            for (String s : defaultScope) {
                boolean found = false;
                for (String si : ss) {
                    if (s.equals(si)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    ss = Arrays.copyOf(ss, ss.length + 1);
                    ss[ss.length - 1] = s;
                }
            }
            return ss;
        }

        public class OAuthUserInfoFB extends OAuthUserInfoBase {

            public OAuthUserInfoFB(String id, String name, String email, String image) {
                super(id, name, email, image);
            }
        }
    }

}
