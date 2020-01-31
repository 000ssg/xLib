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
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import ssg.lib.common.JSON;
import ssg.lib.oauth.OAuthAuthorizationServer;
import ssg.lib.oauth.OAuthClient;

/**
 *
 * @author 000ssg
 */
public class OAuthClientVK implements OAuthClient {

    static String authURL = "https://oauth.vk.com/authorize";
    static String tokenURL = "https://oauth.vk.com/access_token";
    static String userInfoURL = "https://api.vk.com/method/getProfiles";

    static String[] defaultScope = new String[]{
        "email",
        "openid"
    };

    String clientId;
    String clientSecret;
    String[] scope;

    public OAuthClientVK() {
    }

    public OAuthClientVK(
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
        return new OAuthContextVK(redirectId);
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

    public class OAuthContextVK extends OAuthContextBase {

        String redirect;
        String uid;
        String email;
        OAuthUserInfoVK userInfo;

        public OAuthContextVK() {
        }

        public OAuthContextVK(String redirect) {
            this.redirect = redirect;
        }

        @Override
        public String domain() {
            return "vk.com";
        }

        @Override
        public String title() {
            return "ВКонтакте";
        }

        @Override
        public <A extends OAuthClient> A getOAuth() {
            return (A) OAuthClientVK.this;
        }

        @Override
        public <U extends OAuthUserInfo> U getOAuthUserInfo() throws IOException {
            if (userInfo == null && accessToken() != null) {
                URL url = new URL(userInfoURL + "?uid=" + uid + "&access_token=" + accessToken() + "&v=5.101");
                String s = doGet(url, null, null);
                JSON.Decoder jsond = new JSON.Decoder();
                Map<String, Object> map = jsond.readObject(s, Map.class);
                /*
                    {"response":[{"id":xxx,"first_name":"name 1","last_name":"name 2","is_closed":true,"can_access_closed":true}]}
                 */
                if (map.containsKey("response")) {
                    map = (Map) (((List) map.get("response")).get(0));
                }
                String id = "" + map.get("id");
                String name = (String) map.get("first_name") + " " + map.get("last_name");
                String email = (String) map.get("email");
                String image = (String) map.get("picture");
                if (email == null) {
                    email = this.email;
                }

                boolean isClosed = Boolean.TRUE.equals(map.get("is_closed"));
                if (isClosed) {
                    if (!Boolean.TRUE.equals(map.get("can_access_closed"))) {
                        throw new IOException("Cannot access closed user account: " + name + "/" + id);
                    }
                }

                userInfo = new OAuthUserInfoVK(id, name, email, image);
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
            uri.append("&v=5.101");

            URL url = new URL(uri.toString());
            return url;
        }

//        @Override
//        public boolean checkAuthData(Map<String, Object> authData) throws IOException {
//            return true;
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
            body.append((type != null && AT_REFRESH.equals(type)) ? "&refresh_token=" : "&code=");
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
            setTokenType((String) tt.get("token_type"));
            setAccessToken((String) tt.get("access_token"));
            setRefreshToken((String) tt.get("refresh_token"));
            setIdToken((String) tt.get("id_token"));
            this.setExpiresAt(((Number) tt.get("expires_in")).longValue());

            uid = "" + tt.get("user_id");
            email = "" + tt.get("email");
            return accessToken();
        }

        @Override
        public boolean revokeAuthToken() throws IOException {
            return false;
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

        public class OAuthUserInfoVK extends OAuthUserInfoBase {

            public OAuthUserInfoVK(String id, String name, String email, String image) {
                super(id, name, email, image);
            }
        }
    }

}
