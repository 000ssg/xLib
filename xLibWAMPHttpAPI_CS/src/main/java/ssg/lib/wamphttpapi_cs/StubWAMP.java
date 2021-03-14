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
package ssg.lib.wamphttpapi_cs;

import java.util.Map;
import ssg.lib.common.Stub;
import ssg.lib.common.Stub.StubContext.PDIR;
import ssg.lib.wamp.WAMPRealm;

/**
 * JavaScript API generator for WAMP calls wrapper using "autobahn.js"
 * connection.
 *
 * @author 000ssg
 */
public class StubWAMP extends Stub.StubJavascript<WAMPRealm, Map, Map, String> {

    String[] types = new String[]{"wamp"};

    public StubWAMP() {
    }

    @Override
    public String[] getTypes() {
        return types;
    }

    @Override
    public String openStub(StubContext<WAMPRealm, Map, Map, String> context, WAMPRealm api) {
        return super.openStub(context, api)
                + "\n  caller: new autobahn.Connection({url: \"" + context.getProperty("wampURI") + "\", realm: \"" + context.getProperty("wampRealm") + "\"}),";

    }

    @Override
    public String generateStubMethodBody(StubContext<WAMPRealm, Map, Map, String> context, Map m) {
        StringBuilder sb = new StringBuilder();
        String sbp = generateStubMethodParametersObject(context, m);

        sb.append("\n  {\n");
        sb.append("      this.caller.session.call('" + context.nameOf(m) + "', [], {");
        boolean first = true;
        for (Map rp : context.parameters(m)) {
            if (context.direction(rp) != PDIR.out) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                sb.append(context.nameOf(rp) + ": " + context.nameOf(rp));
            }
        }
        String mn=context.nameOf(m);
        sb.append("}).then(function(result){");
        sb.append("\n        if(\"function\"==typeof(this.callsDebug)) this.callsDebug('" + mn + "', result, null);");
        sb.append("\n        if(\"function\"==typeof(done)) done('" + mn + "', result);");
        sb.append("\n    }, function(error){ if(\"function\"==typeof(done)) done('" + mn + "', null, error);});");
        sb.append("\n  }");
        return sb.toString();
    }
}
