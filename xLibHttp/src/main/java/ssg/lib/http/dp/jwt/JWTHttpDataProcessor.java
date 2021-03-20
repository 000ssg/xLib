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
package ssg.lib.http.dp.jwt;

import java.io.IOException;
import java.util.List;
import ssg.lib.http.HttpDataProcessor;
import ssg.lib.http.HttpSession;
import ssg.lib.http.HttpUser;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.http.base.HttpResponse;
import ssg.lib.http.dp.jwt.JWT.Token;

/**
 * Sample JWT service: use "/'root'/[authenticate|verify]" to request token and
 * to verify token.
 *
 * NOTE: by default returns token for logged in user.
 *
 * @author 000ssg
 */
public class JWTHttpDataProcessor extends HttpDataProcessor {

    JWT jwt;
    String jwtSecret;

    public JWTHttpDataProcessor(String root) {
        super(root);
    }

    public JWTHttpDataProcessor(String root, String secret) {
        super(root);
    }

    @Override
    public void onCompleted(HttpData data) throws IOException {
        super.onCompleted(data);

        try {
            HttpRequest req = (HttpRequest) data;
            HttpSession session = req.getHttpSession();

            String[] path = data.getMatcher().getPathItems();
            if (path == null || path.length < 2) {
                this.do500(data, "text/html; encoding=utf-8", ("<html><body>" + "Invalid request path: " + data.getHead().getProtocolInfo()[1] + "</body></html>").getBytes("UTF-8"));
            } else if ("verify".equals(path[path.length - 1])) {
                String s = req.getHead().getHeader1("Authorization");
                if (s.startsWith("Bearing ")) {
                    s = s.substring(s.indexOf(" ") + 1);
                    Token t = jwt.get(s);
                    if (t != null) {
                        this.do200(data, "x-application/json; encoding=utf-8", ("{valid:true, expires_in:" + (t.exp - System.currentTimeMillis() / 1000) + "}").getBytes("UTF-8"));
                    } else {
                        this.do500(data, "x-application/json; encoding=utf-8", ("{valid:false}").getBytes("UTF-8"));
                    }
                } else {
                    this.do500(data, "x-application/json; encoding=utf-8", ("{valid:false}").getBytes("UTF-8"));
                }
            } else if ("authenticate".equals(path[path.length - 1])) {
                String s = doAuthenticate(req, jwt);
                Token t = jwt.get(s);
                if (t != null) {
                    HttpResponse resp = req.getResponse();
                    resp.addHeader("JWT-Token", s);
                    this.do200(data, null, null);
                } else {
                    this.do500(data, "x-application/json; encoding=utf-8", ("{valid:false}").getBytes("UTF-8"));
                }
            }
        } catch (IOException ioex) {
            throw ioex;
        } catch (Throwable th) {
            throw new IOException(th);
        }
    }

    public String doAuthenticate(HttpRequest req, JWT jwt) throws IOException {
        HttpSession session = req.getHttpSession();
        HttpUser user = session.getUser();
        if (user != null && !"jwt".equals(user.getProperties().get("type"))) {
            Token t = jwt.findJWT(user.getId(), user.getName());
            if (t != null && t.id.equals(user.getId())) {
                switch (t.verify()) {
                    case valid:
                        return t.toJWT();
                    default:
                        t = null;
                }
            } else {
                t = null;
            }
            if (t == null) {
                List<String> roles = user.getRoles();
                StringBuilder rls = new StringBuilder();
                if (roles != null && !roles.isEmpty()) {
                    for (String rl : roles) {
                        if (rl == null || rl.isEmpty()) {
                            continue;
                        }
                        if (rls.length() > 0) {
                            rls.append(',');
                        }
                        rls.append(rl);
                    }
                }

                t = jwt.createJWT(user.getId(), user.getName(), rls.length() > 0 ? rls.toString() : null, null);
                return t != null ? t.toJWT() : null;
            }
        }
        return null;
    }
}
