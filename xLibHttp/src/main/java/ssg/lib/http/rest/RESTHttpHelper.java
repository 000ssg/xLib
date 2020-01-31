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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import ssg.lib.http.RAT;
import ssg.lib.http.base.HttpData;

/**
 * Generates client-friendly RESTMethods' call API (e.g. javascript code to
 * execute).
 *
 * @author 000ssg
 */
public class RESTHttpHelper {

    Map<String, APIType> apiTypes = new LinkedHashMap<>();

    public RESTHttpHelper() {
        addAPITypes(new JS_JQuery());
    }

    public RESTHttpHelper(APIType... types) {
        addAPITypes(types);
    }

    public RESTHttpHelper addAPITypes(APIType... types) {
        if (types != null) {
            for (APIType at : types) {
                if (at != null && at.getTypes() != null) {
                    for (String t : at.getTypes()) {
                        if (t != null) {
                            apiTypes.put(t, at);
                        }
                    }
                }
            }
        }
        return this;
    }

    /**
     * Returns true if method can/should handle upload event -> accepts as input
     * byte[] or input stream.
     *
     * @param m
     * @return
     */
    public boolean isUploadMethod(RESTMethod m) {
        return getUploadMethodParameterOrder(m) >= 0;
    }

    /**
     * Returns order of method parameter for upload data (byte[] or input
     * stream)
     *
     * @param m
     * @return
     */
    public int getUploadMethodParameterOrder(RESTMethod m) {
        if (m != null && m.getMethod() != null && m.getMethod().getParameterTypes().length > 0) {
            int idx = 0;
            for (Class cl : m.getMethod().getParameterTypes()) {
                if (InputStream.class == cl) {
                    return idx;
                } else if (byte[].class == cl) {
                    return idx;
                }
                idx++;
            }
        }
        return -1;
    }

    /**
     * Returns true if method forces response for download (returns byte[] or
     * accepts output stream).
     *
     * @param m
     * @return
     */
    public boolean isDownloadMethod(RESTMethod m) {
        if (byte[].class == m.getMethod().getReturnType()) {
            return true;
        }
        return getDownloadMethodParameterOrder(m) >= 0;
    }

    /**
     * Returns order of parameter for writing download context (output stream).
     * If returns -1 then still may be download-oriented method by returning
     * byte[]!.
     *
     * @param m
     * @return
     */
    public int getDownloadMethodParameterOrder(RESTMethod m) {
        if (m != null && m.getMethod() != null && byte[].class == m.getMethod().getReturnType()) {
            return -1;
        }
        if (m != null && m.getMethod() != null && m.getMethod().getParameterTypes().length > 0) {
            int idx = 0;
            for (Class cl : m.getMethod().getParameterTypes()) {
                if (OutputStream.class == cl) {
                    return idx;
                }
                idx++;
            }
        }
        return -1;
    }

