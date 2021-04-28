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
package ssg.lib.wamphttpapi_cs;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import ssg.lib.api.APIAuthContext;
import ssg.lib.api.API_Publisher;
import ssg.lib.api.util.Reflective_API_Builder;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.HttpAuthenticator.Domain;
import ssg.lib.http.HttpAuthenticator.VerificationResult;
import ssg.lib.http.RAT;
import ssg.lib.http.dp.tokens.APKTokenVerifier;
import ssg.lib.http.dp.tokens.JWTTokenVerifier;
import ssg.lib.http.dp.tokens.TokenUserVerifier;
import ssg.lib.http.dp.tokens.TokenVerifier;
import ssg.lib.http.dp.tokens.TokenVerifier.TokenStoreRAM;
import ssg.lib.http.dp.tokens.TokenVerifierHttpDataProcessor;
import ssg.lib.http.rest.annotations.XMethod;
import ssg.lib.http.rest.annotations.XType;
import ssg.lib.http_cs.HttpCaller;
import ssg.lib.httpapi_cs.API_MethodsProvider_AccessHelper;
import ssg.lib.service.Repository;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPTransport;
import ssg.lib.wamp.WAMPTransport.WAMPTransportMessageListener;
import ssg.lib.wamp.auth.WAMPAuth;
import ssg.lib.wamp.auth.WAMPAuthProvider;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ID;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_METHOD;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_PROVIDER;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ROLE;
import ssg.lib.wamp.auth.impl.WAMPAuthAny;
import ssg.lib.wamp.auth.impl.WAMPAuthCRA;
import ssg.lib.wamp.auth.impl.WAMPAuthTicket;
import ssg.lib.wamp.features.WAMP_FP_Reflection;
import ssg.lib.wamp.features.WAMP_FP_SessionMetaAPI;
import ssg.lib.wamp.features.WAMP_FP_VirtualSession;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.nodes.WAMPClient;
import ssg.lib.wamp.nodes.WAMPNode;
import ssg.lib.wamp.util.WAMPTools;
import ssg.lib.wamp.util.WAMPTransportList;

/**
 *
 * @author 000ssg
 */
public class Test_WAMPOrchestra {

