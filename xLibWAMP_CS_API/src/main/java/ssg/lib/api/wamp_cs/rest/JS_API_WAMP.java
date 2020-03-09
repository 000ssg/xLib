/*
 * The MIT License
 *
 * Copyright 2020 000ssg.
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
package ssg.lib.api.wamp_cs.rest;

import java.util.Collection;
import ssg.lib.http.RAT;
import ssg.lib.http.rest.RESTHttpHelper;
import ssg.lib.http.rest.RESTHttpHelper.JS_API;
import ssg.lib.http.rest.RESTMethod;
import ssg.lib.http.rest.RESTParameter;

/**
 * JavaScript API generator for WAMP calls wrapper using "autobahn.js" connection.
 *
 * @author 000ssg
 */
public class JS_API_WAMP extends JS_API {

    String[] types = new String[]{"wamp"};
    WAMP_Context defaultWAMP;

    public JS_API_WAMP(WAMP_Context wamp) {
        this.defaultWAMP = wamp;
    }

    @Override
    public String[] getTypes() {
        return types;
    }

    public WAMP_Context getWAMP(RESTHttpHelper helper, String urlBase, String namespace, RAT rat) {
        return this.defaultWAMP;
    }

    @Override
    public String openAPI(RESTHttpHelper helper, String urlBase, String namespace, RAT rat, Collection<RESTMethod[]> methods, boolean withExtendedComment) {
        WAMP_Context wamp = getWAMP(helper, urlBase, namespace, rat);
        String s = super.openAPI(helper, urlBase, namespace, rat, methods, withExtendedComment);
        s += "\n  caller: new autobahn.Connection({url: \"" + wamp.getURI() + "\", realm: \"" + wamp.getRealm() + "\"}),";
        return s;
    }

    @Override
    public boolean isAllowed(RESTHttpHelper helper, RESTMethod method, RAT rat) {
        return super.isAllowed(helper, method, rat) && method != null;// && method.getName().endsWith(".REVISION");
    }

    @Override
    public String generateAPIMethod(RESTHttpHelper helper, RESTMethod method, String methodName) {
        if (methodName == null) {
            methodName = method.getName();
        }
        if (methodName.contains(".")) {
            methodName = methodName.replace(".", "_");
        }
        return super.generateAPIMethod(helper, method, methodName); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String generateAPIMethodBody(RESTHttpHelper helper, RESTMethod m) {
        StringBuilder sb = new StringBuilder();
        String sbp = generateAPIMethodParametersObject(helper, m);

        sb.append("\n  {\n");
        sb.append("      this.caller.session.call('" + m.getName() + "', [], {");
        boolean first = true;
        for (RESTParameter rp : m.getParams()) {
            if (rp.getInOut() != RESTParameter.IN_OUT.OUT) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                sb.append(rp.getName() + ": " + rp.getName());
            }
        }
        sb.append("}).then(function(result){");
        sb.append("\n        if(\"function\"==typeof(this.callsDebug)) this.callsDebug('" + m.getName() + "', result, null);");
        sb.append("\n        if(\"function\"==typeof(done)) done('" + m.getName() + "', result);");
        sb.append("\n    }, function(error){ if(\"function\"==typeof(done)) done('" + m.getName() + "', null, error);});");
        sb.append("\n  }");
        return sb.toString();
    }

    /**
     * Provides WAMP configuration parameters for API.
     */
    public static interface WAMP_Context {

        String getURI();

        String getRealm();
    }
}
