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
package ssg.lib.httpapi_cs;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import ssg.lib.api.API_Publisher;
import ssg.lib.api.util.Reflective_API_Builder;
import ssg.lib.api.util.Reflective_API_Builder.Reflective_API_Context;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.HttpAuthenticator;
import ssg.lib.http.HttpAuthenticator.Domain;
import ssg.lib.http.RAT;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.http.dp.tokens.APKTokenVerifier;
import ssg.lib.http.dp.tokens.JWTTokenVerifier;
import ssg.lib.http.dp.tokens.TokenUserVerifier;
import ssg.lib.http.dp.tokens.TokenVerifier;
import ssg.lib.http.dp.tokens.TokenVerifierHttpDataProcessor;
import ssg.lib.http.rest.StubVirtualData;
import ssg.lib.http.rest.annotations.XMethod;
import ssg.lib.http.rest.annotations.XType;
import ssg.lib.http_cs.HttpCaller;
import ssg.lib.service.Repository;

/**
 *
 * @author 000ssg
 */
public class Test_APIOrchestra {

    APIRunner authenticator;
    APIRunner service;

    @XType
    public static class Service {

        Map<String, List<String>> items = new LinkedHashMap<>();

        @XMethod
        public String getVersion() {
            return "1.2.0.1";
        }

        @XMethod(roles = {"admin"})
        public String getExtendedVersion() {
            return getVersion() + ".002";
        }

        @XMethod(roles = {"admin", "user"})
        public List<String> getItems(HttpRequest req, String prefix, String suffix) {
            List<String> r = new ArrayList<>();
            String user = user(req);
            if (user != null && items.containsKey(user)) {
                for (String s : items.get(user)) {
                    if ((prefix == null || s.startsWith(prefix)) && (suffix == null || s.endsWith(suffix))) {
                        r.add(s);
                    }
                }
            }
            return r;
        }

        @XMethod(roles = {"admin", "user"})
        public int addItems(HttpRequest req, String... items) {
            int r = 0;
            String user = user(req);
            if (user != null) {
                List<String> rr = null;
                synchronized (this.items) {
                    rr = this.items.get(user);
                    if (rr == null) {
                        rr = new ArrayList<>();
                        this.items.put(user, rr);
                    }
                }
                if (items != null && items.length > 0)
                synchronized (rr) {
                    for (String s : items) {
                        if (rr.contains(s)) {
                            continue;
                        }
                        rr.add(s);
                        r++;
                    }
                }
            }
            return r;
        }

        String user(HttpRequest req) {
            return req != null && req.getHttpSession() != null && req.getHttpSession().getUser() != null ? req.getHttpSession().getUser().getId() : null;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(getClass().getName());
            sb.append('{');
            sb.append("version=" + getVersion());
            sb.append(", ext.version=" + getExtendedVersion());
            sb.append(", items[" + items.size() + "]:");
            for (Entry<String, List<String>> e : items.entrySet()) {
                sb.append("\n  " + e.getKey() + "[" + e.getValue().size() + "]: " + e.getValue());
            }
            sb.append('\n');
            sb.append('}');
            return sb.toString();
        }
    }

