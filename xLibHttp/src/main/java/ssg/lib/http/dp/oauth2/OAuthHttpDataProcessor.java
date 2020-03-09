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
package ssg.lib.http.dp.oauth2;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.di.DI;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.HttpAuthenticator;
import ssg.lib.http.HttpDataProcessor;
import ssg.lib.http.HttpMatcher;
import ssg.lib.http.HttpSession;
import ssg.lib.http.HttpUser;
import ssg.lib.http.RAT;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.oauth2.client.OAuthClient;
import ssg.lib.oauth2.client.OAuthContext;
import ssg.lib.oauth2.client.OAuthUserInfo;

/**
 *
 * @author 000ssg
 */
public class OAuthHttpDataProcessor extends HttpDataProcessor {

    HttpAuthenticator auth;
    Map<String, OAuthClient> oauths = new LinkedHashMap<>();
    String authorize = "login";
    String token = "token";
    String logout = "logout";

    public OAuthHttpDataProcessor() {
        super(new HttpMatcher("oauth/*"));
    }

    public OAuthHttpDataProcessor(HttpMatcher matcher) {
        super(matcher);
    }

    @Override
    public Runnable getRunnable(Object provider, DI data) {
        return null;
    }

    @Override
    public List<Task> getTasks(TaskPhase... phases) {
        return Collections.emptyList();
    }

    public OAuthHttpDataProcessor addOAuth(String name, OAuthClient client) {
        if (name != null && client != null) {
            oauths.put(name, client);
        }
        return this;
    }

    public void setHttpAuhtenticator(HttpAuthenticator auth) {
        this.auth = auth;
    }

