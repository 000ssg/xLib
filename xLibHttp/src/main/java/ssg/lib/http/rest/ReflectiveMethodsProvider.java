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
package ssg.lib.http.rest;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.common.Refl;
import ssg.lib.common.Refl.ReflImpl;

/**
 *
 * @author 000ssg
 */
public class ReflectiveMethodsProvider implements MethodsProvider {

    ReflImpl refl = new ReflImpl();
    boolean addClassNameInPath = false;

    public ReflectiveMethodsProvider() {
    }

    public ReflectiveMethodsProvider setClassNameInPath(boolean addClassNameInPath) {
        this.addClassNameInPath = addClassNameInPath;
        return this;
    }

    Collection<Class> exclusions = new HashSet<Class>() {
        {
            add(URI.class);
            add(URL.class);
            add(File.class);
            add(Map.class);
            add(Collection.class);
        }
    };

    Collection<Object> excludeMethods = new HashSet<Object>() {
        {
            for (Method m : Object.class.getMethods()) {
                add(m);
                add(m.toGenericString());
            }
        }
    };

    Collection<Object> allowedMethods = new HashSet<Object>();

    Map<String, String[]> methodParameterNamesMapping = new HashMap<String, String[]>();

    public void addExclusion(Class... types) {
        if (types != null) {
            for (Class cl : types) {
                if (!exclusions.contains(cl)) {
                    exclusions.add(cl);
                }
            }
        }
    }

    public void addExcludedMethods(Object... ms) {
        if (ms != null) {
            for (Object m : ms) {
                if (m instanceof Method) {
                    if (!excludeMethods.contains(m)) {
                        excludeMethods.add(m);
                    }
                    m = ((Method) m).toGenericString();
                    if (!excludeMethods.contains(m)) {
                        excludeMethods.add(m);
                    }
                } else if (m instanceof String) {
                    if (!excludeMethods.contains(m)) {
                        excludeMethods.add(m);
                    }
                }
            }
        }
    }

    public void addAllowedMethods(Object... ms) {
        if (ms != null) {
            for (Object m : ms) {
                if (m instanceof Method) {
                    if (!allowedMethods.contains(m)) {
                        allowedMethods.add(m);
                    }
                    m = ((Method) m).toGenericString();
                    if (!allowedMethods.contains(m)) {
                        allowedMethods.add(m);
                    }
                } else if (m instanceof String) {
                    if (!allowedMethods.contains(m)) {
                        allowedMethods.add(m);
                    }
                }
            }
        }
    }

    /**
     * Contains set of parameter names to use instead of available ones. If
     * number of names exceed number of parameters, Last value stands for method
     * path.
     *
     * If some value is null -> do not replace but use originally evaluated one.
     *
     * @param method
     * @param names
     */
    public void addMappedParameterName(Object method, String... names) {
        if (method == null || names == null) {
            return;
        }
        String m = null;
        if (method instanceof Method) {
            m = ((Method) method).toGenericString();
        } else {
            m = "" + method;
        }
        methodParameterNamesMapping.put(m, names);
    }

