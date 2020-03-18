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
package ssg.lib.http;

import ssg.lib.http.base.HttpContext;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.http.base.HttpResponse;

/**
 *
 * @author 000ssg
 */
public class HttpSession implements HttpContext {

    static DateFormat tf = new SimpleDateFormat("HH:mm:ss.SSS") {
        {
            setTimeZone(TimeZone.getTimeZone("UTC"));
        }
    };

    UUID id = UUID.randomUUID();
    long expiresAt;
    private HttpUser user;
    boolean closed = false;
    String serviceRoot;
    HttpApplication application;
    String revalidateUser;
    private boolean secure = false;

    Map<String, Object> properties = new LinkedHashMap<>();
    Map<String, HttpCookie> cookies = new LinkedHashMap<>();
    private Locale locale;

    public HttpSession(String serviceRoot) {
        this.serviceRoot = serviceRoot;
    }

    public HttpSession(String serviceRoot, HttpApplication app) {
        this.serviceRoot = serviceRoot;
        application = app;
    }

    public UUID getId() {
        return id;
    }

    public void onClose() {
        closed = true;
    }

    public void touch(long lifetime) {
        expiresAt = System.currentTimeMillis() + lifetime;
    }

    public boolean isValid() {
        return System.currentTimeMillis() < expiresAt;
    }

    public void invalidate() {
        expiresAt = System.currentTimeMillis() - 1;
    }

    /**
     * Basic re-authentication support: set auth text to allow 401 basic
     * authentication response.
     *
     * @param revalidate
     */
    public void setRevalidateUser(String revalidate) {
        revalidateUser = revalidate;
    }

    public String getRevalidateUser() {
        return revalidateUser;
    }

    public String getBaseURL() {
        String r = null;
        if (application == null) {
            r = serviceRoot;
        } else {
            String appRoot = application.getRoot();
            if (serviceRoot.endsWith("/") || appRoot.startsWith("/")) {
                r = serviceRoot + appRoot;
            } else {
                r = serviceRoot + "/" + appRoot;
            }
        }
        return r != null && !r.endsWith("/") ? r + "/" : r;
    }

    public HttpApplication getApplication() {
        return application;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append('{');
        sb.append("id=" + id);
        if (serviceRoot != null) {
            sb.append(", service=" + serviceRoot);
        }
        if (secure) {
            sb.append(", secure");
        }
        if (application != null) {
            sb.append(", app=" + application);
        }
        sb.append(((isValid()) ? ", expires in=" + tf.format(new Date(expiresAt - System.currentTimeMillis())) : ", invalid"));
        if (user != null) {
            sb.append("\n  user=");
            sb.append(user.toString().replace("\n", "\n  "));
            sb.append('\n');
        }
        if (properties != null) {
            sb.append("\n  properties[" + properties.size() + "]:");
            for (String pn : properties.keySet()) {
                sb.append("\n    " + pn + ": " + ("" + properties.get(pn)).replace("\n", "\n      "));
            }
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public RAT getRAT() {
        return (user != null) ? user.rat : null;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public Map<String, HttpCookie> getCookies() {
        return cookies;
    }

    public HttpUser getUser() {
        return user;
    }

    public void setUser(HttpUser user) {
        if (getApplication() != null) {
            user = getApplication().onAuhtenticatedUser(this, user);
        }
        this.user = user;
    }

    public static Map<String, HttpCookie> getCookies(HttpRequest req) {
        Map<String, HttpCookie> r = new LinkedHashMap<>();
        String[] sCookies = req.getHead().getHeader(HttpData.HH_COOKIE);
        if (sCookies != null) {
            for (String sCookie : sCookies) {
                String[] ss = sCookie.split(";");
                for (String s : ss) {
                    s=s.trim();
                    HttpCookie cookie = new HttpCookie(s);
                    r.put(cookie.name, cookie);
                }
            }
        }
        return r;
    }

    public static Map<String, HttpCookie> getSetCookies(HttpResponse resp) {
        Map<String, HttpCookie> r = new LinkedHashMap<>();
        String[] sCookies = resp.getHead().getHeader(HttpData.HH_SET_COOKIE);
        if (sCookies != null) {
            for (String sCookie : sCookies) {
                HttpCookie cookie = new HttpCookie(sCookie);
                r.put(cookie.name, cookie);
            }
        }
        return r;
    }

    /**
     * @return the locale
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * @param locale the locale to set
     */
    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    /**
     * @return the secure
     */
    public boolean isSecure() {
        return secure;
    }

    /**
     * @param secure the secure to set
     */
    public void setSecure(boolean secure) {
        this.secure = secure;
    }
}
