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

import java.io.IOException;
import ssg.lib.common.Stub;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.rest.StubVirtualData;
import ssg.lib.wamp.features.WAMP_FP_Reflection.RR;
import ssg.lib.wamp.nodes.WAMPRouter;

/**
 * Correction for StubVirtualData.
 *
 * Fix for stub context initialization by adding wamp-specific properties.
 *
 * Registration of "wamp"'s StubWAMP implementation to generate
 * autobahn.js-based client javascript.
 *
 * @author 000ssg
 */
public class StubWAMPVirtualData extends StubVirtualData<WAMPRouter> {

    public StubWAMPVirtualData(String resPath, String basePath, WAMPRouter owner, Stub... stubs) {
        super(resPath, basePath, owner, stubs);
    }

    public StubWAMPVirtualData(String resPath, String basePath, WAMPRouter owner, String... stubs) {
        super(resPath, basePath, owner, stubs);
    }

    @Override
    public StubWAMPVirtualData configure(Stub.StubContext context) {
        return (StubWAMPVirtualData) super.configure(context);
    }

    @Override
    public StubWAMPVirtualData configure(String realm, String... types) {
        return (StubWAMPVirtualData) super.configure(realm, types); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Stub initNamedStub(String name) {
        Stub r = super.initNamedStub(name);
        if (r == null && "wamp".equals(name)) {
            r = new StubWAMP();
        }
        return r;
    }

    @Override
    public <Z> Z getObjectForWR(WR wr, HttpData httpData) throws IOException {
        return (Z) owner().find(wr.realm);
    }

    @Override
    public Stub.StubContext getContextForWR(WR wr, HttpData httpData) throws IOException {
        return super.getContextForWR(wr, httpData)
                .setProperty("wampURI", "ws" + (httpData.isSecure() ? "s" : "") + "://" + httpData.getHead().getHeader1("host") + path())
                .setProperty("wampRealm", wr.realm);
    }

    @Override
    public long timestamp(String realm) {
        RR rr = StubWAMPReflectionContext.rr(owner().find(realm));
        return rr != null ? rr.timestamp() : 0;
    }

}
