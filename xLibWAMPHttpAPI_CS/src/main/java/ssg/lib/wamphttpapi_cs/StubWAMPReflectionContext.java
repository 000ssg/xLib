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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import ssg.lib.common.Stub.StubContext;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPRealm;
import ssg.lib.wamp.features.WAMP_FP_Reflection;
import ssg.lib.wamp.features.WAMP_FP_Reflection.RR;
import ssg.lib.wamp.rpc.impl.dealer.WAMPRPCDealer;
import ssg.lib.wamp.util.WAMPTools;

/**
 *
 * @author sesidoro
 */
public class StubWAMPReflectionContext extends StubContext<WAMPRealm, Map, Map, String> {

    public StubWAMPReflectionContext(String baseURL, String namespace, boolean generateExtendedComments) {
        super(baseURL, namespace, generateExtendedComments);
    }

    RR rr(WAMPRealm api) {
        if (api != null) {
            WAMPRPCDealer rpcd = api.getActor(WAMP.Role.dealer);
            WAMP_FP_Reflection wr = rpcd.getFeatureProvider(WAMPFeature.procedure_reflection);
            if (wr != null) {
                RR rr = wr.getRegistrations(api.getName());
                return rr;
            }
        }
        return null;
    }

    public long timestamp(WAMPRealm api) {
        RR rr = rr(api);
        return rr != null ? rr.timestamp() : System.currentTimeMillis();
    }

    @Override
    public String nameOf(Object obj) {
        return obj instanceof WAMPRealm ? ((WAMPRealm) obj).getName() : obj instanceof Map ? (String) ((Map) obj).get("name") : obj instanceof String ? (String) obj : null;
    }

    @Override
    public List<Map> methods(WAMPRealm api) {
        RR rr = rr(api);
        if (rr != null) {
            List<Map> r = new ArrayList<>();
            for (String pn : rr.getNames("proc")) {
                for (Map<String, Object> m : rr.getMaps("proc", pn)) {
                    if (m != null) {
                        r.add(WAMPTools.createDict("name", pn, "def", m));
                    }
                }
            }
            return r;
        }
        return Collections.emptyList();
    }

    @Override
    public List<Map> parameters(Map method) {
        List<Map> params = method != null && method.get("def") instanceof Map
                ? (List) ((Map) method.get("def")).get("parameters")
                : null;
        return params != null ? params : Collections.emptyList();
    }

    @Override
    public PDIR direction(Map parameter) {
        return parameter != null ? PDIR.in : null;
    }

    @Override
    public String returnType(Map method) {
        return method != null && method.get("def") instanceof Map && ((Map) method.get("def")).get("returns") instanceof Map
                ? (String) ((Map) ((Map) method.get("def")).get("returns")).get("type")
                : null;
    }

    @Override
    public String type(Map parameter) {
        return parameter != null ? (String) parameter.get("type") : null;
    }

    @Override
    public StubWAMPReflectionContext clone() {
        try {
            StubWAMPReflectionContext copy = (StubWAMPReflectionContext) super.clone();
            return copy;
        } catch (Throwable th) {
            th.printStackTrace();
            return null;
        }
    }
}
