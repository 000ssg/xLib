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
package ssg.lib.httpapi_cs;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import ssg.lib.api.APIAccess;
import ssg.lib.api.util.Reflective_API_Builder.ReflectiveAccessHelper;
import ssg.lib.http.rest.MethodsProvider;
import ssg.lib.http.rest.RESTAccess;
import ssg.lib.http.rest.RESTMethod;
import ssg.lib.http.rest.XMethodsProvider;

/**
 *
 * @author 000ssg
 */
public class API_MethodsProvider_AccessHelper implements ReflectiveAccessHelper {

    MethodsProvider[] evaluators;
    Map<Class, Map<Method, RESTAccess>> access = new LinkedHashMap<>();

    public API_MethodsProvider_AccessHelper(MethodsProvider... providers) {
        if (providers == null || providers.length == 0) {
            evaluators = new MethodsProvider[]{new XMethodsProvider()};
        } else {
            evaluators = new MethodsProvider[0];
            for (MethodsProvider p : providers) {
                if (p == null) {
                    continue;
                }
                boolean found = false;
                for (MethodsProvider pi : evaluators) {
                    if (pi.equals(p)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    evaluators = Arrays.copyOf(evaluators, evaluators.length + 1);
                    evaluators[evaluators.length - 1] = p;
                }
            }
        }
    }

    @Override
    public APIAccess evalAccess(Class cl, Method m) {
        if (cl == null) {
            return null;
        }
        Map<Method, RESTAccess> a = access.get(cl);
        if (a == null) {
            a = evaluate(cl);
        }
        if (a != null) {
            RESTAccess ra = a.get(m);
            if (ra != null) {
                Collection<String> ss = new LinkedHashSet<>();
                if (ra.getInstance() != null && ra.getInstance().getRoles() != null) {
                    ss.addAll(ra.getInstance().getRoles());
                }
                if (ra.getMethod() != null && ra.getMethod().getRoles() != null) {
                    ss.addAll(ra.getMethod().getRoles());
                }
                if (!ss.isEmpty()) {
                    APIAccess r = new APIAccess();
                    for (String s : ss) {
                        r.set(s, APIAccess.A_EXECUTE);
                    }
                    return r;
                }
            }
        }
        return null;
    }

    Map<Method, RESTAccess> evaluate(Class cl) {
        for (MethodsProvider mp : evaluators) {
            if (mp.canHandleClass(cl)) {
                synchronized (evaluators) {
                    Map<Method, RESTAccess> r = new HashMap<>();
                    Map<String, List<RESTMethod>> ms = mp.findMethods(cl);
                    if (ms != null) {
                        for (List<RESTMethod> rms : ms.values()) {
                            for (RESTMethod rm : rms) {
                                if (rm.getAccess() != null) {
                                    r.put(rm.getMethod(), rm.getAccess());
                                }
                            }
                        }
                    }
                    access.put(cl, r);
                    return r;
                }
            }
        }
        return null;
    }

}