    public boolean checkIfAllowed(Method m) {
        if (excludeMethods.contains(m) || excludeMethods.contains(m.toGenericString())) {
            return false;
        }
        if (!allowedMethods.isEmpty()) {
            if (allowedMethods.contains(m) || allowedMethods.contains(m.toGenericString())) {
                // OK
            } else {
                return false;
            }
        }
        int modifiers = m.getModifiers();
        return !Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers) && Modifier.isPublic(modifiers);
    }

    @Override
    public boolean isOperable() {
        return true;
    }

    @Override
    public boolean canHandleClass(Class clazz) {
        if (clazz == null) {
            return false;
        }
        for (Class cl : exclusions) {
            if (cl.isAssignableFrom(clazz)) {
                return false;
            }
        }
        return !refl.getBeanInfo(clazz, true).isEmpty();
    }

    @Override
    public Map<String, List<RESTMethod>> findMethods(Object obj) {
        Class clazz = (obj instanceof Class) ? (Class) obj : obj.getClass();

        Map<String, List<RESTMethod>> wms = new LinkedHashMap<String, List<RESTMethod>>();

        if (clazz == null) {
            return wms;
        }
        for (Class cl : exclusions) {
            if (cl.isAssignableFrom(clazz)) {
                return wms;
            }
        }

        Method[] mss = clazz.getMethods();

        // override methods list if processing Proxy instance.
        if (obj instanceof Proxy) {
            List<Method> pmss = Refl.ReflImpl.getProxyMethods((Proxy) obj);
            if (pmss != null && !pmss.isEmpty()) {
                mss = pmss.toArray(new Method[pmss.size()]);
            }
        }

        Arrays.sort(mss, new Comparator<Method>() {

            public int compare(Method o1, Method o2) {
                if (o1 == null && o2 == null) {
                    return 0;
                }
                if (o1 == null) {
                    return -1;
                }
                if (o2 == null) {
                    return 1;
                }
                return o1.getName().compareTo(o2.getName());
            }
        });

        RESTProvider pr = new RESTProvider();
        {
            String s = clazz.getSimpleName();
            pr.setName(s);
            if (addClassNameInPath) {
                if (Character.isUpperCase(s.charAt(0))) {
                    s = s.substring(0, 1).toLowerCase() + s.substring(1);
                }
                pr.setPaths(s);
            }
        }

        Map<String, Object[][]> bis = refl.getBeanInfo(clazz, true);
        Collection<Method> bms = new HashSet<Method>();
        for (Object[][] oo : bis.values()) {
            if (oo[0][1] instanceof Method) {
                bms.add((Method) oo[0][1]);
            }
            if (oo[1][1] instanceof Method) {
                bms.add((Method) oo[1][1]);
            }
        }

        List<RESTMethod> ms = new ArrayList<RESTMethod>();
        for (Method m : mss) {
            if (checkIfAllowed(m)) {
                Parameter[] prs = m.getParameters();
                String[] prss = methodParameterNamesMapping.get(m.toGenericString());

                String operationName = m.getName();
                if (operationName == null || operationName.isEmpty()) {
                    operationName = m.getName();
                }
                String[] operationPaths = (prss != null && prss.length > prs.length)
                        ? Arrays.copyOfRange(prss, prs.length, prss.length)
                        : null;
                if (operationPaths == null) {
                    String s = m.getName();
                    if ((s.startsWith("get") || s.startsWith("set")) && s.length() > 3) {
                        s = s.substring(3);
                    } else if (s.startsWith("is") && s.length() > 2) {
                        s = s.substring(2);
                    }
                    if (s.length() > 1 && Character.isUpperCase(s.charAt(0))) {
                        s = s.substring(0, 1).toLowerCase() + s.substring(1);
                    }
                    operationPaths = new String[]{s};
                }

                for (String operationPath : operationPaths) {
                    RESTMethod mth = new RESTMethod();
                    mth.setProvider(pr);
                    m.setAccessible(true);
                    mth.setMethod(m);
                    mth.setName(operationName);
                    mth.setPath(operationPath);
                    int pri = 0;
                    for (Parameter p : prs) {
                        RESTParameter wsp = new RESTParameter();
                        if (prss != null && prss[pri] != null) {
                            wsp.setName(prss[pri]);
                        } else {
                            wsp.setName(p.getName());
                        }
                        wsp.setType(p.getType());
                        mth.getParams().add(wsp);
                        pri++;
                    }

                    mth.setReturnType(m.getReturnType());
                    if (ms.isEmpty()) {
                        ms.add(mth);
                        wms.put(operationName, ms);
                    } else if (ms.get(0).getName().equals(operationName)) {
                        ms.add(mth);
                    } else {
                        ms = new ArrayList<RESTMethod>();
                        ms.add(mth);
                        wms.put(operationName, ms);
                    }
                }
            }
        }
        return wms;
    }

}