    public boolean isMethodAllowed(RESTMethod method, RAT rat) {
        if (method != null && method.getAccess() != null) {
            if (rat == null || !method.getAccess().test(rat)) {
                return false;
            }
        }
        return true;
    }

//    /**
//     * Prepares type name for adding to generated API method signature comments.
//     *
//     * @param type
//     * @return
//     */
//    public String getAPIMethodTypeNameForComment(Class type) {
//        return ((type.isArray()) ? type.getComponentType().getSimpleName() + "[]" : type.getSimpleName());
//    }
//
//    /**
//     * Generates type-specific method API signature. Defaults to js ->
//     * javascript.
//     *
//     * @param type
//     * @param m
//     * @param mIdx
//     * @return
//     */
//    public String generateAPIMethodSignature(String type, RESTMethod m, boolean compact) {
//        StringBuilder sb = new StringBuilder();
//        sb.append("function(");
//        String prefix = (compact) ? "" : "\n\t";
//        if (!compact && m.getReturnType() != null) {
//            sb.append("\n\t// RETURNS: " + getAPIMethodTypeNameForComment(m.getReturnType()));
//        }
//        boolean first = true;
//        for (RESTParameter p : m.getParams()) {
//            if (HttpData.class.isAssignableFrom(p.getType()) || p.getInOut() != null && p.getInOut() == RESTParameter.IN_OUT.OUT) {
//                continue;
//            }
//            if (first) {
//                first = false;
//            } else {
//                sb.append(",");
//            }
//            if (!compact) {
//                sb.append(prefix + "// " + getAPIMethodTypeNameForComment(p.getType()));
//            }
//            sb.append(prefix + p.getName());
//        }
//        if (!first) {
//            sb.append(",");
//        }
//        if (!compact) {
//            sb.append(prefix + "// optional call result handler: function(url,data).");
//        }
//        sb.append(prefix + "done");
//        sb.append(")");
//        return sb.toString();
//    }
//
//    /**
//     * Generates type-specific method API parameters object. Defaults to js ->
//     * javascript.
//     *
//     * @param m
//     * @return
//     */
//    public String generateAPIMethodParametersObject(String type, RESTMethod m) {
//        boolean first = true;
//        StringBuilder sbp = new StringBuilder();
//        for (RESTParameter p : m.getParams()) {
//            if (HttpData.class.isAssignableFrom(p.getType()) || p.getInOut() != null && p.getInOut() == RESTParameter.IN_OUT.OUT) {
//                continue;
//            }
//            if (first) {
//                first = false;
//            } else {
//                sbp.append(", ");
//            }
//            sbp.append(p.getName() + ": " + p.getName());
//        }
//        return sbp.toString();
//    }
//
//    /**
//     * Generates type-specific method API implementation. Defaults to js ->
//     * javascript.
//     *
//     * @param type
//     * @param m
//     * @return
//     */
//    public String generateAPIMethodBody(String type, RESTMethod m) {
//        StringBuilder sb = new StringBuilder();
//        String sbp = this.generateAPIMethodParametersObject(type, m);
//
//        sb.append("\n  {\n");
//        sb.append("\n    var url=this.baseURL+'" + m.getName() + "';");
//
//        if (isUploadMethod(m)) {
//            int upOrder = this.getUploadMethodParameterOrder(m);
//            RESTParameter up = m.getParams().get(upOrder);
//            sb.append("\n    var file = document.getElementById(" + up.getName() + ");");
//
//            sb.append("\n    if (file && file.files && file.files[0])");
//            sb.append("\n        file = file.files[0];");
//            sb.append("\n    if (!file || file == '')");
//            sb.append("\n        return;");
//
//            sb.append("\n    var fd = new FormData();");
//            sb.append("\n    fd.append(\"" + up.getName() + "\", file);");
//            sb.append("\n    // These extra params aren't necessary but show that you can include other data.");
//            sb.append("\n    //                fd.append(\"username\", \"Groucho\");");
//            sb.append("\n    //                fd.append(\"accountnum\", 123456);");
//            sb.append("\n    var xhr = new XMLHttpRequest();");
//            sb.append("\n    xhr.open('POST', url, true);");
//
//            sb.append("\n    xhr.upload.onprogress = function (e) {");
//            sb.append("\n        if (e.lengthComputable) {");
//            sb.append("\n            var percentComplete = (e.loaded / e.total) * 100;");
//            sb.append("\n            console.log(percentComplete + '% uploaded');");
//            sb.append("\n        }");
//            sb.append("\n    };");
//            sb.append("\n    xhr.onload = function () {");
//            sb.append("\n        if (this.status == 200) {");
//            sb.append("\n            var resp = JSON.parse(this.response);");
//            sb.append("\n            if(\"function\"==typeof(this.callsDebug)) this.callsDebug(url, resp);");
//            sb.append("\n            if (resp.status == 'OK') {");
//            sb.append("\n                if(\"function\"==typeof(done)) done(url, resp);");
//            sb.append("\n            } else {");
//            sb.append("\n            }");
//            sb.append("\n        }");
//            sb.append("\n        ;");
//            sb.append("\n    };");
//            sb.append("\n    xhr.send(fd);");
//            sb.append("\n    xhr.always(function (data) {");
//            sb.append("\n        document.getElementById('result').value = dumpObj(data);");
//            sb.append("\n      }");
//            sb.append("\n    );");
//        } else {
//            // simple POST execution with REST-formatted result...
//            if (sbp.length() > 0) {
//                sb.append("\n    $.post(url, {" + sbp.toString() + "})");
//            } else {
//                sb.append("\n    $.post(url)");
//            }
//            sb.append("\n      .done()");
//            sb.append("\n      .fail()");
//            sb.append("\n      .always(function(data) {");
//            sb.append("\n        if(\"function\"==typeof(this.callsDebug)) this.callsDebug(url, data);");
//            sb.append("\n        if(\"function\"==typeof(done)) done(url, data);");
//            sb.append("\n      });");
//        }
//
//        sb.append("\n  }");
//        return sb.toString();
//    }
    public APIType getDefaultAPIType() {
        APIType t = apiTypes.get("js");
        if (t == null && !apiTypes.isEmpty()) {
            t = apiTypes.values().iterator().next();
        }
        return t;
    }

