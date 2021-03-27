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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPFeatureProvider;
import ssg.lib.wamp.WAMPRealm;
import ssg.lib.wamp.WAMPRealmFactory;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.auth.WAMPAuth;
import ssg.lib.wamp.auth.WAMPAuthProvider;
import ssg.lib.wamp.util.WAMPException;

/**
 *
 * @author 000ssg
 */
public class CustomWAMPRealmFactory extends WAMPRealmFactory implements WAMPRealmFactory.RealmVerifier, WAMPRealm.SessionVerifier {

    public static boolean DEBUG = true;

    Collection<String> realmNames = new HashSet<>();
    Map<String, List<WAMPAuthProvider>> authProviders = new LinkedHashMap<>();

    public CustomWAMPRealmFactory() {
        super(new ArrayList<>());
        try {
            Field f = getClass().getSuperclass().getDeclaredField("verifiers");
            f.setAccessible(true);
            ((Collection) f.get(this)).add(this);
        } catch (Throwable th) {
        }
    }

    public CustomWAMPRealmFactory(List<RealmVerifier> verifiers) {
        super(verifiers);
        try {
            Field f = getClass().getSuperclass().getDeclaredField("verifiers");
            f.setAccessible(true);
            ((Collection) f.get(this)).add(this);
        } catch (Throwable th) {
        }
    }

    public CustomWAMPRealmFactory configureRealms(String... names) {
        if (names != null) {
            for (String name : names) {
                if (name != null && !name.trim().isEmpty()) {
                    realmNames.add(name.trim());
                }
            }
        }
        return this;
    }

    public CustomWAMPRealmFactory configureAuths(String realmName, WAMPAuthProvider... authProviders) {
        if (authProviders != null && realmName != null) {
            for (WAMPAuthProvider p : authProviders) {
                if (p == null) {
                    continue;
                }
                List<WAMPAuthProvider> ps = this.authProviders.get(realmName);
                if (ps == null) {
                    ps = new ArrayList<>();
                    this.authProviders.put(realmName, ps);
                }
                if (!ps.contains(p)) {
                    ps.add(p);
                }
            }
        }
        return this;
    }

    @Override
    public WAMPRealm newRealm(Object context, String name, WAMPFeature[] features, Map<WAMPFeature, WAMPFeatureProvider> featureProviders, WAMP.Role... roles) throws WAMPException {
        WAMPRealm realm = super.newRealm(context, name, features, featureProviders, roles);
        if (DEBUG) {
            System.out.println("CREATE REALM " + name + " (" + context + ")");
        }
        List<WAMPAuthProvider> apr = authProviders.get(name);
        if (apr != null) {
            realm.getAuthProviders().addAll(apr);
            if (DEBUG) {
                System.out.println("  AUTH (special) REALM " + name + " (" + context + ") " + apr);
            }
        } else {
            apr = authProviders.get("");
            if (apr != null) {
                realm.getAuthProviders().addAll(apr);
                if (DEBUG) {
                    System.out.println("  AUTH (common) REALM " + name + " (" + context + ") " + apr);
                }
            }
        }
        realm.addSessisonVerifier(this);
        return realm;
    }

    /**
     * WAMP realm validation (verifier)
     *
     * @param context
     * @param name
     * @param features
     * @param roles
     * @return
     * @throws WAMPException
     */
    public boolean matches(Object context, String name, WAMPFeature[] features, WAMP.Role... roles) throws WAMPException {
        if (realmNames.contains(name)) {
            return true;
        }
        throw new WAMPException("Realm " + name + " is not allowed.");
    }

    @Override
    public void verifySession(WAMPRealm realm, WAMPSession session, WAMPAuth auth) throws WAMPException {
        if (DEBUG) {
            System.out.println("VERIFY SESSION[" + (session.getLocal().isRouter() ? "router" : "client") + ", " + realm.getName() + "] "
                    + session.getRemote().getRoles().keySet() + ", " + auth
            //+ "\n  Stacktrace:\n  " + CommonTools.stackTrace(2)
            );
        }
    }
}
