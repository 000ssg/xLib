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
package ssg.lib.common;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.common.Stub.StubContext.PDIR;

/**
 * Generic API stub generator.
 *
 * Stub instance provides generation of target syntax-compatible text.
 *
 * StubContext provides access to hierarchy of [A]PI - [F]unction/procedure -
 * [P]arameters - [T]ype elements. It also provides general parametrization,
 * generic evaluation of type, name, parameter direction.
 *
 * @author 000ssg
 * @param <A> API - methods/types holder
 * @param <F> F function/procedure representation
 * @param <P> P parameter
 * @param <T> data type
 */
public interface Stub<A, F, P, T> {

    String[] getTypes();

    /**
     * Define API block and present API within comment if forComment is true.
     *
     * @param helper
     * @param methods
     * @param forComment
     * @return
     */
    String openStub(StubContext<A, F, P, T> context, A api);

    /**
     * Finalize API block.
     *
     * @param helper
     * @return
     */
    String closeStub(StubContext<A, F, P, T> context);

    /**
     * Generate API method. Optional parameter "methodName" may be used to
     * override method name in API (e.g. if overloaded methods are present).
     *
     * @param helper
     * @param method
     * @param methodName
     * @return
     */
    String generateStubMethod(StubContext<A, F, P, T> context, F method, String methodName);

    String generateMethodsSeparator(StubContext<A, F, P, T> context);

    String getStubMethodTypeNameForComment(StubContext<A, F, P, T> context, T type);

    String generateStubMethodSignature(StubContext<A, F, P, T> context, F m, boolean compact);

    String generateStubMethodParametersObject(StubContext<A, F, P, T> context, F m);

    String generateStubMethodBody(StubContext<A, F, P, T> context, F m);

    default String normalizeName(String name) {
        return name != null ? name.replace(".", "_") : name;
    }

    default String generate(StubContext<A, F, P, T> context, A api) {
        StringBuilder sb = new StringBuilder();
        sb.append(openStub(context, api));

        Collection<F> methods = context.methods(api);
        Map<String, int[]> counters = new HashMap<>();

        boolean firstMethod = true;
        if (methods != null) {
            int mIdx = 0;
            for (F m : methods) {
                if (!context.isAllowed(m)) {
                    continue;
                }
                if (firstMethod) {
                    firstMethod = false;
                } else {
                    sb.append(generateMethodsSeparator(context));
                }
                String name = context.nameOf(m);
                int[] mc = counters.get(name);
                if (mc == null) {
                    mc = new int[1];
                    counters.put(name, mc);
                }
                mc[0]++;

                sb.append(generateStubMethod(context, m, (name + ((mc[0] > 1) ? mc[0] - 1 : ""))));

                mIdx++;
            }
        }
        sb.append(closeStub(context));
        return sb.toString();
    }

    public static abstract class StubContext<A, F, P, T> implements Cloneable {

        /**
         * Name of property for base URL.
         */
        public static final String BASE_URL = "baseURL";
        /**
         * Name of property for namespace.
         */
        public static final String NAMESPACE = "namespace";

        public static enum PDIR {
            in,
            out,
            in_out,
            ret
        }

        boolean generateExtendedComments = false;
        Map<String, Object> properties;
        long timestamp;

        public StubContext(String namespace) {

        }

        public StubContext(
                String baseURL,
                String namespace,
                boolean generateExtendedComments
        ) {
            setProperty(BASE_URL, baseURL);
            setProperty(NAMESPACE, namespace);
            this.generateExtendedComments = generateExtendedComments;
        }

        public String baseURL() {
            return getProperty(BASE_URL);
        }

        public String namespace() {
            return getProperty(NAMESPACE);
        }

        public boolean generateExtendedComments() {
            return generateExtendedComments;
        }

        public boolean isAllowed(F method) {
            return true;
        }

        public boolean isAllowedParameter(P p) {
            return true;
        }

        public boolean isUploadMethod(F method) {
            boolean r = false;
            List<P> params = parameters(method);
            if (params != null) {
                for (P p : params) {
                    if (p == null) {
                        continue;
                    }
                    if (type(p) instanceof String && "byte[]".equals(p) || byte[].class.isAssignableFrom(type(p).getClass())) {
                        r = true;
                        break;
                    }
                }
            }
            return r;
        }