    public WAMPRunner authenticator;
    public WAMPRunner service;
    public List<WAMPRunner> sites = new ArrayList<>();

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
        for (WAMPRunner r : sites) {
            if (r != null) try {
                r.start();
            } catch (Throwable th) {
                th.printStackTrace();
            }
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
        for (WAMPRunner r : sites) {
            if (r != null) try {
                r.stop();
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

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
        public List<String> getItems(APIAuthContext auth, String prefix, String suffix) {
            List<String> r = new ArrayList<>();
            String user = user(auth);
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
        public int addItems(APIAuthContext auth, String... items) {
            int r = 0;
            String user = user(auth);
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

        String user(APIAuthContext auth) {
            return auth != null ? auth.id() : null;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(getClass().getName());
            sb.append('{');
            sb.append("version=" + getVersion());
            sb.append(", ext.version=" + getExtendedVersion());
            sb.append(", items[" + items.size() + "]:");
            for (Map.Entry<String, List<String>> e : items.entrySet()) {
                sb.append("\n  " + e.getKey() + "[" + e.getValue().size() + "]: " + e.getValue());
            }
            sb.append('\n');
            sb.append('}');
            return sb.toString();
        }
    }

    public static void main(String... args) throws Exception {
        WAMPNode.DUMP_ESTABLISH_CLOSE = false;
        REST_WAMP_MethodsProvider.DEBUG = false;
        CustomWAMPRealmFactory.DEBUG = false;
        WAMPTransportList.GLOBAL_ENABLE_TRACE_MESSAGES = false;
        boolean TRACE_ROUTER_MESSAGES = false;

        Test_WAMPOrchestra all = new Test_WAMPOrchestra();
        HttpCaller caller = new HttpCaller();
        Service service = new Service();

        int jwtPort = 30040;
        String jwtRoot = "/common"; // HTTP application root
        String tokenAuth = "token"; // in-app tokens (APK + ...) authentication interface
        String jwtSecret = "secretForJWTclients"; // shared secret for use by trusted clients
        String apkSecret = "secretForAPKclients"; // shared secret for use by trusted clients
        String apiSecret = "secretForAPIclients"; // shared secret for use by trusted clients

        // HTTP authenticator: 3 users (with password authentication) with different set of roles
        Domain domain = new Domain(jwtRoot)
                .configureUser("aaa", "bbb", null, new RAT().roles("admin", "jwt"))
                .configureUser("bbb", "bbb", null, new RAT().roles("user", "jwt"))
                .configureUser("ccc", "bbb", null, new RAT().roles("jwt"));

        // JWT tokens creator/validator: dynamic (maps to HTTP-authenticated users in domain)
        TokenVerifier jwt = new JWTTokenVerifier().configureStore(new TokenStoreRAM());
        domain.getUserStore().verifiers().add(new TokenUserVerifier(jwt));

        // alternative APK tokens validator: created 3 built-in tokens
        TokenVerifier atv = new APKTokenVerifier()
                .configureStore(new TokenStoreRAM());
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
                .configureStore(new TokenStoreRAM());
        caller.registerToken(apitv, "api-a", "api", "a", "admin,api", null);
        caller.registerToken(apitv, "api-b", "api", "b", "user,api", null);
        caller.registerToken(apitv, "api-c", "api", "c", "api", null);
        domain.getUserStore().verifiers().add(new TokenUserVerifier(apitv));

        // Http application named "JWT" with root "/common" 
        // Added JWT verification "/common/jwt" with no-auth path "/common/jwt/verify" to allow secret-aware clients to verify token.
        all.authenticator = new WAMPRunner(
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

        // create service to use jwt authentication
        int servicePort = 30042;
        String serviceRoot = "/service";
        String serviceWAMP = "methods";
        String serviceREST = "methods";
        // {<realm name>, <wap secret>, <ticket secret>, <ticket aith id>, <ticket auth role>}
        String[][] realmNames = {
            {"demo"},
            {"test"}
        };
        final String wampCRASecret = "wampCRA_Secret1";
        final String wampTicketSecret = "wampTicket_Secret1";
        WAMPAuthProvider wampAuthCRA = null; // trust authentication: just enable connection, nothing else
        WAMPAuthProvider wampAuthTicket = null; // ticket authentication: eval user authenticity and details using TokenUserVerifiers
        WAMPAuthProvider wampAuthAny = new WAMPAuthAny(); // use HTTP authenticated user if no other authentciation method succeeded
        {
            wampAuthCRA = new WAMPAuthCRA(wampCRASecret);
            final URL tvURL = new URL("http://localhost:" + jwtPort + jwtRoot + "/" + tokenAuth + "/verify");
            final List<TokenUserVerifier> tvs = new ArrayList<TokenUserVerifier>() {
                {
                    add(new TokenUserVerifier(
                            tvURL,
                            jwtSecret,
                            id -> {
                                return id != null && id.split("\\.").length == 3;
                            }
                    ));
                    add(new TokenUserVerifier(
                            tvURL,
                            apkSecret,
                            id -> {
                                return id != null && id.startsWith("apk.");
                            }
                    ));
                    add(new TokenUserVerifier(
                            tvURL,
                            apiSecret,
                            id -> {
                                return id != null && id.startsWith("api-key-");
                            }
                    ));
                }
            };

            wampAuthTicket = new WAMPAuthTicket(wampTicketSecret, (session, authid, ticket) -> {
                for (TokenUserVerifier tv : tvs) {
                    if (tv.canVerify("Bearer " + authid)) try {
                        VerificationResult vr = tv.verify("Bearer " + authid);
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

        {
            // HTTP auth: disable basic authentication, configure delegated auths via tokens
            Domain serviceDomain = new Domain(serviceRoot);
            serviceDomain.getUserStore().verifiers().add(new TokenUserVerifier(
                    new URL("http://localhost:" + jwtPort + jwtRoot + "/" + tokenAuth + "/verify"),
                    jwtSecret,
                    id -> {
                        return id != null && id.split("\\.").length == 3;
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

            // enable realms and reated auth methods (ticket, cra, any) in the order
            CustomWAMPRealmFactory realmFactory = new CustomWAMPRealmFactory();
            for (final String[] ss : realmNames) {
                realmFactory.configureRealms(ss[0]);
                realmFactory.configureAuths(
                        ss[0],
                        wampAuthTicket,
                        wampAuthCRA,
                        wampAuthAny);
            }

            // create WAMP router embedded into HTTP application, embedded REST, and "Service" API in "demo" realm.
            all.service = new WAMPRunner(serviceDomain, new HttpApplication("Service", serviceRoot)
                    .configureDataProcessors(new Repository())
            )
                    .configureHttp(servicePort)
                    .configureREST(serviceREST)
                    .configureWAMPRouter(serviceWAMP, true)
                    .configure(realmFactory)
                    .configureAPI("demo", "test", new API_Publisher()
                            .configure(Reflective_API_Builder.buildAPI(
                                    "test",
                                    new Reflective_API_Builder.Reflective_API_Context()
                                            .configure(new API_MethodsProvider_AccessHelper()),
                                    Service.class))
                            .configureContext(service)
                    );
            // add "bridge" for in-router API publishing: not required, just to check flow
            all.service.configureBridgeWAMPAuth("demo", new WAMPAuth(
                    "bridge", //String method,
                    WAMPRunner.API_PUB_CLIENT_TITLE, // String authid,
                    "api-publisher", // String role,
                    null // Map<String, Object> details
            ));
            // add WAMP reflection support to enable automatically generated javascripts
            all.service.wamp()
                    .configureFeature(WAMPFeature.caller_identification)
                    .configureFeature(WAMPFeature.procedure_reflection, new WAMP_FP_Reflection())
                    .configureFeature(WAMPFeature.x_session_meta_api, new WAMP_FP_SessionMetaAPI())
                    .configureFeature(WAMP_FP_VirtualSession.virtual_session, new WAMP_FP_VirtualSession());

            String router_root = all.service.getApp() != null ? all.service.getApp().getRoot() + "/" : "/";
            all.service.configureStub(
                    new StubWAMPVirtualData((all.service.getApp() != null ? all.service.getApp().getRoot() + "/" : "/") + "wamp", (all.service.getApp() != null ? all.service.getApp().getRoot() + "/" : "/") + "wamp", all.service.getRouter(), "js", "jw", "wamp")
                            .configure(new StubWAMPReflectionContext(null, null, true))
                            .configure("demo", "js", "jw", "wamp")
                            .configure("test", "js", "jw", "wamp")
            );

            // add WAMP messages listener for transport to follow WAMP protocol...
            if (TRACE_ROUTER_MESSAGES) {
                all.service.configure(new WAMPTransportMessageListener() {
                    @Override
                    public void onMessageReceived(WAMPTransport wt, WAMPMessage msg) {
                        System.out.println("RR[" + System.currentTimeMillis() + ", " + Thread.currentThread().getName() + ", " + System.identityHashCode(wt) + "]: " + msg.toList());
                    }

                    @Override
                    public void onMessageUnreceived(WAMPTransport wt, WAMPMessage msg) {
                        System.out.println("RU[" + System.currentTimeMillis() + ", " + Thread.currentThread().getName() + ", " + System.identityHashCode(wt) + "]: " + msg.toList());
                    }

                    @Override
                    public void onMessageSent(WAMPTransport wt, WAMPMessage msg) {
                        System.out.println("RS[" + System.currentTimeMillis() + ", " + Thread.currentThread().getName() + ", " + System.identityHashCode(wt) + "]: " + msg.toList());
                    }
                });
            }

        }

        for (int i = 0; i < 1; i++) {
            Domain siteDomain = new Domain("site");
            siteDomain.getUserStore().verifiers().add(new TokenUserVerifier(
                    new URL("http://localhost:" + jwtPort + jwtRoot + "/" + tokenAuth + "/verify"),
                    jwtSecret,
                    id -> {
                        return id != null && id.split("\\.").length == 3;
                    }
            ));
            siteDomain.getUserStore().verifiers().add(new TokenUserVerifier(
                    new URL("http://localhost:" + jwtPort + jwtRoot + "/" + tokenAuth + "/verify"),
                    apkSecret,
                    id -> {
                        return id != null && id.startsWith("apk.");
                    }
            ));
            siteDomain.getUserStore().verifiers().add(new TokenUserVerifier(
                    new URL("http://localhost:" + jwtPort + jwtRoot + "/" + tokenAuth + "/verify"),
                    apiSecret,
                    id -> {
                        return id != null && id.startsWith("api-key-");
                    }
            ));

            CustomWAMPRealmFactory realmFactory = new CustomWAMPRealmFactory();
            for (final String[] ss : realmNames) {
                realmFactory.configureRealms(ss[0]);
                realmFactory.configureAuths(
                        ss[0],
                        wampAuthTicket,
                        wampAuthAny);
            }

            WAMPRunner site = new WAMPRunner(siteDomain, null)
                    .configureWAMPRouter(all.service.getRouterURI(), true)
                    .configure(realmFactory);
            // add WAMP reflection support to enable automatically generated javascripts
            site.wamp().configureFeature(WAMPFeature.procedure_reflection, new WAMP_FP_Reflection());
            all.sites.add(site);
        }

        try {
            all.start();
            Thread.sleep(1000 * 1);

            // Prepare URIs
            URI authURI = new URI("http://localhost:" + jwtPort + jwtRoot + "/" + tokenAuth + "/authenticate");
            URI verifyURI = new URI("http://localhost:" + jwtPort + jwtRoot + "/" + tokenAuth + "/verify");
//            URI restURI = new URI("http://localhost:" + jwtPort + jwtRoot + "/" + jwtREST);
            URI serviceURI = new URI("http://localhost:" + servicePort + serviceRoot + "/" + serviceREST);
            URI wrURI = all.service.getRouterURI();

            // prepare HTTP contexts
            // get login tokens for enumerated users
            // pairs [user:pwd]. For unknown user ("aa") no token!
            caller.requestJwtTokens(
                    authURI,
                    true,
                    "aa:aaaaa", "aaa:bbb", "bbb:bbb", "ccc:bbb"
            );

            System.out.println("-------------------------------------- URLs"
                    + "\n-- JWT       authURI: " + authURI
                    + "\n-- JWT     verifyURI: " + verifyURI
                    //                    + "\n-- JWT   REST URI: " + restURI
                    + "\n-- SVC    serviceURI: " + serviceURI
                    + "\n-- WAMP router wrURI: " + wrURI
                    + "\n--------------------------------------"
            );

            System.out.println("-------------------------------------- Servers"
                    + "\n-- " + all.authenticator.toString().replace("\n", "\n-- ")
                    + "\n----------"
                    + "\n-- " + all.service.toString().replace("\n", "\n-- ")
                    + "\n----------"
                    + "\n-- " + all.service.getRouter().toString().replace("\n", "\n-- ")
                    + "\n--------------------------------------\n"
            );

            // try service calls with different user tokens
            System.out.println("\n\n--------------------------------------------------------------------"
                    + "\n----- Verify API restrictions are effectively applied."
                    + "\n-----  Users with 'a' (apk-a,api-a,aaa) have admin,user roles"
                    + "\n-----  Users with 'a' (apk-a,api-a,aaa) have user role "
                    + "\n-----  Users with 'a' (apk-c,api-c,ccc) have neither admin nor user role"
                    + "\n-----  Method 'getVersionExtra' requires admin role"
                    + "\n-----  Method 'addItems' requires admin or user role"
                    + "\n-----  Other methods are for any user"
                    + "\n-----    method addItems adds items for the user name collection"
                    + "\n-----    method getItems (items) lists items from the user name collection"
                    + "\n------------------------------------------------------------------------\n"
            );
            {
                final AtomicInteger counter = new AtomicInteger();
                for (String[] ss : new String[][]{
                    {"demo/test.Service.addItems", "items", "ab{c}ba"},
                    {"demo/test.Service.addItems", "items", "ba{c}ab"},
                    {"demo/test.Service.getItems"},
                    {"demo/test.Service.getVersion"},
                    {"demo/test.Service.getExtendedVersion"}
                }) {
                    if (ss == null || ss.length < 1 || ss[0] == null) {
                        continue;
                    }
                    String uri = ss[0].contains("://") ? ss[0] : HttpCaller.uriWithParameters(serviceURI + (ss[0].startsWith("/") ? "" : "/") + ss[0], 1, ss);
                    caller.doCallAllTokens(
                            uri,
                            true,
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

            try {
                for (String tokenUser : new String[]{
                    "apk-a",
                    "apk-b",
                    "api-a",
                    "bbb"
                }) {
                    all.sites.get(0).configureHttpAuth("demo", tokenUser, "Authorization", "Bearer " + caller.getTokens().get(tokenUser));
                }
                WAMPNode.DUMP_ESTABLISH_CLOSE = false;
                WAMPClient client1 = all.sites.get(0).connect(wrURI, "apk-a", "test-agent", "demo", new WAMPFeature[]{WAMPFeature.caller_identification}, WAMP.Role.caller);
                WAMPClient client2 = all.sites.get(0).connect(wrURI, "apk-b", "test-agent", "demo", new WAMPFeature[]{WAMPFeature.caller_identification}, WAMP.Role.caller);
                WAMPClient client3 = all.sites.get(0).connect(wrURI, "api-a", "test-agent", "demo", new WAMPFeature[]{WAMPFeature.caller_identification}, WAMP.Role.caller);
                WAMPClient client4 = all.sites.get(0).connect(wrURI, "bbb", "test-agent", "demo", new WAMPFeature[]{WAMPFeature.caller_identification}, WAMP.Role.caller);
                long est1 = client1.waitEstablished(2000L);
                long est2 = client2.waitEstablished(2000L);
                long est3 = client3.waitEstablished(2000L);
                long est4 = client4.waitEstablished(2000L);
                WAMPClient client5 = all.sites.get(0).connect(wrURI, "aaa:bbb", "test-agent", "demo", new WAMPFeature[]{WAMPFeature.caller_identification}, WAMP.Role.caller);
                long est5 = client5.waitEstablished(2000L);

                System.out.println("\n-------------------------------------------------------------------------------------"
                        + "\n-- WAMP clients status (<0 - not established/failed, >=0 waiting time (ms):"
                        + "\n  1: " + est1
                        + "\n  2: " + est2
                        + "\n  3: " + est3
                        + "\n  4: " + est4
                        + "\n  5: " + est5
                        + "\n-------------------------------------------------------------------------------------"
                );

                if (est1 >= 0) try {
                    client1.call("test.Service.addItems", WAMPTools.EMPTY_LIST, WAMPTools.createDict("items", new String[]{"aaaa1", "aaaa2", "aaaa3"}));
                } catch (Throwable th) {
                    th.printStackTrace();
                }
                if (est2 >= 0) try {
                    client2.call("test.Service.addItems", WAMPTools.EMPTY_LIST, WAMPTools.createDict("items", new String[]{"baaa1", "baaa2", "baaa3"}));
                } catch (Throwable th) {
                    th.printStackTrace();
                }
                if (est3 >= 0) try {
                    client3.call("test.Service.addItems", WAMPTools.EMPTY_LIST, WAMPTools.createDict("items", new String[]{"bbaa1", "bbaa2", "bbaa3"}));
                } catch (Throwable th) {
                    th.printStackTrace();
                }
                if (est4 >= 0) try {
                    client4.call("test.Service.addItems", WAMPTools.EMPTY_LIST, WAMPTools.createDict("items", new String[]{"bbba1", "bbba2", "bbba3"}));
                } catch (Throwable th) {
                    th.printStackTrace();
                }
                if (est5 >= 0) try {
                    client5.call("test.Service.addItems", WAMPTools.EMPTY_LIST, WAMPTools.createDict("items", new String[]{"bbbb1", "bbbb2", "bbbb3"}));
                } catch (Throwable th) {
                    th.printStackTrace();
                } else {
                    //System.out.println("\n------------------------------------- FAILED CLIENT:\n---- " + client5.toString().replace("\n", "\n---- "));
                }
            } catch (Throwable th) {
                th.printStackTrace();
            }
        } finally {

            System.out.println("-------------------------------------- Services"
                    + "\n  " + service.toString().replace("\n", "\n  ")
            );

            //Thread.sleep(1000 * 60 * 60 * 15);
            all.stop();
            System.exit(0);
        }

    }
}
