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

import java.net.URI;
import ssg.lib.common.net.NetTools;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.HttpAuthenticator;
import ssg.lib.http.HttpAuthenticator.Domain;
import ssg.lib.http.RAT;
import ssg.lib.http.dp.tokens.APKTokenVerifier;
import ssg.lib.http.dp.tokens.JWTTokenVerifier;
import ssg.lib.http.dp.tokens.TokenVerifier;
import ssg.lib.http.dp.tokens.TokenVerifierHttpDataProcessor;
import ssg.lib.http.rest.annotations.XMethod;
import ssg.lib.http.rest.annotations.XType;
import ssg.lib.http_cs.HttpCaller;
import ssg.lib.http_cs.HttpRunner;
import ssg.lib.http_cs.HttpRunner.HttpConfig;
import ssg.lib.httpapi_cs.APIRunner.APIConfig;
import ssg.lib.net.MCS.MCSConfig;
import ssg.lib.service.Repository;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.features.WAMP_FP_Reflection;
import ssg.lib.wamp.features.WAMP_FP_SessionMetaAPI;
import ssg.lib.wamp.features.WAMP_FP_VirtualSession;
import ssg.lib.wamphttpapi_cs.WAMPRunner.WAMPConfig;

/**
 *
 * @author 000ssg
 */
public class Test_WAMPRunner_Conf {

    @XType
    public static class XDemo {
        @XMethod
        public double getRND(){
            return Math.random();
        }
    }
    
    public static void main(String... args) throws Exception {
        int ssoPort = 30010;
        int wampPort = 30011;

        //////////////////////////////////////////////////////////// HTTP helper
        HttpCaller caller = new HttpCaller();

        ///////////////////////////////////////////////////////// authenticators
        String ssoRoot = "/sso";
        String wampRoot = "/wamp";
        String tokenAuth = "token"; // in-app tokens (APK + ...) authentication interface
        String jwtSecret = "secretForJWTclients"; // shared secret for use by trusted clients
        String apkSecret = "secretForAPKclients"; // shared secret for use by trusted clients
        String apiSecret = "secretForAPIclients"; // shared secret for use by trusted clients

        // HTTP authenticator: 3 users (with password authentication) with different set of roles
        HttpAuthenticator.Domain ssoDomain = new HttpAuthenticator.Domain(ssoRoot)
                .configureUser("aaa", "bbb", null, new RAT().roles("admin", "jwt"))
                .configureUser("bbb", "bbb", null, new RAT().roles("user", "jwt"))
                .configureUser("ccc", "bbb", null, new RAT().roles("jwt"));

        // JWT tokens creator/validator: dynamic (maps to HTTP-authenticated users in ssoDomain)
        TokenVerifier jwt = new JWTTokenVerifier().configureStore(new TokenVerifier.TokenStoreRAM());
        // enable jwt tokens for login into SSO server
        //ssoDomain.getUserStore().verifiers().add(new TokenUserVerifier(jwt));

        // alternative APK tokens validator: created 3 built-in tokens
        TokenVerifier atv = new APKTokenVerifier()
                .configureStore(new TokenVerifier.TokenStoreRAM());
        caller.registerToken(atv, "apk-a", "apk", "a", "admin,apk", null);
        caller.registerToken(atv, "apk-b", "apk", "b", "user,apk", null);
        caller.registerToken(atv, "apk-c", "apk", "c", "apk", null);
        // enable "apk." users to login into SSO server as well
        //ssoDomain.getUserStore().verifiers().add(new TokenUserVerifier(atv));

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
        // enable "api-" users to login into SSO server as well
        //ssoDomain.getUserStore().verifiers().add(new TokenUserVerifier(apitv));

        HttpRunner authServer = new HttpRunner(
                // named application at "/sso"
                new HttpApplication("SSO", ssoRoot)
                        // enable basic auth so domain-defined users might easily login
                        .configureBasicAuthentication(true)
                        // ensure can configure application-level data handlers
                        .configureDataProcessors(new Repository())
                        // add handler of authentication requests by verifying tokens
                        .configureDataProcessor(
                                1,
                                // verifier root is at 'token/' -> '/sso/token/'
                                // built-in verifier path: '/sso/token/verify'
                                new TokenVerifierHttpDataProcessor(tokenAuth + "/")
                                        .configure(jwtSecret, jwt)
                                        .configure(apkSecret, atv)
                                        .configure(apiSecret, apitv)
                        )
                        // disable authentication when calling token verification
                        // it checks valid connection based on secret in header
                        .configureNoAuthPaths(
                                tokenAuth + "/verify"
                        )
        )
                // add SSO users
                .configureDomain(ssoDomain)
                .configuration(new MCSConfig("").init(
                        "acceptors=3"
                ),
                        new HttpConfig("").init(
                                "httpPort=" + ssoPort
                        )
                );
        URI tokenVerifyURI = new URI("http://localhost:" + ssoPort + ssoRoot + "/" + tokenAuth + "/verify");

        String wampCRASecret = "wampCRA_Secret1";
        String wampTicketSecret = "wampTicket_Secret1";
        WAMPRunner wamp = new WAMPRunner(
                new HttpApplication("WAMP Router", wampRoot)
        )
                .configureDomain(new Domain(wampRoot))
                .configuration(
                        new MCSConfig("").init(
                                "acceptors=3"
                        ),
                        new HttpConfig("").init(
                                "httpPort=" + wampPort,
                                "rest=wamp",
                                "tokenDelegate=type=jwt;uri=" + tokenVerifyURI + ";secret=" + jwtSecret,
                                "tokenDelegate=type=token;uri=" + tokenVerifyURI + ";secret=" + apkSecret + ";prefix=apk.",
                                "tokenDelegate=type=token;uri=" + tokenVerifyURI + ";secret=" + apiSecret + ";prefix=api-key-"
                        ),
                        new APIConfig("").init(
                                
                                "api=namespace=A;name=a;item=ssg.lib.wamphttpapi_cs.Test_WAMPRunner_Conf$XDemo"
                        ),
                        new WAMPConfig("").init(
                                "routerPath=wamp",
                                "auth=type=wampcra;secret=" + wampCRASecret,
                                "auth={'type':'ticket','secret':'" + wampTicketSecret + "', 'tokenDelegate': ['type=jwt;uri=" + tokenVerifyURI + ";secret=" + jwtSecret + "']}",
                                "auth=type=any"
                        )
                );
                    wamp.wamp()
                    .configureFeature(WAMPFeature.caller_identification)
                    .configureFeature(WAMPFeature.procedure_reflection, new WAMP_FP_Reflection())
                    .configureFeature(WAMPFeature.x_session_meta_api, new WAMP_FP_SessionMetaAPI())
                    .configureFeature(WAMP_FP_VirtualSession.virtual_session, new WAMP_FP_VirtualSession());


        authServer.start();
        wamp.start();

        NetTools.delay(3000);
        
        System.out.println("\n------------------------------------------ SSO"
                + "\n-- " + authServer.toString().replace("\n", "\n-- ")
        );
        System.out.println("\n\n----------------------------------------- WAMP"
                + "\n-- " + wamp.toString().replace("\n", "\n-- ")
        );
        System.out.println("\n\n----------------------------------------- WAMP Router"
                + "\n-- " + wamp.getRouter().toString().replace("\n", "\n-- ")
        );
        
        NetTools.delay(3000);
        
        wamp.stop();
        authServer.stop();
    }
}