        public Collection<P> getUploadMethodParameters(F method) {
            Collection<P> r = null;
            List<P> params = parameters(method);
            if (params != null) {
                int c = 0;
                for (P p : params) {
                    if (type(p) instanceof String && "byte[]".equals(p) || byte[].class.isAssignableFrom(type(p).getClass())) {
                        if (r == null) {
                            r = new ArrayList<>();
                        }
                        r.add(p);
                    }
                }
            }
            return r;
        }

        public <Z extends StubContext> Z setProperty(String name, Object v) {
            if (properties == null) {
                properties = new LinkedHashMap<>();
            }
            if (name != null) {
                if (v == null) {
                    if (properties.containsKey(name)) {
                        properties.remove(name);
                    }
                } else {
                    properties.put(name, v);
                }
            }
            return (Z) this;
        }

        public <T> T getProperty(String name) {
            return properties != null ? (T) properties.get(name) : null;
        }

        @Override
        public StubContext clone() throws CloneNotSupportedException {
            StubContext copy = (StubContext) super.clone();
            if (properties != null) {
                copy.properties = new LinkedHashMap<>();
                copy.properties.putAll(properties);
            }
            return copy;
        }

        ////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////// A, F, P, T
        ////////////////////////////////////////////////////////////////////////
        /**
         * Returns name of any item.
         *
         * @param obj
         * @return
         */
        public abstract String nameOf(Object obj);

        /**
         * Returns optional extra path for method.
         *
         * @param method
         * @return
         */
        public abstract String pathOf(F method);

        /**
         * List of methods for rendering.
         *
         * @param api
         * @return
         */
        public abstract List<F> methods(A api);

        /**
         * List of method parameters, optionally including return value, if any.
         *
         * @param method
         * @return
         */
        public abstract List<P> parameters(F method);

        /**
         * Parameter direction: in,out,in_out, ret.
         *
         * @param parameter
         * @return
         */
        public abstract PDIR direction(P parameter);

        /**
         * Returns function return value or null if not a function.
         *
         * @param method
         * @return
         */
        public abstract T returnType(F method);

        /**
         * Returns type of parameter.
         *
         * @param parameter
         * @return
         */
        public abstract T type(P parameter);

        ///////////////
        /**
         * Returns true if given method has parameter with given type and/or
         * direction.
         *
         * @param method
         * @param type
         * @param dir
         * @return
         */
        public boolean hasParameter(F method, T type, PDIR dir) {
            if (dir == null) {
                if (type == null) {
                    return false;
                }
                for (P p : parameters(method)) {
                    if (type.getClass().isAssignableFrom(type(p).getClass()));
                }
            } else {
                if (type == null) {
                    for (P p : parameters(method)) {
                        if (dir == direction(p)) {
                            return true;
                        }
                    }
                } else {
                    if (dir == PDIR.ret) {
                        return returnType(method) != null;
                    }
                }
            }
            return false;
        }
    }

    public abstract static class StubJavascript<A, F, P, T> implements Stub<A, F, P, T> {

        @Override
        public String openStub(StubContext<A, F, P, T> context, A api) {
            StringBuilder sb = new StringBuilder();
            // script start
            sb.append("<!-- // \n");
            sb.append("// service API: " + context.baseURL() + "\n");
            Collection<F> methods = context.methods(api);

            if (context.generateExtendedComments() && methods != null) {
                Map<String, int[]> counters = new HashMap<>();
                for (F ma : methods) {
                    if (!context.isAllowed(ma)) {
                        continue;
                    }

                    String name = context.nameOf(ma);
                    int[] mc = counters.get(name);
                    if (mc == null) {
                        mc = new int[1];
                        counters.put(name, mc);
                    }
                    mc[0]++;

                    sb.append("\n//   " + normalizeName(name + ((mc[0] > 1) ? mc[0] - 1 : "")) + ": " + generateStubMethodSignature(context, ma, true).replace("\n", "\n//   "));
                    if (context.returnType(ma) != null) {
                        sb.append(": ");
                        sb.append(context.nameOf(context.returnType(ma)));
                    }
                }
            }
            sb.append("//\n");
            sb.append("\nvar " + context.namespace() + " = {\n");
            sb.append("\n  baseURL: \"" + context.baseURL() + "/\",");
            sb.append("\n  callsDebug: undefined,");
            return sb.toString();
        }