    public String generateAPI(
            RAT rat,
            String type,
            String urlBase,
            Collection<RESTMethod[]> methods,
            String namespace,
            String operation) {

        APIType at = apiTypes.get(type);
        if (at == null) {
            at = getDefaultAPIType();
        }
        if (at == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(at.openAPI(this, urlBase, namespace, rat, methods, true));

        boolean firstMethod = true;
        if (methods != null) {
            Collection<RESTMethod[]> ms = methods;
            for (RESTMethod[] ma : ms) {
                int mIdx = 0;
                for (RESTMethod m : ma) {
                    if (!at.isAllowed(this, m, rat)) {
                        continue;
                    }
                    if (firstMethod) {
                        firstMethod = false;
                    } else {
                        sb.append(at.generateMethodsSeparator(this));
                    }
                    sb.append(at.generateAPIMethod(this, m, (m.getName() + ((mIdx > 0) ? mIdx : ""))));

                    mIdx++;
                }
            }
        }
        sb.append(at.closeAPI(this));
        return sb.toString();
    }

//    public String generateAPI0(
//            RAT rat,
//            String type,
//            String urlBase,
//            Collection<RESTMethod[]> methods,
//            String namespace,
//            String operation) {
//
//        Collection<RESTMethod> allowed = new HashSet<>();
//
//        StringBuilder sb = new StringBuilder();
//        // script start
//        sb.append("<!-- // \n");
//        //sb.append("// service REST API: " + ctx.getUrlBase() + "\n");
//        sb.append("// service REST API: " + urlBase + "\n");
//        {
//            //for (String mn : RESTMethod.getRESTMethodNames(ctx.serviceClass())) {
//            Collection<RESTMethod[]> ms = methods; //RESTMethod.getRESTMethod(ctx.serviceClass(), mn);
//            for (RESTMethod[] ma : ms) {
//                int mIdx = 0;
//                for (RESTMethod m : ma) {
//                    if (m != null && m.getAccess() != null) {
//                        if (rat == null || !m.getAccess().test(rat)) {
//                            continue;
//                        }
//                    }
//                    allowed.add(m);
//                    sb.append("\n//   " + (m.getName() + ((mIdx > 0) ? mIdx : "")) + ": " + this.generateAPIMethodSignature("js", m, true).replace("\n", "\n//   "));
//                    mIdx++;
//                }
//            }
//            //}
//        }
//        sb.append("//\n");
//        sb.append("\nvar REST_" + namespace + " = {\n");
//        //sb.append("\n  baseURL: \"" + ctx.getUrlBase() + "REST/" + operation + "/\",");
//        sb.append("\n  baseURL: \"" + urlBase + "/" + ((operation != null) ? operation + "/" : "") + "\",");
//        sb.append("\n  callsDebug: undefined,");
//        boolean firstMethod = true;
//        if (methods != null) {
//            Collection<RESTMethod[]> ms = methods;
//            for (RESTMethod[] ma : ms) {
//                int mIdx = 0;
//                for (RESTMethod m : ma) {
//                    if (!allowed.contains(m)) {
//                        continue;
//                    }
//                    if (firstMethod) {
//                        firstMethod = false;
//                    } else {
//                        sb.append(",");
//                    }
//                    sb.append("\n  " + (m.getName() + ((mIdx > 0) ? mIdx : "")) + ": " + this.generateAPIMethodSignature("js", m, false).replace("\n", "\n    "));
//                    sb.append(this.generateAPIMethodBody("js", m).replace("\n", "\n      "));
//
//                    mIdx++;
//                }
//            }
//        }
//        sb.append("\n};");
//        sb.append("\n// EOS -->\n");
//        return sb.toString();
//    }
    public static interface APIType {

        String[] getTypes();

        /**
         * Define API block and present API within comment if forComment is
         * true.
         *
         * @param helper
         * @param methods
         * @param forComment
         * @return
         */
        String openAPI(RESTHttpHelper helper, String urlBase, String namespace, RAT rat, Collection<RESTMethod[]> methods, boolean withExtendedComment);

        /**
         * Finalize API block.
         *
         * @param helper
         * @return
         */
        String closeAPI(RESTHttpHelper helper);

        boolean isAllowed(RESTHttpHelper helper, RESTMethod method, RAT rat);

        /**
         * Generate API method. Optional parameter "methodName" may be used to
         * override method name in API (e.g. if overloaded methods are present).
         *
         * @param helper
         * @param method
         * @param methodName
         * @return
         */
        String generateAPIMethod(RESTHttpHelper helper, RESTMethod method, String methodName);

        String generateMethodsSeparator(RESTHttpHelper helper);

        String getAPIMethodTypeNameForComment(RESTHttpHelper helper, Class type);

        String generateAPIMethodSignature(RESTHttpHelper helper, RESTMethod m, boolean compact);

        String generateAPIMethodParametersObject(RESTHttpHelper helper, RESTMethod m);

        String generateAPIMethodBody(RESTHttpHelper helper, RESTMethod m);
    }

    public abstract static class JS_API implements APIType {

        @Override
        public String openAPI(RESTHttpHelper helper, String urlBase, String namespace, RAT rat, Collection<RESTMethod[]> methods, boolean withExtendedComment) {
            StringBuilder sb = new StringBuilder();
            // script start
            sb.append("<!-- // \n");
            //sb.append("// service REST API: " + ctx.getUrlBase() + "\n");
            sb.append("// service REST API: " + urlBase + "\n");
            if (withExtendedComment && methods != null) {
                Collection<RESTMethod[]> ms = methods;
                for (RESTMethod[] ma : ms) {
                    int mIdx = 0;
                    for (RESTMethod m : ma) {
                        if (!isAllowed(helper, m, rat)) {
                            continue;
                        }
                        sb.append("\n//   " + (m.getName() + ((mIdx > 0) ? mIdx : "")) + ": " + generateAPIMethodSignature(helper, m, true).replace("\n", "\n//   "));
                        mIdx++;
                    }
                }
            }
            sb.append("//\n");
            sb.append("\nvar REST_" + namespace + " = {\n");
            //sb.append("\n  baseURL: \"" + urlBase + "/" + ((operation != null) ? operation + "/" : "") + "\",");
            sb.append("\n  baseURL: \"" + urlBase + "/\",");
            sb.append("\n  callsDebug: undefined,");
            return sb.toString();
        }

        @Override
        public String closeAPI(RESTHttpHelper helper) {
            return "\n};"
                    + "\n// EOS -->\n";
        }

        @Override
        public boolean isAllowed(RESTHttpHelper helper, RESTMethod method, RAT rat) {
            return helper.isMethodAllowed(method, rat);
        }

        @Override
        public String generateAPIMethod(RESTHttpHelper helper, RESTMethod method, String methodName) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n  " + ((methodName != null) ? methodName : method.getName()) + ": " + generateAPIMethodSignature(helper, method, false).replace("\n", "\n    "));
            sb.append(generateAPIMethodBody(helper, method).replace("\n", "\n      "));
            return sb.toString();
        }

        @Override
        public String generateMethodsSeparator(RESTHttpHelper helper) {
            return ",";
        }

        @Override
        public String getAPIMethodTypeNameForComment(RESTHttpHelper helper, Class type) {
            return ((type.isArray()) ? type.getComponentType().getSimpleName() + "[]" : type.getSimpleName());
        }

        @Override
        public String generateAPIMethodSignature(RESTHttpHelper helper, RESTMethod m, boolean compact) {
            StringBuilder sb = new StringBuilder();
            sb.append("function(");
            String prefix = (compact) ? "" : "\n\t";
            if (!compact && m.getReturnType() != null) {
                sb.append("\n\t// RETURNS: " + getAPIMethodTypeNameForComment(helper, m.getReturnType()));
            }
            boolean first = true;
            for (RESTParameter p : m.getParams()) {
                if (HttpData.class.isAssignableFrom(p.getType()) || p.getInOut() != null && p.getInOut() == RESTParameter.IN_OUT.OUT) {
                    continue;
                }
                if (first) {
                    first = false;
                } else {
                    sb.append(",");
                }
                if (!compact) {
                    sb.append(prefix + "// " + getAPIMethodTypeNameForComment(helper, p.getType()));
                }
                sb.append(prefix + p.getName());
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
        public String generateAPIMethodParametersObject(RESTHttpHelper helper, RESTMethod m) {
            boolean first = true;
            StringBuilder sbp = new StringBuilder();
            for (RESTParameter p : m.getParams()) {
                if (HttpData.class.isAssignableFrom(p.getType()) || p.getInOut() != null && p.getInOut() == RESTParameter.IN_OUT.OUT) {
                    continue;
                }
                if (first) {
                    first = false;
                } else {
                    sbp.append(", ");
                }
                sbp.append(p.getName() + ": " + p.getName());
            }
            return sbp.toString();
        }

//        @Override
//        public String generateAPIMethodBody(RESTHttpHelper helper, RESTMethod m) {
//            StringBuilder sb = new StringBuilder();
//            String sbp = generateAPIMethodParametersObject(helper, m);
//
//            sb.append("\n  {\n");
//            sb.append("\n    var url=this.baseURL+'" + m.getName() + "';");
//
//            if (helper.isUploadMethod(m)) {
//                int upOrder = helper.getUploadMethodParameterOrder(m);
//                RESTParameter up = m.getParams().get(upOrder);
//                sb.append("\n    var file = document.getElementById(" + up.getName() + ");");
//
//                sb.append("\n    if (file && file.files && file.files[0])");
//                sb.append("\n        file = file.files[0];");
//                sb.append("\n    if (!file || file == '')");
//                sb.append("\n        return;");
//
//                sb.append("\n    var fd = new FormData();");
//                sb.append("\n    fd.append(\"" + up.getName() + "\", file);");
//                sb.append("\n    // These extra params aren't necessary but show that you can include other data.");
//                sb.append("\n    //                fd.append(\"username\", \"Groucho\");");
//                sb.append("\n    //                fd.append(\"accountnum\", 123456);");
//                sb.append("\n    var xhr = new XMLHttpRequest();");
//                sb.append("\n    xhr.open('POST', url, true);");
//
//                sb.append("\n    xhr.upload.onprogress = function (e) {");
//                sb.append("\n        if (e.lengthComputable) {");
//                sb.append("\n            var percentComplete = (e.loaded / e.total) * 100;");
//                sb.append("\n            console.log(percentComplete + '% uploaded');");
//                sb.append("\n        }");
//                sb.append("\n    };");
//                sb.append("\n    xhr.onload = function () {");
//                sb.append("\n        if (this.status == 200) {");
//                sb.append("\n            var resp = JSON.parse(this.response);");
//                sb.append("\n            if(\"function\"==typeof(this.callsDebug)) this.callsDebug(url, resp);");
//                sb.append("\n            if (resp.status == 'OK') {");
//                sb.append("\n                if(\"function\"==typeof(done)) done(url, resp);");
//                sb.append("\n            } else {");
//                sb.append("\n            }");
//                sb.append("\n        }");
//                sb.append("\n        ;");
//                sb.append("\n    };");
//                sb.append("\n    xhr.send(fd);");
//                sb.append("\n    xhr.always(function (data) {");
//                sb.append("\n        document.getElementById('result').value = dumpObj(data);");
//                sb.append("\n      }");
//                sb.append("\n    );");
//            } else {
//                // simple POST execution with REST-formatted result...
//                if (sbp.length() > 0) {
//                    sb.append("\n    $.post(url, {" + sbp.toString() + "})");
//                } else {
//                    sb.append("\n    $.post(url)");
//                }
//                sb.append("\n      .done()");
//                sb.append("\n      .fail()");
//                sb.append("\n      .always(function(data) {");
//                sb.append("\n        if(\"function\"==typeof(this.callsDebug)) this.callsDebug(url, data);");
//                sb.append("\n        if(\"function\"==typeof(done)) done(url, data);");
//                sb.append("\n      });");
//            }
//
//            sb.append("\n  }");
//            return sb.toString();
//        }
    }

    public static class JS_JQuery extends JS_API {

        String[] types = new String[]{"js"};

        @Override
        public String[] getTypes() {
            return types;
        }

        @Override
        public String generateAPIMethodBody(RESTHttpHelper helper, RESTMethod m) {
            StringBuilder sb = new StringBuilder();
            String sbp = generateAPIMethodParametersObject(helper, m);

            sb.append("\n  {\n");
            sb.append("\n    var url=this.baseURL+'" + m.getName() + "';");

            if (helper.isUploadMethod(m)) {
                int upOrder = helper.getUploadMethodParameterOrder(m);
                RESTParameter up = m.getParams().get(upOrder);
                sb.append("\n    var file = document.getElementById(" + up.getName() + ");");

                sb.append("\n    if (file && file.files && file.files[0])");
                sb.append("\n        file = file.files[0];");
                sb.append("\n    if (!file || file == '')");
                sb.append("\n        return;");

                sb.append("\n    var fd = new FormData();");
                sb.append("\n    fd.append(\"" + up.getName() + "\", file);");
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
}
