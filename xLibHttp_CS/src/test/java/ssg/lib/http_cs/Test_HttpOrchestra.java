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
package ssg.lib.http_cs;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import ssg.lib.common.JSON;
import ssg.lib.common.Refl;
import ssg.lib.common.net.NetTools;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.HttpAuthenticator.Domain;
import ssg.lib.http.RAT;
import ssg.lib.http.dp.tokens.APKTokenVerifier;
import ssg.lib.http.dp.tokens.TokenUserVerifier;
import ssg.lib.http.dp.tokens.TokenVerifier;
import ssg.lib.http.dp.tokens.TokenVerifier.TokenStoreRAM;
import ssg.lib.http.dp.tokens.TokenVerifierHttpDataProcessor;
import ssg.lib.http.dp.tokens.JWTTokenVerifier;
import ssg.lib.http.rest.StubRESTHttpContext;
import ssg.lib.http.rest.StubVirtualData;
import ssg.lib.http.rest.annotations.XMethod;
import ssg.lib.http.rest.annotations.XType;
import ssg.lib.service.Repository;

/**
 * Http distributed system demo:
 *
 * "authenticator": module, that provides user login and creates/verifies
 * tokens.
 *
 * "service": module, that provides REST API access for anonymous and
 * authenticated/authorized users (verified via "authenticator".
 *
 * Test demonstrates how to configure http application authentication (basic)
 * and how to authenticate user based on token (jwt, others), using token
 * verification service. With jwt, the token generation/usage is demonstrated.
 *
 * The result of authentication is verified via calls to restricted (role-based)
 * REST methods.
 *
 * @author 000ssg
 */
public class Test_HttpOrchestra {

    public HttpRunner authenticator;
    public HttpRunner service;
    public List<HttpRunner> sites = new ArrayList<>();

    @XType
    public static class Service {

        @XMethod(roles = {"user", "admin"})
        public long timestamp() {
            return System.currentTimeMillis();
        }

        @XMethod
        public String echo(String message) {
            return "ECHO: " + message;
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
        Test_HttpOrchestra all = new Test_HttpOrchestra();
        HttpCaller caller = new HttpCaller();

        int jwtPort = 30040;
        String jwtRoot = "/common"; // HTTP application root
        String tokenAuth = "token"; // in-app tokens (APK + ...) authentication interface
        String jwtSecret = "secretForJWTclients"; // secret for use by trusted clients
        String apkSecret = "secretForAPKclients"; // secret for use by trusted clients
        String apiSecret = "secretForAPIclients"; // secret for use by trusted clients

        // HTTP authenticator: 3 users with different set of roles
        Domain domain = new Domain(jwtRoot)
                .configureUser("aaa", "bbb", null, new RAT().roles("admin", "jwt"))
                .configureUser("bbb", "bbb", null, new RAT().roles("user", "jwt"))
                .configureUser("ccc", "bbb", null, new RAT().roles("jwt"));

        // JWT tokens creator/validator: dynamic (maps to HTTP-authenticated users in domain)
        TokenVerifier jwt=new JWTTokenVerifier().configureStore(new TokenStoreRAM());
        domain.getUserStore().verifiers().add(new TokenUserVerifier(jwt));

        // alternative APK tokens validator: created 3 built-in tokens - non-domain users
        TokenVerifier atv = new APKTokenVerifier()
                .configureStore(new TokenStoreRAM());
        caller.registerToken(atv, "apk-a", "apk", "a", "admin,apk", null);
        caller.registerToken(atv, "apk-b", "apk", "b", "user,apk", null);
        caller.registerToken(atv, "apk-c", "apk", "c", "apk", null);
        domain.getUserStore().verifiers().add(new TokenUserVerifier(atv));

        // alternative api-key: created 3 built-in tokens - non-domain users
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
        all.authenticator = new HttpRunner(
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
//                                jwtAuth + "/verify",
                                tokenAuth + "/verify")
        )
                .configureHttp(jwtPort);

        // create service to use jwt authentication
        int servicePort = 30042;
        String serviceRoot = "/service";
        String serviceREST = "methods";
        Domain serviceDomain = new Domain(serviceRoot);
//        serviceDomain.getUserStore().verifiers().add(new JWTUserVerifier(new URL("http://localhost:" + jwtPort + jwtRoot + "/" + jwtAuth + "/verify"), jwtSecret));
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
        all.service = new HttpRunner(new HttpApplication("Service", serviceRoot)
                .configureDataProcessors(new Repository())
        )
                .configureHttp(servicePort)
                .configureREST(serviceREST);
        all.service.getService().configureAuthentication(serviceDomain);
        all.service.getREST().registerProviders(null, new Service());

        String router_root = all.service.getApp() != null ? all.service.getApp().getRoot() + "/" : "/";
        all.service.configureStub(new StubVirtualData(router_root.substring(0, router_root.length() - 1), null, all.service.getREST(), "js", "jw")
                .configure(new StubRESTHttpContext(null, null, true))
                .configure("methods", "js", "jw"));

        Refl jr = new Refl.ReflJSON.ReflJSON();
        JSON.Encoder je = new JSON.Encoder(jr);
        JSON.Decoder jd = new JSON.Decoder(jr);

        try {
            all.start();

            System.out.println("-------------------------------------- Servers"
                    + "\n-- " + all.authenticator.toString().replace("\n", "\n-- ")
                    + "\n----------"
                    + "\n-- " + all.service.toString().replace("\n", "\n-- ")
                    + "\n--------------------------------------\n"
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

            // get t
            System.out.println("\n\n---------------------------------------------------------------------------- Tokens (" + caller.tokens.size() + ")");
            for (String key : caller.tokens.keySet()) {
                System.out.println("Token[" + key + "] " + caller.tokens.get(key));
            }

            // try service calls with different user tokens
            {
                System.out.println("\n----------------------------------------------------------------------------- Do calls:");
                for (String[] ss : new String[][]{
                    {"timestamp"},
                    {"echo", "message", "hello"}
                }) {
                    String uri = HttpCaller.uriWithParameters(serviceURI + (ss[0].startsWith("/") ? "" : "/") + ss[0], 1, ss);
                    caller.doCallAllTokens(uri, false, null, null, null, null);
                }
            }

            NetTools.httpGet(new URL(serviceURI.toString() + "/script.js"), null, null, new NetTools.HttpResult.HttpResultDebug());
        } finally {
            all.stop();
            System.exit(0);
        }
    }

}
