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
package ssg.lib.wamphttpapi_cs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.common.Config;
import ssg.lib.http.HttpAuthenticator;
import ssg.lib.http.dp.tokens.TokenUserVerifier;
import ssg.lib.http_cs.AuthAdapter;
import ssg.lib.http_cs.AuthAdapter.AuthAdapterConf;
import ssg.lib.wamp.auth.WAMPAuthProvider;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ID;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_METHOD;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_PROVIDER;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ROLE;
import ssg.lib.wamp.auth.impl.WAMPAuthAny;
import ssg.lib.wamp.auth.impl.WAMPAuthCRA;
import ssg.lib.wamp.auth.impl.WAMPAuthTicket;

/**
 *
 * @author 000ssg
 */
public class WAMPAdapter {

    public WAMPAdapterConf createWAMPAdapterConf(String text) {
        if (text.startsWith("{") || text.startsWith("[")) {
            return new WAMPAdapterConf(text);
        } else {
            return new WAMPAdapterConf(text.split(";"));
        }
    }

    public WAMPAuthProvider createAuthProviders(WAMPAdapterConf conf, AuthAdapter authAdapter) throws IOException {
        WAMPAuthProvider result = null;

        if (conf != null) {
            if ("any".equals(conf.type)) {
                result = new WAMPAuthAny();
            } else if ("wampcra".equalsIgnoreCase(conf.type)) {
                result = new WAMPAuthCRA(conf.secret);
                if (conf.role != null) {
                    for (String s : conf.role) {
                        String[] ss = s.split("=");
                        if (ss.length > 1) {
                            ((WAMPAuthCRA) result).configureRole(ss[0].trim(), ss[1].trim());
                        }
                    }
                }
            } else if ("ticket".equalsIgnoreCase(conf.type)) {
                if (conf.tokenDelegate != null && conf.tokenDelegate.length > 0) {
                    final List<TokenUserVerifier> tvs = new ArrayList<>();
                    for (String tvi : conf.tokenDelegate) {
                        AuthAdapterConf aconf = authAdapter.createAuthadapterConf(tvi);
                        if (aconf != null) {
                            TokenUserVerifier tuv = authAdapter.createUserVerifier(aconf);
                            if (tuv != null) {
                                tvs.add(tuv);
                            }
                        }
                    }
                    result = new WAMPAuthTicket(conf.secret, (session, authid, ticket) -> {
                        for (TokenUserVerifier tv : tvs) {
                            if (tv.canVerify("Bearer " + authid)) try {
                                HttpAuthenticator.VerificationResult vr = tv.verify("Bearer " + authid);
                                if (vr != null) {
                                    Map<String, Object> r = new LinkedHashMap<>();
                                    r.put(K_AUTH_METHOD, "ticket");
                                    r.put(K_AUTH_ID, vr.userId);
                                    r.put(K_AUTH_PROVIDER, vr.userDomain != null ? vr.userDomain : "http");
                                    if (vr.userRoles != null && vr.userRoles.length > 0) {
                                        r.put(K_AUTH_ROLE, vr.userRoles[0]);
                                    } else {
                                        r.put(K_AUTH_ROLE, "guest");
                                    }
                                    return r;
                                }
                            } catch (IOException ioex) {
                                ioex.printStackTrace();
                            }
                        }
                        return null;
                    });
                }
            }
        }

        return result;
    }

    public static class WAMPAdapterConf extends Config {

        public WAMPAdapterConf() {
            super("");
        }

        public WAMPAdapterConf(String... args) {
            super("");
        }

        @Description("WAMP authentication type: any, wampcra, ticket")
        public String type;
        @Description("WAMP shared secret for wampcra/ticket authentication")
        public String secret;
        @Description("Ticket delegated verifier (for use with AuthAdapter)")
        public String[] tokenDelegate;
        @Description(value = "User roles (for WAMPCRA type)", pattern = "authid=role")
        public String[] role;
    }
}
