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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.common.Refl;

/**
 *
 * @author 000ssg
 */
public abstract class AnnotationsBasedMethodsProvider implements MethodsProvider {

    public static final String BODY_AS_PARAMETER = "#!:BODY";

    Class wiClass; // type/instance
    Class wmClass; // method
    Class wpClass; // method parameter
    Class waClass; // access descriptor
    Class woClass; // options

    public AnnotationsBasedMethodsProvider(
            String wiClassName,
            String wmClassName,
            String wpClassName
    ) {
        wiClass = getAnnotationForClassName(wiClassName);
        wmClass = getAnnotationForClassName(wmClassName);
        wpClass = getAnnotationForClassName(wpClassName);
    }

    public AnnotationsBasedMethodsProvider(
            String wiClassName,
            String wmClassName,
            String wpClassName,
            String waClassName,
            String woClassName
    ) {
        wiClass = getAnnotationForClassName(wiClassName);
        wmClass = getAnnotationForClassName(wmClassName);
        wpClass = getAnnotationForClassName(wpClassName);
        waClass = getAnnotationForClassName(waClassName);
        woClass = getAnnotationForClassName(woClassName);
    }

    Class getAnnotationForClassName(String className) {
        Class cl = null;
        try {
            cl = getClass().getClassLoader().loadClass(className);
            if (cl != null && !cl.isAnnotation()) {
                cl = null;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return cl;
    }

    Method getAnnotationPropertyReader(Class cl, String name) {
        try {
            return (cl != null && name != null) ? cl.getDeclaredMethod(name) : null;
        } catch (NoSuchMethodException nsmex) {
        } catch (ClassFormatError cfex) {
            cfex.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean isOperable() {
        return wiClass != null && wmClass != null && wpClass != null;
    }

    @Override
    public boolean canHandleClass(Class clazz) {
        if (clazz == null || wmClass == null) {
            return false;
        } else {
            for (Method m : clazz.getMethods()) {
                int modifiers = m.getModifiers();
                if (!Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers) && Modifier.isPublic(modifiers)) {
                    for (Annotation a : m.getDeclaredAnnotations()) {
                        if (wmClass.isAssignableFrom(a.getClass())) {
                            return true;
                        }
                    }
                }
            }
            Class[] clis = clazz.getInterfaces();
            if (clis != null && clis.length > 0) {
                for (Class cli : clis) {
                    for (Method m : cli.getMethods()) {
                        int modifiers = m.getModifiers();
                        if (!Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers) && Modifier.isPublic(modifiers)) {
                            for (Annotation a : m.getDeclaredAnnotations()) {
                                if (wmClass.isAssignableFrom(a.getClass())) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public Map<String, List<RESTMethod>> findMethods(Object obj) {
        Class clazz = (obj instanceof Class) ? (Class) obj : obj.getClass();

        Map<String, List<RESTMethod>> wms = new LinkedHashMap<String, List<RESTMethod>>();
        Method[] mss = clazz.getMethods();

        RESTProvider pr = new RESTProvider();
        RESTAccess prAccess = null;
        if (wiClass != null && clazz.getAnnotations().length > 0) {
            for (Annotation a : clazz.getAnnotations()) {
                String pn = getProviderName(a);
                String[] pp = getProviderPaths(a);
                String pd = getProviderDescription(a);
                if (pn != null) {
                    pr.setName(pn);
                }
                if (pp != null) {
                    pr.setPaths(pp);
                }
                if (pd != null) {
                    pr.setDescription(pd);
                }
                prAccess = evaluateRESTAccess(prAccess, a);
            }
        } else if (wiClass != null && obj instanceof Proxy) {
            // try to get inner details from Proxy via Refl.
            try {
                List<Method> pmss = Refl.ReflImpl.getProxyMethods((Proxy) obj);
                if (pmss != null && !pmss.isEmpty()) {
                    mss = pmss.toArray(new Method[pmss.size()]);
                }
                Class pType = Refl.ReflImpl.getProxyObjectType((Proxy) obj);

                if (pType != null) {
                    for (Annotation a : pType.getAnnotations()) {
                        String pn = getProviderName(a);
                        String[] pp = getProviderPaths(a);
                        String pd = getProviderDescription(a);
                        if (pn != null) {
                            pr.setName(pn);
                        }
                        if (pp != null) {
                            pr.setPaths(pp);
                        }
                        if (pd != null) {
                            pr.setDescription(pd);
                        }
                        prAccess = evaluateRESTAccess(prAccess, a);
                    }
                }
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        pr.setAccess(prAccess);

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

        for (Method m : mss) {
            //System.out.println("->  " + m.toGenericString());
            int modifiers = m.getModifiers();
            if (!Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers) && Modifier.isPublic(modifiers)) {
                Annotation[] aa1 = m.getDeclaredAnnotations();
                Annotation[] aa2 = m.getAnnotations();

                RESTAccess ra = null;
                if (aa1 != null) {
                    for (Annotation a : aa1) {
                        ra = this.evaluateRESTAccess(ra, a);
                    }
                }
                if (aa2 != null) {
                    for (Annotation a : aa2) {
                        ra = this.evaluateRESTAccess(ra, a);
                    }
                }
                if (ra != null && pr.getAccess() != null) {
                    ra.setInstance(pr.getAccess().getInstance());
                }

                for (Annotation a : m.getDeclaredAnnotations()) {
                    if (wmClass.isAssignableFrom(a.getClass())) {
                        String operationName = getOperationName(a);
                        if (operationName == null || operationName.isEmpty()) {
                            operationName = m.getName();
                        }
                        String[] operationPaths = getOperationPaths(a);
                        if (operationPaths == null || operationPaths.length == 0 || operationPaths[0] != null && operationPaths[0].isEmpty()) {
                            operationPaths = new String[]{name2path(operationName)};
                        }

                        int c = buildMethods(
                                wms,
                                pr,
                                ra,
                                pr.getPaths(),
                                m,
                                operationName,
                                operationPaths
                        );
                    }
                }
            }
        }

        return wms;
    }

    /**
     * Creates RESTMethod for each combination of provider (optional)/operation
     * paths...
     *
     * @param wms
     * @param pr
     * @param providerPaths
     * @param m
     * @param operationName
     * @param operationPaths
     * @return
     */
    public int buildMethods(
            Map<String, List<RESTMethod>> wms,
            RESTProvider pr,
            RESTAccess ra,
            String[] providerPaths,
            Method m,
            String operationName,
            String[] operationPaths) {
        int result = 0;
        List<RESTMethod> ms = null;//new ArrayList<RESTMethod>();
        for (String operationPath : operationPaths) {
            for (String providerPath : (providerPaths != null && providerPaths.length > 0)
                    ? providerPaths
                    : new String[]{""}) {
                RESTMethod mth = new RESTMethod();
                mth.setProvider(pr);
                m.setAccessible(true);
                mth.setMethod(m);
                mth.setName(operationName);
                mth.setAccess(ra);
                String op = providerPath;
                if (op == null) {
                    op = "";
                }
                if (op.length() > 0 && !op.endsWith("/")) {
                    if (!operationName.startsWith("/")) {
                        op += "/";
                    }
                }
                if (op.endsWith("/") && operationPath.startsWith("/")) {
                    op = op.substring(0, op.length() - 1);
                }
                op += operationPath;
                //System.out.println("    : " + op);
                mth.setPath(op);
                Class[] pts = m.getParameterTypes();
                Annotation[][] pas = m.getParameterAnnotations();
                for (int i = 0; i < pts.length; i++) {
                    RESTParameter wsp = null;
                    for (Annotation pa : pas[i]) {
                        if (wpClass.isAssignableFrom(pa.getClass())) {
                            String pn = getParameterName(pa);
                            if (pn != null && !pn.isEmpty()) {
                                wsp = new RESTParameter();
                                wsp.setName(pn);
                                wsp.setType(pts[i]);
                                wsp.setOptional(isOptionalParameter(pa));
                                mth.getParams().add(wsp);
                            }
                            break;
                        }
                    }
                    // if no annotation
                    if (wsp == null) {
                        wsp = new RESTParameter();
                        wsp.setName("param" + i);
                        wsp.setType(pts[i]);
                        mth.getParams().add(wsp);
                    }
                }
                mth.setReturnType(m.getReturnType());
                result++;
                if (ms != null && ms.isEmpty()) {
                    ms.add(mth);
                } else if (ms != null && ms.get(0).getName().equals(operationName)) {
                    ms.add(mth);
                } else {
                    ms = new ArrayList<RESTMethod>();
                    ms.add(mth);
                }

                List<RESTMethod> oms = wms.get(op);
                if (oms != null) {
                    oms.addAll(ms);
                } else {
                    wms.put(op, ms);
                }
            }
        }

        return result;
    }

    /**
     * converts string to path-like string by removing leading get/set/is and
     * changing to lowercase 1st character if more than 1 available.
     *
     * @param name
     * @return
     */
    public String name2path(String name) {
        String s = name;
        if ((s.startsWith("get") || s.startsWith("set")) && s.length() > 3) {
            s = s.substring(3);
        } else if (s.startsWith("is") && s.length() > 2) {
            s = s.substring(2);
        }
        if (s.length() > 1 && Character.isUpperCase(s.charAt(0))) {
            s = s.substring(0, 1).toLowerCase() + s.substring(1);
        }
        return s;
    }

    /**
     * Returns service(method) provider name
     *
     * @param annotation
     * @return
     */
    public abstract String getProviderName(Annotation annotation);

    /**
     * Returns provider description.
     *
     * @param annotation
     * @return
     */
    public abstract String getProviderDescription(Annotation annotation);

    /**
     * Returns service root path if any
     *
     * @param annotation
     * @return the java.lang.String[]
     */
    public abstract String[] getProviderPaths(Annotation annotation);

    /**
     * Returns operation path defaulted to operation name.May return complex
     * path definition.
     *
     * @param annotation
     * @return the java.lang.String[]
     */
    public String[] getOperationPaths(Annotation annotation) {
        return new String[]{getOperationName(annotation)};
    }

    /**
     * Check if method parameter is optional.I
     *
     * @param annotation
     * @return
     */
    public boolean isOptionalParameter(Annotation annotation) {
        return false;
    }

    /**
     * Returns operation name.
     *
     * @param annotation
     * @return
     */
    public abstract String getOperationName(Annotation annotation);

    /**
     * Returns operation description.
     *
     * @param annotation
     * @return
     */
    public abstract String getOperationDescription(Annotation annotation);

    /**
     * Returns parameter name.
     *
     * @param annotation
     * @return
     */
    public abstract String getParameterName(Annotation annotation);

    /**
     * Returns parameter description.
     *
     * @param annotation
     * @return
     */
    public abstract String getParameterDescription(Annotation annotation);

    /**
     * Create or updates and returns provided RESTAccess object based on given
     * annotation.
     *
     * If annotation does not contain access info, no RESTAccess object is
     * create and no updates are applied to provided one.
     *
     * @param access
     * @param annotation
     * @return
     */
    public abstract RESTAccess evaluateRESTAccess(RESTAccess access, Annotation annotation);
}
