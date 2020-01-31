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
import java.util.Map;
import ssg.lib.common.JSON;
import ssg.lib.oauth.OAuthAuthorizationServer;
import ssg.lib.oauth.OAuthClient;

/**
 * Configure Google OAuth at
 * https://console.developers.google.com/apis/credentials
 *
 * @author 000ssg
 */
public class OAuthClientGoolge implements OAuthClient {

    static String authURL = "https://accounts.google.com/o/oauth2/v2/auth";
    static String tokenURL = "https://www.googleapis.com/oauth2/v4/token";
    static String logoutURL = "https://accounts.google.com/o/oauth2/revoke";
    static String userInfoURL = "https://www.googleapis.com/oauth2/v2/userinfo";

    static String[] defaultScope = new String[]{
        "https://www.googleapis.com/auth/userinfo.email",
        "openid"
    };

    String clientId;
    String clientSecret;
    String[] scope;

    public OAuthClientGoolge() {
    }

    public OAuthClientGoolge(
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
        return new OAuthContextGoogle(redirectId);
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

    public class OAuthContextGoogle extends OAuthContextBase {

        String redirect;
        OAuthUserInfoGoogle userInfo;

        public OAuthContextGoogle() {
        }

        public OAuthContextGoogle(String redirect) {
            this.redirect = redirect;
        }

        @Override
        public String domain() {
            return "google";
        }

        @Override
        public String title() {
            return "Google";
        }

        @Override
        public <A extends OAuthClient> A getOAuth() {
            return (A) OAuthClientGoolge.this;
        }

        @Override
        public <U extends OAuthUserInfo> U getOAuthUserInfo() throws IOException {
            if (userInfo == null && accessToken() != null) {
                String s = doGet(new URL(userInfoURL), null, null);
                JSON.Decoder jsond = new JSON.Decoder();
                Map<String, Object> map = jsond.readObject(s, Map.class);
                /*
                  "picture": "https://lh4.googleusercontent.com/-bTt1pFrGnrc/AAAAAAAAAAI/AAAAAAAAAAA/2Us6OODhi3s/photo.jpg", 
                  "verified_email": true, 
                  "id": "114966834125215044653", 
                  "email": "000sobrat@gmail.com"
                 */
                String id = (String) map.get("id");
                String email = (String) map.get("email");
                String image = (String) map.get("picture");
                userInfo = new OAuthUserInfoGoogle(id, null, email, image);
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
                    .append("&prompt=consent")
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
            uri.append("&access_type=offline");

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
            body.append("grant_type=");
            body.append((type != null) ? type : AT_AUTHORIZE);
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
            this.setExpiresAt((Long) tt.get("expires_at"));
            return accessToken();
        }

        @Override
        public boolean revokeAuthToken() throws IOException {
            String token = accessToken();
            if (token != null) {
                Object obj = this.doGet(new URL(logoutURL + "?token=" + token), null, null);
                int a = 0;
                reset();
                return true;
            }
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

        public class OAuthUserInfoGoogle extends OAuthUserInfoBase {

            public OAuthUserInfoGoogle(String id, String name, String email, String image) {
                super(id, name, email, image);
            }
        }
    }

}