        @Override
        public String closeStub(StubContext<A, F, P, T> context) {
            return "\n};"
                    + "\n// EOS -->\n";
        }

        @Override
        public String generateStubMethod(StubContext<A, F, P, T> context, F method, String methodName) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n  " + normalizeName((methodName != null) ? methodName : context.nameOf(method)) + ": " + generateStubMethodSignature(context, method, false).replace("\n", "\n    "));
            sb.append(generateStubMethodBody(context, method).replace("\n", "\n      "));
            return sb.toString();
        }

        @Override
        public String generateMethodsSeparator(StubContext<A, F, P, T> context) {
            return ",";
        }

        @Override
        public String getStubMethodTypeNameForComment(StubContext<A, F, P, T> context, T type) {
            return context.nameOf(type);//(type.isArray()) ? type.getComponentType().getSimpleName() + "[]" : type.getSimpleName());
        }

        @Override
        public String generateStubMethodSignature(StubContext<A, F, P, T> context, F m, boolean compact) {
            StringBuilder sb = new StringBuilder();
            sb.append("function(");
            String prefix = (compact) ? "" : "\n\t";
            if (!compact && context.returnType(m) != null) {
                sb.append("\n\t// path: " + context.pathOf(m));
                sb.append("\n\t// RETURNS: " + getStubMethodTypeNameForComment(context, context.returnType(m)));
            }
            boolean first = true;
            for (P p : context.parameters(m)) {
                if (!context.isAllowedParameter(p) || PDIR.out == context.direction(p) || PDIR.ret == context.direction(p)) {
                    continue;
                }
                if (first) {
                    first = false;
                } else {
                    sb.append(",");
                }
                if (!compact) {
                    sb.append(prefix + "// " + getStubMethodTypeNameForComment(context, context.type(p)));
                }
                sb.append(prefix + context.nameOf(p));
            }
            if (!first) {
                sb.append(",");
            }
            if (!compact) {
                sb.append(prefix + "// optional call result handler: function(url,data).");
            }
            sb.append(prefix + "done");
            sb.append(")");
            return sb.toString();
        }