    public void start() {
        if (authenticator != null) try {
            authenticator.start();
        } catch (Throwable th) {
            th.printStackTrace();
        }
        if (service != null) try {
            service.start();
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public void stop() {
        if (authenticator != null) try {
            authenticator.stop();
        } catch (Throwable th) {
            th.printStackTrace();
        }
        if (service != null) try {
            service.stop();
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public static void main(String... args) throws Exception {
        Test_APIOrchestra all = new Test_APIOrchestra();
        HttpCaller caller = new HttpCaller();

        int jwtPort = 30040;
        String jwtRoot = "/common"; // HTTP application root
        String tokenAuth = "token"; // in-app tokens (APK + ...) authentication interface
        String jwtSecret = "secretForJWTclients"; // secret for use by trusted clients
        String apkSecret = "secretForAPKclients"; // secret for use by trusted clients
        String apiSecret = "secretForAPIclients"; // secret for use by trusted clients

        // HTTP authenticator: 3 users with different set of roles
        HttpAuthenticator.Domain domain = new HttpAuthenticator.Domain(jwtRoot)
                .configureUser("aaa", "bbb", null, new RAT().roles("admin", "jwt"))
                .configureUser("bbb", "bbb", null, new RAT().roles("user", "jwt"))
                .configureUser("ccc", "bbb", null, new RAT().roles("jwt"));

        // JWT tokens creator/validator: dynamic (maps to HTTP-authenticated users in domain)
        TokenVerifier jwt = new JWTTokenVerifier().configureStore(new TokenVerifier.TokenStoreRAM());
        domain.getUserStore().verifiers().add(new TokenUserVerifier(jwt));

        // alternative APK tokens validator: created 3 built-in tokens
        TokenVerifier atv = new APKTokenVerifier()
                .configureStore(new TokenVerifier.TokenStoreRAM());
        caller.registerToken(atv, "apk-a", "apk", "a", "admin,apk", null);
        caller.registerToken(atv, "apk-b", "apk", "b", "user,apk", null);
        caller.registerToken(atv, "apk-c", "apk", "c", "apk", null);
        domain.getUserStore().verifiers().add(new TokenUserVerifier(atv));

        // alternative api-key: created 3 built-in tokens
        TokenVerifier apitv = new TokenVerifier() {
            @Override
            public boolean canVerify(String id) {
                return id != null && id.startsWith("api-key-");
            }

            @Override
            public String createTokenId() {
                return "api-key-" + super.createTokenId();
            }

            @Override
            public String type() {
                return "api";
            }
        }
                .configureStore(new TokenVerifier.TokenStoreRAM());
        caller.registerToken(apitv, "api-a", "api", "a", "admin,api", null);
        caller.registerToken(apitv, "api-b", "api", "b", "user,api", null);
        caller.registerToken(apitv, "api-c", "api", "c", "api", null);
        domain.getUserStore().verifiers().add(new TokenUserVerifier(apitv));

        // Http application named "JWT" with root "/common" 
        // Added JWT verification "/common/jwt" with no-auth path "/common/jwt/verify" to allow secret-aware clients to verify token.
        all.authenticator = new APIRunner(
                domain,
                new HttpApplication("JWT", jwtRoot)
                        .configureDataProcessors(new Repository())
                        .configureDataProcessor(
                                1,
                                new TokenVerifierHttpDataProcessor(tokenAuth + "/")
                                        .configure(jwtSecret, jwt)
                                        .configure(apkSecret, atv)
                                        .configure(apiSecret, apitv)
                        )
                        .configureBasicAuthentication(true)
                        .configureNoAuthPaths(
                                tokenAuth + "/verify")
        )
                .configureHttp(jwtPort);

        ////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////
        int servicePort = 30042;
        String serviceRoot = "/service";
        String serviceREST = "methods";
        Domain serviceDomain = new Domain(serviceRoot);
        serviceDomain.getUserStore().verifiers().add(new TokenUserVerifier(
                new URL("http://localhost:" + jwtPort + jwtRoot + "/" + tokenAuth + "/verify"),
                jwtSecret,
                id -> {
                    return id != null && id.split("\\.").length==3;
                }
        ));
        serviceDomain.getUserStore().verifiers().add(new TokenUserVerifier(
                new URL("http://localhost:" + jwtPort + jwtRoot + "/" + tokenAuth + "/verify"),
                apkSecret,
                id -> {
                    return id != null && id.startsWith("apk.");
                }
        ));
        serviceDomain.getUserStore().verifiers().add(new TokenUserVerifier(
                new URL("http://localhost:" + jwtPort + jwtRoot + "/" + tokenAuth + "/verify"),
                apiSecret,
                id -> {
                    return id != null && id.startsWith("api-key-");
                }
        ));

        Service service = new Service();
        all.service = new APIRunner(new HttpApplication("Service", serviceRoot)
                .configureDataProcessors(new Repository())
        )
                .configureHttp(servicePort)
                .configureREST(serviceREST);
        all.service.getService().configureAuthentication(serviceDomain);
        all.service.getREST().registerProviders(null, service);
        all.service.configureAPI("demo", "a", new API_Publisher()
                .configure(Reflective_API_Builder.buildAPI(
                        "a",
                        new Reflective_API_Context()
                                .configure(new API_MethodsProvider_AccessHelper()),
                        service.getClass())
                )
                .configureContext(service)
        );

        String script_path = null;
        {
            APIRunner.APIGroup ag = (APIRunner.APIGroup) ((Map) all.service.getAPIGroups().get("demo")).values().iterator().next();
            String router_root = all.service.getApp() != null ? all.service.getApp().getRoot() + "/" : "/";
            String rest_root = all.service.getApp() != null ? all.service.getApp().getRoot() + "/" + serviceREST : "/" + serviceREST;
            all.service.configureStub(new StubVirtualData(router_root.substring(0, router_root.length() - 1), rest_root, ag.apis.getAPI("a"), "js", "jw")
                    .configure(new StubAPIContext(null, null, true))
                    .configure("a", "js", "jw"));
            script_path = "http://localhost:" + servicePort + serviceRoot + "/a/script.js";
        }

        try {
            all.start();
            System.out.println("-------------------------------------- Servers"
                    + "\n-- " + all.authenticator.toString().replace("\n", "\n-- ")
                    + "\n----------"
                    + "\n-- " + all.service.toString().replace("\n", "\n-- ")
                    + "\n--------------------------------------\n"
            );
            System.out.println("-------------------------------------- Services"
                    + "\n  " + service.toString().replace("\n", "\n  ")
            );

            // do test HTTP calls
            URI authURI = new URI("http://localhost:" + jwtPort + jwtRoot + "/" + tokenAuth + "/authenticate");
            URI verifyURI = new URI("http://localhost:" + jwtPort + jwtRoot + "/" + tokenAuth + "/verify");
//            URI restURI = new URI("http://localhost:" + jwtPort + jwtRoot + "/" + jwtREST);
            URI serviceURI = new URI("http://localhost:" + servicePort + serviceRoot + "/" + serviceREST);

            System.out.println("-------------------------------------- URLs"
                    + "\n-- JWT    authURI: " + authURI
                    + "\n-- JWT  verifyURI: " + verifyURI
                    //                    + "\n-- JWT   REST URI: " + restURI
                    + "\n-- SVC serviceURI: " + serviceURI
                    + "\n--------------------------------------"
            );

            // prepare HTTP contexts
            // get login tokens for enumerated users
            // pairs [user, GET path]. For unknown user ("aa") no token!
            caller.requestJwtTokens(
                    authURI,
                    true,
                    "aa:aaaaa", "aaa:bbb", "bbb:bbb", "ccc:bbb"
            );

            // try service calls with different user tokens
            {
                final AtomicInteger counter = new AtomicInteger();
                for (String[] ss : new String[][]{
                    {"addItems", "items", "ab{c}a"},
                    {"addItems", "items", "ba{c}b"},
                    {"items"},
                    {"version"},
                    {"extendedVersion"},
                    {"a/a.Service.addItems", "items", "ab{c}ba"},
                    {"a/a.Service.addItems", "items", "ba{c}ab"},
                    {"a/a.Service.getItems"},
                    {"a/a.Service.getVersion"},
                    {"a/a.Service.getExtendedVersion"},
                    {script_path}
                }) {
                    if (ss == null || ss.length < 1 || ss[0] == null) {
                        continue;
                    }
                    String uri = ss[0].contains("://") ? ss[0] : HttpCaller.uriWithParameters(serviceURI + (ss[0].startsWith("/") ? "" : "/") + ss[0], 1, ss);
                    caller.doCallAllTokens(
                            uri,
                            false,
                            null,
                            null,
                            (svc, objs) -> {
                                if (svc.contains("%7Bc%7D")) {
                                    svc = svc.replace("%7Bc%7D", "_" + counter.incrementAndGet() + "_");
//                                } else if (svc.contains("{c}")) {
//                                    svc = svc.replace("{c}", "_" + counter.incrementAndGet() + "_");
                                }
                                return svc;
                            },
                            null);
                }
            }
        } finally {
            System.out.println("-------------------------------------- Services"
                    + "\n  " + service.toString().replace("\n", "\n  ")
            );

            all.stop();
            System.exit(0);
        }

    }
}
