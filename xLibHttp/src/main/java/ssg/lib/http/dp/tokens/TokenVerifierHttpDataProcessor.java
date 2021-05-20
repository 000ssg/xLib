/*
 * The MIT License
 *
 * Copyright 2021 000ssg.
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
package ssg.lib.http.dp.tokens;

import java.io.IOException;
import java.nio.channels.Channel;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.di.DI;
import ssg.lib.http.HttpDataProcessor;
import ssg.lib.http.HttpSession;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.http.base.HttpResponse;
import ssg.lib.http.dp.tokens.TokenVerifier.Token;

/**
 * Token verification service:
 *
 * use "/'root'/verify" to verify token (NOTE: avoid authentication for given
 * path, use "App-Secret" to allow explicit validation. If no secret or it is ""
 * - optional default token verifier may be used)
 *
 * @author 000ssg
 */
public class TokenVerifierHttpDataProcessor extends HttpDataProcessor {

    // HTTP header names
    public static String HEADER_SECRET = "App-Secret";

    // Token validation fields
    public static String TOKEN_VALID = "valid"; // boolean
    public static String TOKEN_EXPIRES_IN = "expires_in"; // int, seconds
    public static String TOKEN_USER_DOMAIN = "user_domain";
    public static String TOKEN_USER_ID = "user_id";
    public static String TOKEN_USER_NAME = "user_name";
    public static String TOKEN_USER_ROLES = "user_roles";

    Map<String, TokenVerifier> verifiers = new HashMap<>();

    public TokenVerifierHttpDataProcessor(String root) {
        super(root);
    }

    public TokenVerifierHttpDataProcessor(String root, TokenVerifier verifier) {
        super(root);
        verifiers.put("", verifier);
    }

    public TokenVerifierHttpDataProcessor(String root, String secret, TokenVerifier verifier) {
        super(root);
        verifiers.put(secret, verifier);
    }

    public TokenVerifierHttpDataProcessor configure(String secret, TokenVerifier verifier) {
        if (secret != null && verifier != null) {
            verifiers.put(secret, verifier);
        }
        return this;
    }

    @Override
    public void onHeaderLoaded(HttpData data) throws IOException {
        super.onHeaderLoaded(data);
        //System.out.println(""+getClass().getName()+".onHeaderLoaded: "+data.toString().replace("\n", "\n  "));
    }

    @Override
    public void onCompleted(HttpData data) throws IOException {
        super.onCompleted(data);

        try {
            HttpRequest req = (HttpRequest) data;
            HttpSession session = req.getHttpSession();

            //System.out.println(""+getClass().getName()+".onCompleted: "+data.toString().replace("\n", "\n  "));
            
            String[] path = data.getMatcher().getPathItems();
            if (path == null || path.length < 2) {
                this.do500(data, "text/html; encoding=utf-8", ("<html><body>" + "Invalid request path: " + data.getHead().getProtocolInfo()[1] + "</body></html>").getBytes("UTF-8"));
            } else if ("verify".equals(path[path.length - 1])) {
                String s = req.getHead().getHeader1("Authorization");
                TokenVerifier tv = (TokenVerifier) verifiers.get("");
                if (!verifiers.isEmpty()) {
                    String secret = req.getHead().getHeader1(HEADER_SECRET);
                    if (secret != null && verifiers.get(secret) instanceof TokenVerifier) {
                        tv = (TokenVerifier) verifiers.get(secret);
                    }
                }
                if (s.startsWith("Bearer ") && tv != null) {
                    s = s.substring(s.indexOf(" ") + 1);
                    Token t = tv.canVerify(s) ? tv.get(s) : null;
                    if (t != null) {
                        this.do200(data, "x-application/json; encoding=utf-8", (""
                                + "{"
                                + "\"" + TOKEN_VALID + "\":true"
                                + ", \"" + TOKEN_EXPIRES_IN + "\":" + (t.getExp() - System.currentTimeMillis() / 1000)
                                + (t.getId() != null ? ", \"" + TOKEN_USER_ID + "\":\"" + t.getId() + "\"" : "")
                                + (t.getName() != null ? ", \"" + TOKEN_USER_NAME + "\":\"" + t.getName() + "\"" : "")
                                + (t.getRoles() != null ? ", \"" + TOKEN_USER_ROLES + "\":\"" + t.getRoles() + "\"" : "")
                                + "}"
                                + "").getBytes("UTF-8")
                        );
                    } else {
                        this.do500(data, "x-application/json; encoding=utf-8", ("{\"" + TOKEN_VALID + "\":false}").getBytes("UTF-8"));
                    }
                } else {
                    this.do500(data, "x-application/json; encoding=utf-8", ("{\"" + TOKEN_VALID + "\":false}").getBytes("UTF-8"));
                }
            } else if ("authenticate".equals(path[path.length - 1])) {
                boolean done = false;
                for (TokenVerifier tv : verifiers.values()) {
                    if (tv instanceof TokenAuthenticator) {
                        TokenAuthenticator ta = (TokenAuthenticator) tv;
                        if (ta.canAuthenticate(req)) {
                            String s = ta.doAuthentication(req);
                            Token t = (s != null) ? tv.get(s) : null;
                            if (t != null) {
                                HttpResponse resp = req.getResponse();
                                resp.addHeader(ta.authHeaderName(), s);
                                this.do200(data, null, null);
                                done = true;
                            }
                        }
                    }
                }
                if (!done) {
                    this.do500(data, "x-application/json; encoding=utf-8", ("{\"" + TOKEN_VALID + "\":false}").getBytes("UTF-8"));
                }
            }
        } catch (IOException ioex) {
            throw ioex;
        } catch (Throwable th) {
            throw new IOException(th);
        }
    }

    @Override
    public void onDeassigned(Channel p, DI di) {
        super.onDeassigned(p, di);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append('{');
        sb.append("verifiers=" + verifiers.size());
        for (Entry<String, TokenVerifier> e : verifiers.entrySet()) {
            sb.append("\n  " + TokenVerifier.secret2text(e.getKey()) + ": " + e.getValue().toString().replace("\n", "\n    "));
        }
        sb.append('\n');
        sb.append('}');
        return sb.toString();
    }

}
