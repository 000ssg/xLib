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
package ssg.lib.http;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.base.HttpRequest;

/**
 *
 * @author sesidoro
 */
public class HttpSessionManager {

    public static boolean DUMP = false;

    Map<String, HttpSession> sessions = new LinkedHashMap<>();

    public synchronized HttpSession create(String path, HttpRequest req, List<HttpApplication> apps, String sessionIdCookie) throws IOException {
        HttpSession sess = new HttpSession(path);
//        if (req.isSecure()) {
//            sess.setSecure(true);
//        }

        if (apps != null && !apps.isEmpty()) {
            String q = req.getQuery();
            HttpApplication prefApp = null;
            for (HttpApplication app : apps) {
                if (q.startsWith(app.getRoot())) {
                    if (prefApp == null || prefApp.getRoot().length() < app.getRoot().length()) {
                        prefApp = app;
                    }
                }
            }
            sess.application = prefApp;
            String appRoot = sess.application.getRoot();
            path = sess.application.getRoot();
//            path += (path.endsWith("/") || appRoot.startsWith("/"))
//                    ? (path.endsWith("/") && appRoot.startsWith("/"))
//                    ? appRoot.substring(1)
//                    : appRoot
//                    : "/" + appRoot;
            if (!path.endsWith("/")) {
                path += "/";
            }
            sess.setLocale(Locale.forLanguageTag(sess.application.chooseLocale(req)));
        } else if (req.getHead().getHeader1(HttpData.HH_ACCEPT_LANGUAGE) != null) {
            String[] ll = req.getHead().getHeader1(HttpData.HH_ACCEPT_LANGUAGE).split(";");
            String l = ll[0];
            sess.setLocale(Locale.forLanguageTag(l.contains(",") ? l.split(",")[0] : l));
        }
        sess.expiresAt = System.currentTimeMillis() + 1000 * 60 * 15;

        if (sess.getApplication() != null) {
            HttpCookie cookie = new HttpCookie(
                    sessionIdCookie,
                    "" + sess.id,
                    null,
                    path,
                    null, //sess.expiresAt,
                    HttpCookie.HttpOnly | (sess.isSecure() ? HttpCookie.Secure : 0) //HttpCookie.SameSite)
            );
            sess.getCookies().put("" + sess.id, cookie);
            req.getResponse().getHead().addHeader(HttpData.HH_SET_COOKIE, cookie.toSetString());
        }
        sessions.put("" + sess.getId(), sess);

        if (DUMP) {
            System.out.println("Session.CREATE(" + sess.getId() + ", " + path + ") " + (sess != null ? sess.getUser() != null ? "auth=OK" : "no auth" : "no session"));
        }

        return sess;
    }

    public synchronized HttpSession get(String id) {
        HttpSession r = sessions.get(id);
        if (DUMP) {
            System.out.println("Session.GET(" + id + ") " + (r != null ? r.getUser() != null ? "auth=OK" : "no auth" : "no session"));
        }
        return r;
    }

    public synchronized HttpSession remove(String id) {
        HttpSession r = sessions.remove(id);
        if (DUMP) {
            System.out.println("Session.REMOVE(" + id + ") " + (r != null ? r.getUser() != null ? "auth=OK" : "no auth" : "no session"));
        }
        return r;
    }
}