    /**
     * Handles authorization flow.
     *
     * @param data
     * @throws IOException
     */
    @Override
    public void onCompleted(HttpData data) throws IOException {
        super.onCompleted(data);
        // proceed
        if (data instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) data;
            HttpMatcher rm = req.getMatcher();
            String[] pis = rm.getPathItems();
            String action = pis[pis.length - 1];
            OAuthClient oauth = (pis.length > 2) ? oauths.get(pis[pis.length - 2]) : oauths.get(null);

            if (authorize.equals(action)) {
                String redirectURL = initAuthRequest(req, oauth);
                if (redirectURL != null) {
                    this.do302(data, redirectURL);
                } else {
                    throw new IOException("Invalid authentication request:\n  action=" + action + "\n  oauth=" + ("" + oauth).replace("\n", "\n  ") + ", req=" + req.getQuery());
                }
            } else if (token.equals(action)) {
                if (checkAuthRequest(req, oauth)) {
                    URL redirectTo = initUserInfo(req, oauth);
                    if (redirectTo != null) {
                        this.do302(data, redirectTo.toString());
                    } else {
                        this.do200(data, "text/plain; charset=utf-8", ("OAuth " + oauth + " success").getBytes("UTF-8"));
                    }
                } else {
                    URL redirectTo = initUserInfo(req, oauth);
                    if (redirectTo != null) {
                        this.do302(data, redirectTo.toString());
                    } else {
                        this.do500(data, "text/plain; charset=utf-8", ("OAuth " + oauth + " failure").getBytes("UTF-8"));
                    }
                }
            } else if (logout.equals(action)) {
                URL url = revokeAuth(req, oauth);
                if (url != null) {
                    this.do302(data, url.toString());
                    //this.do200(data, "text/plain; charset=utf-8", ("OAuth " + oauth + " revoked").getBytes("UTF-8"));
                } else {
                    this.do500(data, "text/plain; charset=utf-8", ("OAuth " + oauth + " revoke failure: " + req).getBytes("UTF-8"));
                }
            } else {
                this.do500(data, "text/plain; charset=utf-8", ("OAuth " + oauth + " handling failure: " + req).getBytes("UTF-8"));
            }
        }
    }

    /**
     * Returns array of strings: {login URL, domain, title}
     *
     * @return
     */
    public List<String[]> getAuthLinks() {
        List<String[]> r = new ArrayList<>();
        HttpMatcher m = getMatcher();
        String path = m.getPath();
        if (path.endsWith("/*")) {
            path = path.substring(0, path.length() - 2);
        }
        for (String key : oauths.keySet()) {
            OAuthClient oac = oauths.get(key);
            try {
                OAuthContext test = oac.createContext(null);
                r.add(new String[]{
                    path + "/" + key + "/" + authorize,
                    (test != null) ? test.domain() : "none",
                    (test != null) ? test.title() : "undefined"
                });
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        return r;
    }

    /**
     * Prepare redirect URL and keep OAuth context in session...
     *
     * @param req
     * @return
     * @throws IOException
     */
    public String initAuthRequest(HttpRequest req, OAuthClient client) throws IOException {
        HttpSession sess = req.getHttpSession();
        OAuthContext oac = (OAuthContext) sess.getProperties().get("" + System.identityHashCode(OAuthHttpDataProcessor.class));
        if (oac != null) {
            if (oac != null) {
                try {
                    oac.revokeAuthToken();
                    sess.setUser(null);
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
            oac = null;
        }

        String redirectURL = req.getQuery();
        int idx = redirectURL.lastIndexOf("/" + authorize);
        if (idx != -1) {
            redirectURL = redirectURL.substring(0, idx + 1) + token;
        }
        redirectURL = req.getHostURL() + redirectURL;
        oac = client.createContext(redirectURL);
        {
            sess.getProperties().put("" + System.identityHashCode(OAuthHttpDataProcessor.class), oac);
            String referer = req.getHead().getHeader1(HttpData.HH_REFERER);
            if (referer != null) {
                oac.setProperty(HttpData.HH_REFERER, referer);
            }
        }
        sess.getProperties().put("" + System.identityHashCode(OAuthHttpDataProcessor.class), oac);

        return "" + oac.getAuthURL();
    }

    public boolean checkAuthRequest(HttpRequest req, OAuthClient client) throws IOException {
        HttpSession sess = req.getHttpSession();
        OAuthContext oac = (OAuthContext) sess.getProperties().get("" + System.identityHashCode(OAuthHttpDataProcessor.class));

        Map<String, Object> params = new LinkedHashMap<>();
        HttpMatcher rm = req.getMatcher();
        params.putAll(rm.getParameters(rm, false));
        if (req.canHaveFormParameters()) {
            params.putAll(req.getFormParameters(null));
        }
        String code = (String) params.get("code");

        if (oac != null && oac.checkAuthData(params)) {
            String token = oac.getAuthToken(code, params);
            return token != null;
        } else {
            return false;
        }
    }

    public URL initUserInfo(HttpRequest req, OAuthClient client) throws IOException {
        HttpSession sess = req.getHttpSession();
        OAuthContext oac = (OAuthContext) sess.getProperties().get("" + System.identityHashCode(OAuthHttpDataProcessor.class));
        final OAuthUserInfo oau = oac.getOAuthUserInfo();
        if (oau == null) {
        } else {
            HttpUser user = null;

            if (auth != null) {
                user = auth.authenticate(null, oac);
            } else {
                user = new HttpUser(oac.domain(), oau.id(), oau.name(), new RAT());
                user.getProperties().put(HttpUser.P_AUTH_TYPE, HttpUser.AUTH_TYPE.oauth);
            }

            if (user != null) {
                //user.getProperties().put("oauth", oau);
                if (!user.getProperties().containsKey("email") && oau.email() != null) {
                    user.getProperties().put("email", oau.email().toLowerCase());
                }
            }

            sess.setUser(user);

            // redirect to referer -> assuming that was initial non-authenticated user request target
            try {
                if (oac.getProperty(HttpData.HH_REFERER) != null) {
                    String url = oac.getProperty(HttpData.HH_REFERER);
                    return new URL(url);
                }
            } catch (IOException ioex) {
            }

            HttpApplication app = sess.getApplication();
            if (app != null) {
                app.onAuhtenticatedUser(sess, user);
                String path = app.getDefaultPage(req);
                if (path != null) {
                    String url = req.getHostURL();
                    if (!(url.endsWith("/") || path.startsWith("/"))) {
                        url += "/";
                    }
                    url += path;
                    return new URL(url);
                }
            }
        }
        return null;
    }

    public URL revokeAuth(HttpRequest req, OAuthClient client) throws IOException {
        HttpSession sess = req.getHttpSession();
        OAuthContext oac = (OAuthContext) sess.getProperties().get("" + System.identityHashCode(OAuthHttpDataProcessor.class));
        if (oac != null && oac.revokeAuthToken()) {
            HttpApplication app = sess.getApplication();
            if (app != null) {
                String path = app.getDefaultPage(req);
                sess.setUser(null);
                if (path != null) {
                    String url = req.getHostURL();
                    if (!(url.endsWith("/") || path.startsWith("/"))) {
                        url += "/";
                    }
                    url += path;
                    return new URL(url);
                }
            }
        }
        return null;
    }
}
