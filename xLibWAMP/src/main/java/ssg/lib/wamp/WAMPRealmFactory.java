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
package ssg.lib.wamp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.util.WAMPException;

/**
 *
 * @author 000ssg
 */
public class WAMPRealmFactory {

    static WAMPRealmFactory defaultFactory;

    public static WAMPRealm createRealm(Object context, String name, WAMPFeature[] features, Map<WAMPFeature, WAMPFeatureProvider> featureProviders, Role... roles) throws WAMPException {
        if (defaultFactory == null) {
            defaultFactory = new WAMPRealmFactory();
        }
        return defaultFactory.newRealm(context, name, features, featureProviders, roles);
    }

    public static WAMPRealmFactory getInstance() {
        return defaultFactory;
    }

    public static void setInstance(WAMPRealmFactory factory) {
        defaultFactory = factory;
    }

    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////// instance
    ////////////////////////////////////////////////////////////////////////////
    WAMPActorFactory actorFactory;
    List<RealmVerifier> verifiers = new ArrayList<>();

    private WAMPRealmFactory() {
    }

    public WAMPRealmFactory(List<RealmVerifier> verifiers) {
        if (verifiers != null) {
            this.verifiers.addAll(verifiers);
        }
    }

    public WAMPRealm newRealm(Object context, String name, WAMPFeature[] features, Map<WAMPFeature, WAMPFeatureProvider> featureProviders, Role... roles) throws WAMPException {
        if (test(context, name, features, roles)) {
            return WAMPRealm.createRealm(actorFactory, name, features, featureProviders, roles);
        } else {
            return null;
        }
    }

    public boolean test(Object context, String name, WAMPFeature[] features, Role... roles) throws WAMPException {
        boolean allowed = (verifiers.isEmpty()) ? true : false;
        for (RealmVerifier v : verifiers) {
            if (v.matches(context, name, features, roles)) {
                allowed = true;
                break;
            }
        }
        return allowed;
    }

    public static interface RealmVerifier {

        boolean matches(Object context, String name, WAMPFeature[] features, Role... roles) throws WAMPException;
    }
}