        @Override
        public String generateStubMethodParametersObject(StubContext<A, F, P, T> context, F m) {
            boolean first = true;
            StringBuilder sbp = new StringBuilder();
            for (P p : context.parameters(m)) {
                if (!context.isAllowedParameter(p) || PDIR.out == context.direction(p) || PDIR.ret == context.direction(p)) {
                    continue;
                }
                if (first) {
                    first = false;
                } else {
                    sbp.append(", ");
                }
                sbp.append(context.nameOf(p) + ": " + context.nameOf(p));
            }
            return sbp.toString();
        }
    }

    public static class StubJQuery<A, F, P, T> extends StubJavascript<A, F, P, T> {

        String[] types = new String[]{"js"};

        @Override
        public String[] getTypes() {
            return types;
        }

        @Override
        public String generateStubMethodBody(StubContext<A, F, P, T> context, F m) {
            StringBuilder sb = new StringBuilder();
            String sbp = generateStubMethodParametersObject(context, m);

            sb.append("\n  {\n");
            sb.append("\n    var url=this.baseURL+'" + context.nameOf(m) + "';");

            if (context.isUploadMethod(m)) {
                Collection<P> ups = context.getUploadMethodParameters(m);
                if (ups != null) {
                    for (P up : ups) {
                        sb.append("\n    {");
                        sb.append("\n    var file = document.getElementById(" + context.nameOf(up) + ");");

                        sb.append("\n    if (file && file.files && file.files[0])");
                        sb.append("\n        file = file.files[0];");
                        sb.append("\n    if (!file || file == '')");
                        sb.append("\n        return;");

                        sb.append("\n    var fd = new FormData();");
                        sb.append("\n    fd.append(\"" + context.nameOf(up) + "\", file);");
                        sb.append("\n    // These extra params aren't necessary but show that you can include other data.");
                        sb.append("\n    //                fd.append(\"username\", \"Groucho\");");
                        sb.append("\n    //                fd.append(\"accountnum\", 123456);");
                        sb.append("\n    var xhr = new XMLHttpRequest();");
                        sb.append("\n    xhr.open('POST', url, true);");

                        sb.append("\n    xhr.upload.onprogress = function (e) {");
                        sb.append("\n        if (e.lengthComputable) {");
                        sb.append("\n            var percentComplete = (e.loaded / e.total) * 100;");
                        sb.append("\n            console.log(percentComplete + '% uploaded');");
                        sb.append("\n        }");
                        sb.append("\n    };");
                        sb.append("\n    xhr.onload = function () {");
                        sb.append("\n        if (this.status == 200) {");
                        sb.append("\n            var resp = JSON.parse(this.response);");
                        sb.append("\n            if(\"function\"==typeof(this.callsDebug)) this.callsDebug(url, resp);");
                        sb.append("\n            if (resp.status == 'OK') {");
                        sb.append("\n                if(\"function\"==typeof(done)) done(url, resp);");
                        sb.append("\n            } else {");
                        sb.append("\n            }");
                        sb.append("\n        }");
                        sb.append("\n        ;");
                        sb.append("\n    };");
                        sb.append("\n    xhr.send(fd);");
                        sb.append("\n    xhr.always(function (data) {");
                        sb.append("\n        document.getElementById('result').value = dumpObj(data);");
                        sb.append("\n      }");
                        sb.append("\n    );");
                        sb.append("\n    }");
                    }
                }
            } else {
                // simple POST execution with REST-formatted result...
                if (sbp.length() > 0) {
                    sb.append("\n    $.post(url, {" + sbp.toString() + "})");
                } else {
                    sb.append("\n    $.post(url)");
                }
                sb.append("\n      .done()");
                sb.append("\n      .fail()");
                sb.append("\n      .always(function(data) {");
                sb.append("\n        if(\"function\"==typeof(this.callsDebug)) this.callsDebug(url, data);");
                sb.append("\n        if(\"function\"==typeof(done)) done(url, data);");
                sb.append("\n      });");
            }

            sb.append("\n  }");
            return sb.toString();
        }

    }

    /**
     * Default java reflection stub context. Skips from methods all Object's
     * ones but "toString()" and all non-public methods.
     */
    public static class StubReflectionContext extends StubContext<Object, Method, Parameter, Class> {

        public StubReflectionContext(String namespace) {
            super(namespace);
        }

        public StubReflectionContext(String baseURL, String namespace, boolean generateExtendedComments) {
            super(baseURL, namespace, generateExtendedComments);
        }

        @Override
        public String nameOf(Object obj) {
            if (obj instanceof Method) {
                return ((Method) obj).getName();
            } else if (obj instanceof Parameter) {
                return ((Parameter) obj).getName();
            } else if (obj instanceof Class) {
                return ((Class) obj).getName();
            } else {
                return "instance";
            }
        }

        @Override
        public String pathOf(Method method) {
            return "";
        }

        @Override
        public List<Method> methods(Object api) {
            if (api == null) {
                return null;
            }
            List<Method> r = new ArrayList<>();
            for (Method m : api.getClass().getMethods()) {
                String mn = m.getName();
                if ("getClass".equals(mn)
                        || "equals".equals(mn)
                        || "hashCode".equals(mn)
                        || "notify".equals(mn)
                        || "notifyAll".equals(mn)
                        || "wait".equals(mn)) {
                    continue;
                }
                r.add(m);
            }
            return r;
        }

        @Override
        public List<Parameter> parameters(Method method) {
            List<Parameter> r = new ArrayList<>();
            Parameter[] ps = method.getParameters();
            if (ps != null) {
                for (Parameter p : ps) {
                    r.add(p);
                }
            }
            return r;
        }

        @Override
        public PDIR direction(Parameter parameter) {
            return parameter != null ? PDIR.in : null;
        }

        @Override
        public Class returnType(Method method) {
            Class cl = method != null ? method.getReturnType() : null;
            if (void.class == cl) {
                cl = null;
            }
            return cl;
        }

        @Override
        public Class type(Parameter parameter) {
            return parameter != null ? parameter.getType() : null;
        }
    }
}
