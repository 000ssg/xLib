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
package ssg.lib.api.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import ssg.lib.api.API;
import ssg.lib.api.APIAttr;
import ssg.lib.api.APICallable;
import ssg.lib.api.APIItemCategory;
import ssg.lib.api.APIParameterDirection;
import ssg.lib.api.APIDataType;
import ssg.lib.api.APIFunction;
import ssg.lib.api.APIGroup;
import ssg.lib.api.APIParameter;
import ssg.lib.api.APIProcedure;

/**
 *
 * @author 000ssg
 */
public class Reflective_API_Builder {

    public static final String[] disabledMethodNames = {"equals", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait"};
    public static Collection<String> disabledMethods = new HashSet<String>() {
        {
            for (String s : disabledMethodNames) {
                add(s);
            }
        }
    };

    public static API buildAPI(String name, Reflective_API_Context context, Class... types) {
        API_Reflective api = new API_Reflective(name);
        if (context == null) {
            context = new Reflective_API_Context();
        }
        if (types != null) {
            for (Class type : types) {
                if (type == null || context.getFilter() != null && !context.getFilter().allowed(type, null)) {
                    continue;
                }
                String cn = type.isAnonymousClass() ? type.getName() : type.getSimpleName();
                APIGroup group = new APIGroup(cn);
                buildGroup(api, group, type, context);
                api.groups.put(cn, group);
            }
        }

        return api;
    }

    public static void buildGroup(API api, APIGroup group, Class type, Reflective_API_Context context) {
        for (Method m : type.getMethods()) {
            if (disabledMethods.contains(m.getName())) {
                continue;
            }
            if (context.getFilter() != null && !context.getFilter().allowed(type, m)) {
                continue;
            }

            boolean allowed = true;
            long options = 0;
            if (Modifier.isStatic(m.getModifiers())) {
                options |= APIProcedure.PO_STATIC;
            }

            APIProcedure f = buildMethod(api, group, type, m, context);
            if (f == null) {
                continue;
            }
        }
    }

    public static APIProcedure buildMethod(API api, APIGroup group, Class type, Method m, Reflective_API_Context context) {
        // check if valid type/method and not in disable list
        if (m == null || type == null || disabledMethods.contains(m.getName())) {
            return null;
        }
        // check if filtered out
        if (context.getFilter() != null && !context.getFilter().allowed(type, m)) {
            return null;
        }
        // check if type has this method!
        boolean found = false;
        for (Method mi : type.getMethods()) {
            if (mi.equals(m)) {
                found = true;
                break;
            }
        }
        if (!found) {
            for (Method mi : type.getDeclaredMethods()) {
                if (mi.equals(m)) {
                    found = true;
                    m.setAccessible(true);
                    break;
                }
            }
        }
        if (!found) {
            return null;
        }

        boolean allowed = true;
        long options = 0;
        if (Modifier.isStatic(m.getModifiers())) {
            options |= APIProcedure.PO_STATIC;
        }
        if (m.getReturnType() != null && m.getReturnType() != void.class) {
            // function
            APIDataType ret = buildType(api, m.getReturnType(), context);
            // if result is null (disabled?), ignore method
            if (ret == null) {
                return null;
            }

            ReflFunction f = new ReflFunction(m.getName());
            f.options = options;
            f.type = type;
            f.method = m;

            f.response = new APIParameter(null, ret);
            f.response.order = 0;
            f.response.direction = APIParameterDirection.ret;
            ret.usedIn.add(f.response);
            f.response.usedIn.add(f);

            int order = 1;
            for (java.lang.reflect.Parameter p : m.getParameters()) {
                String pn = p.getName();
                APIDataType pt = buildType(api, p.getType(), context);
                if (pt == null) {
                    allowed = false;
                    break;
                }
                APIParameter pp = new APIParameter(pn, pt);
                pp.order = order++;
                pp.direction = APIParameterDirection.in;
                pt.usedIn.add(pp);
                f.params.put(pn, pp);
                pp.usedIn.add(f);
            }

            if (!allowed) {
                return null;
            }

            if (group != null) {
                f.scope = new String[]{group.name};
                APIFunction[] ff = tryRegisterFunction(group.funcs.get(f.name), f);
                if (ff != null) {
                    group.funcs.put(f.name, ff);
                    f.usedIn.add(group);
                }
            } else {
                APIFunction[] ff = tryRegisterFunction(api.funcs.get(f.name), f);
                if (ff != null) {
                    api.funcs.put(f.name, ff);
                    f.usedIn.add(api);
                }
            }

            return f;
        } else {
            // procedure
            ReflAPIProcedure f = new ReflAPIProcedure(m.getName());
            f.options = options;
            f.type = type;
            f.method = m;

            int order = 1;
            for (java.lang.reflect.Parameter p : m.getParameters()) {
                String pn = p.getName();
                APIDataType pt = buildType(api, p.getType(), context);
                if (pt == null) {
                    allowed = false;
                    break;
                }
                APIParameter pp = new APIParameter(pn, pt);
                pp.order = order++;
                pp.direction = APIParameterDirection.in;
                pt.usedIn.add(pp);
                f.params.put(pn, pp);
                pp.usedIn.add(f);
            }

            if (!allowed) {
                return null;
            }

            if (group != null) {
                f.scope = new String[]{group.name};
                APIProcedure[] ff = tryRegisterAPIProcedure(group.procs.get(f.name), f);
                if (ff != null) {
                    group.procs.put(f.name, ff);
                    f.usedIn.add(group);
                }
            } else {
                APIProcedure[] ff = tryRegisterAPIProcedure(api.procs.get(f.name), f);
                if (ff != null) {
                    api.procs.put(f.name, ff);
                    f.usedIn.add(api);
                }
            }

            return f;
        }
    }

    static APIFunction[] tryRegisterFunction(APIFunction[] ff, APIFunction f) {
        if (f == null) {
            return null;
        }
        if (ff != null) {
            boolean found = false;
            for (APIFunction fi : ff) {
                if (fi.equals(f)) {
                    return null;
                }
            }
            ff = Arrays.copyOf(ff, ff.length + 1);
            ff[ff.length - 1] = f;
            return ff;
        } else {
            return new APIFunction[]{f};
        }
    }

    static APIProcedure[] tryRegisterAPIProcedure(APIProcedure[] ff, APIProcedure f) {
        if (f == null) {
            return null;
        }
        if (ff != null) {
            boolean found = false;
            for (APIProcedure fi : ff) {
                if (fi.equals(f)) {
                    return null;
                }
            }
            ff = Arrays.copyOf(ff, ff.length + 1);
            ff[ff.length - 1] = f;
            return ff;
        } else {
            return new APIProcedure[]{f};
        }
    }

    public static APIDataType buildType(API api, Class type, Reflective_API_Context context) {
        APIDataType dt = null;
        if (api == null || type == null || context.getFilter() != null && !context.getFilter().allowed(type, null)) {
            return dt;
        }

        String n = null;
        if (type.isArray()) {
            APIDataType pdt = buildType(api, type.getComponentType(), context);
            if (pdt != null) {
                n = pdt.name + "[]";
            }
        } else {
            n = type.isPrimitive() ? type.getSimpleName() : type.getName();
        }

        if (n == null) {
            return null;
        }

        if (!api.types.containsKey(n)) {
            if (type.isPrimitive()) {
                dt = new ReflDataType(n, type);
            } else if (type.isArray() || Collection.class.isAssignableFrom(type)) {
                dt = new ReflCollectionType(n, type);
                if (type != type.getComponentType()) {
                    ((ReflCollectionType) dt).itemType = (ReflDataType) buildType(api, type.getComponentType(), context);
                }
            } else {
                dt = new ReflObjectType(n, type);
                context.evalTypeAttributes(api, (ReflObjectType) dt);
            }
            dt.usedIn.add(api);
            api.types.put(n, dt);
        } else {
            dt = api.types.get(n);
        }
        return dt;
    }

    public static interface ReflectiveFilter {

        boolean allowed(Class cl, Method m);
    }

    public static class ReflDataType extends APIDataType {

        Class type;

        public ReflDataType(String name, Class type) {
            super(APIItemCategory.data_type, name);
            this.type = type;
            if (type == String.class) {
                mandatory = false;
            } else if (type.isPrimitive()) {
                mandatory = true;
            } else {
                mandatory = false;
            }
        }

        @Override
        public Class getJavaType() {
            return type;
        }
    }

    public static class ReflObjectType extends ReflDataType {

        Map<String, APIAttr> attrs = new LinkedHashMap<>();

        public ReflObjectType(String name, Class type) {
            super(name, type);

        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(super.toString());
            if (!attrs.isEmpty()) {
                sb.delete(sb.length() - 1, sb.length());
                sb.append("\n  attrs[" + attrs.size() + "]=");
                for (String an : attrs.keySet()) {
                    sb.append("\n    " + an + ": ");
                    sb.append(attrs.get(an).toString().replace("\n", "\n    "));
                }
                sb.append("\n}");
            }
            return sb.toString();
        }

        @Override
        public String toFQNString() {
            StringBuilder sb = new StringBuilder();
            sb.append(super.toFQNString());
            if (!attrs.isEmpty()) {
                sb.delete(sb.length() - 1, sb.length());
                sb.append("\n  attrs[" + attrs.size() + "]=");
                for (String an : attrs.keySet()) {
                    sb.append("\n    " + an + ": ");
                    sb.append(attrs.get(an).toFQNString().replace("\n", "\n    "));
                }
                sb.append("\n}");
            }
            return sb.toString();
        }

    }

    public static class ReflCollectionType extends ReflDataType {

        ReflDataType itemType;

        public ReflCollectionType(String name, Class type) {
            super(name, type);

        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(super.toString());
            if (itemType != null) {
                sb.delete(sb.length() - 1, sb.length());
                sb.append("\n  itemType=" + itemType.toString().replace("\n", "\n  "));
                sb.append("\n}");
            }
            return sb.toString();
        }

        @Override
        public String toFQNString() {
            StringBuilder sb = new StringBuilder();
            sb.append(super.toFQNString());
            if (itemType != null) {
                sb.delete(sb.length() - 1, sb.length());
                sb.append("\n  itemType=" + itemType.toFQNString().replace("\n", "\n  "));
                sb.append("\n}");
            }
            return sb.toString();
        }

    }

    public static class ReflAttr extends APIAttr {

        Field fld;
        Method setter;
        Method getter;

        public ReflAttr(String name) {
            super(name);
        }

        public ReflAttr(String name, APIDataType type) {
            super(name, type);
        }

    }

    public static class Reflective_API_Context {

        private ReflectiveFilter filter;

        public Reflective_API_Context() {
        }

        public Reflective_API_Context(ReflectiveFilter filter) {
            setFilter(filter);
        }

        /**
         * @return the filter
         */
        public ReflectiveFilter getFilter() {
            return filter;
        }

        /**
         * @param filter the filter to set
         */
        public void setFilter(ReflectiveFilter filter) {
            this.filter = filter;
        }

        public void evalTypeAttributes(API api, ReflObjectType ot) {
            Field[] fs = ot.type.getFields();
            if (fs != null && fs.length > 0) {
                for (Field f : fs) {
                    ReflAttr ra = new ReflAttr(f.getName());
                    ra.fld = f;
                    ra.type = buildType(api, f.getType(), this);
                    ot.attrs.put(ra.name, ra);
                }
            } else {
                Method[] ms = ot.type.getMethods();
                Map<String, Method[]> mms = new LinkedHashMap<>();
                for (Method m : ms) {
                    String pn = m.getName();
                    if ("getClass".equals(pn)) {
                        continue;
                    }
                    if (m.getParameterCount() == 0 && m.getReturnType() != void.class && m.getName().startsWith("get")) {
                        pn = pn.substring(3);
                        pn = pn.substring(0, 1).toLowerCase() + pn.substring(1);
                        Method[] pms = mms.get(pn);
                        if (pms == null) {
                            pms = new Method[]{m, null};
                            mms.put(pn, pms);
                        } else {
                            pms[0] = m;
                        }
                    } else if (m.getParameterCount() == 0 && m.getReturnType() != void.class && m.getName().startsWith("is")) {
                        pn = pn.substring(2);
                        pn = pn.substring(0, 1).toLowerCase() + pn.substring(1);
                        Method[] pms = mms.get(pn);
                        if (pms == null) {
                            pms = new Method[]{m, null};
                            mms.put(pn, pms);
                        } else {
                            pms[0] = m;
                        }
                    } else if (m.getParameterCount() == 1 && m.getReturnType() == void.class && m.getName().startsWith("set")) {
                        pn = pn.substring(3);
                        pn = pn.substring(0, 1).toLowerCase() + pn.substring(1);
                        Method[] pms = mms.get(pn);
                        if (pms == null) {
                            pms = new Method[]{null, m};
                            mms.put(pn, pms);
                        } else {
                            pms[1] = m;
                        }
                    }
                }
                for (String an : mms.keySet()) {
                    ReflAttr ra = new ReflAttr(an);
                    Method[] rms = mms.get(an);
                    ra.getter = rms[0];
                    ra.setter = rms[1];
                    ra.type = buildType(api, ra.getter.getReturnType(), this);
                    ot.attrs.put(ra.name, ra);
                }
            }
        }
    }

    public static class API_Reflective extends API {

        public API_Reflective(String apiName) {
            super(apiName);
        }

        @Override
        public <T extends APICallable> T createCallable(APIProcedure proc, Object context) {
            if (proc instanceof ReflAPIProcedure || proc instanceof ReflFunction) {
                if ((proc.options & APIProcedure.PO_STATIC) != 0) {
                    return (T) (Object) new ReflCallable(proc, null);
                } else {
                    if (context != null) {
                        Class type = (proc instanceof ReflAPIProcedure) ? ((ReflAPIProcedure) proc).type : ((ReflFunction) proc).type;
                        if (type != null && type.isAssignableFrom(context.getClass())) {
                            return (T) (Object) new ReflCallable(proc, context);
                        }
                    }
                }
            }
            return null;
        }
    }

    public static class ReflAPIProcedure extends APIProcedure {

        Class type;
        Method method;

        public ReflAPIProcedure(String name, String... scope) {
            super(name, scope);
        }
    }

    public static class ReflFunction extends APIFunction {

        Class type;
        Method method;

        public ReflFunction(String name, String... scope) {
            super(name, scope);
        }
    }

    public static class ReflCallable implements APICallable {

        APIProcedure proc;
        Object context;

        public ReflCallable(APIProcedure proc, Object context) {
            this.proc = proc;
            this.context = context;
        }

        @Override
        public <T extends APIProcedure> T getAPIProcedure(Object params) {
            float test = APITools.testParameters(proc, params);
            return (test != 0) ? (T) proc : null;
        }

        @Override
        public <T> T call(Map<String, Object> params) throws APIException {
            try {
                if (proc instanceof ReflAPIProcedure) {
                    Class type = ((ReflAPIProcedure) proc).type;
                    Method method = ((ReflAPIProcedure) proc).method;
                    method.invoke(context, toParamsList(method, params));
                    return (T) null;
                } else {
                    Class type = ((ReflFunction) proc).type;
                    Method method = ((ReflFunction) proc).method;
                    return (T) method.invoke(context, toParamsList(method, params));
                }
            } catch (Throwable th) {
                throw new APIException("Call error: " + proc.fqn(), th);
            }
        }

        public Object[] toParamsList(Method m, Map<String, Object> params) {
            Object[] r = null;
            java.lang.reflect.Parameter[] pp = m.getParameters();
            if (pp == null || pp.length == 0) {
            } else {
                r = new Object[pp.length];
                for (int i = 0; i < pp.length; i++) {
                    java.lang.reflect.Parameter p = pp[i];
                    if (params.containsKey(p.getName())) {
                        r[i] = params.get(p.getName());
                    }
                }
            }
            return r;
        }

    }
}
