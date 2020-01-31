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

import ssg.lib.http.base.HttpData;

/**
 *
 * @author 000ssg
 */
public class HttpCookie {

    public static final int Secure = 0x0001;
    public static final int HttpOnly = 0x0002;
    public static final int SameSite = 0x0004;
    String name;
    String value;
    String domain;
    String path;
    Long expires;
    int flags;

    public HttpCookie(String s) {
        String[] ss = s.split(";");
        for (String sc : ss) {
            int idx = sc.indexOf("=");
            String n = (idx != -1) ? sc.substring(0, idx).trim() : sc.toLowerCase();
            String v = (idx != -1) ? sc.substring(idx + 1).trim() : null;
            if ("Secure".equalsIgnoreCase(sc)) {
                flags = Secure;
            } else if ("HttpOnly".equalsIgnoreCase(sc)) {
                flags = HttpOnly;
            } else if ("SameSite".equalsIgnoreCase(sc)) {
                flags = SameSite;
            } else if ("Expires".equalsIgnoreCase(sc)) {
                expires = HttpData.fromHeaderDatetime(v);
            } else if (name == null && v != null) {
                name = n;
                value = v;
            } else if ("Domain".equalsIgnoreCase(n)) {
                domain = v;
            } else if ("Path".equalsIgnoreCase(n)) {
                path = v;
            } else {
                onUnknown(n, v);
            }
        }
    }

    public HttpCookie(String name, String value, String domain, String path, Long expires, int flags) {
        this.name = name;
        this.value = value;
        this.domain = domain;
        this.path = path;
        this.expires = expires;
        this.flags = flags;
    }

    public void onUnknown(String n, String v) {
    }

    public String toSetString() {
        StringBuilder sb = new StringBuilder();
        sb.append(toGetString());
        if (expires != null) {
            sb.append("; Expires=" + HttpData.toHeaderDatetime(expires));
        }
        if (domain != null) {
            sb.append("; Domain=" + domain);
        }
        if (path != null) {
            sb.append("; Path=" + path);
        }
        if (isSecure()) {
            sb.append("; Secure");
        }
        if (isHttpOnly()) {
            sb.append("; HttpOnly");
        }
        if (isSameSite()) {
            sb.append("; SameSite");
        }
        return sb.toString();
    }

    public String toGetString() {
        return name + "=" + value;
    }

    public boolean isSecure() {
        return (flags & Secure) == Secure;
    }

    public boolean isHttpOnly() {
        return (flags & HttpOnly) == HttpOnly;
    }

    public boolean isSameSite() {
        return (flags & SameSite) == SameSite;
    }

    public boolean isValid() {
        return expires == null || System.currentTimeMillis() <= expires;
    }

}
