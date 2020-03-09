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
package ssg.lib.oauth2.impl;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Map;
import static ssg.lib.oauth2.OAuthConstants.TR_AUTHORIZATION_CODE;
import static ssg.lib.oauth2.OAuthConstants.TR_REFRESH;
import ssg.lib.oauth2.client.OAuthClient;
import ssg.lib.oauth2.client.OAuthContext;
import ssg.lib.oauth2.client.OAuthContextBase;
import ssg.lib.oauth2.client.OAuthUserInfo;
import ssg.lib.oauth2.client.OAuthUserInfoBase;

/**
 * Configure Google OAuth at
 * https://console.developers.google.com/apis/credentials
 *
 * @author 000ssg
 */
public class OAuthClientInstagram implements OAuthClient {

    static String authURL = "https://api.instagram.com/oauth/authorize";
    static String tokenURL = "https://api.instagram.com/oauth/access_token";
//    static String logoutURL = "https://accounts.google.com/o/oauth2/revoke";
    static String userInfoURL = "https://api.instagram.com/v1/users/self/";

    static String[] defaultScope = new String[]{
        "basic",
        "openid"
    };

    String clientId;
    String clientSecret;
    String[] scope;

    public OAuthClientInstagram() {
    }

    public OAuthClientInstagram(
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
        return new OAuthContextInstagram(redirectId);
    }

    public class OAuthContextInstagram extends OAuthContextBase {

        String redirect;
        OAuthUserInfoInstagram userInfo;

        public OAuthContextInstagram() {
        }

        public OAuthContextInstagram(String redirect) {
            this.redirect = redirect;
        }

        @Override
        public String domain() {
            return "instagram";
        }

        @Override
        public String title() {
            return "Instagram";
        }

        @Override
        public <A extends OAuthClient> A getOAuth() {
            return (A) OAuthClientInstagram.this;
        }

        @Override
        public <U extends OAuthUserInfo> U getOAuthUserInfo() throws IOException {
            if (userInfo == null && accessToken() != null) {
                String s = doGet(new URL(userInfoURL + "?access_token=" + accessToken()), null, null);
                Map<String, Object> map = json2map(s);
                String id = (String) map.get("id");
                String name = (String) map.get("name");
                String email = (String) map.get("email");
                String image = (String) map.get("picture");
                userInfo = new OAuthUserInfoInstagram(id, name, email, image);
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
                    .append("&response_type=code")
                    .append("&client_id=")
                    .append(URLEncoder.encode(clientId, "UTF-8"));

            String[] ss = scope;
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
            //uri.append("&access_type=offline");

            URL url = new URL(uri.toString());
            return url;
        }

        @Override
        public String getAuthToken(String code, Map<String, Object> authData) throws IOException {
            String type = (String) authData.get("token_type");

            URL url = new URL(tokenURL);

            StringBuilder body = new StringBuilder();
            body.append("grant_type=");
            body.append((type != null) ? type : TR_AUTHORIZATION_CODE);
            body.append((type != null && TR_REFRESH.equals(type)) ? "&refresh_token=" : "&code=");
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

        public class OAuthUserInfoInstagram extends OAuthUserInfoBase {

            public OAuthUserInfoInstagram(String id, String name, String email, String image) {
                super(id, name, email, image);
            }
        }
    }

}
