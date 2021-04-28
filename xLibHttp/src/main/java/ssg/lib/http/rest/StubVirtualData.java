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
package ssg.lib.http.rest;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import ssg.lib.common.Stub;
import ssg.lib.common.Stub.StubContext;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.dp.HttpResourceBytes;
import ssg.lib.http.dp.HttpResourceBytes.VirtualData;

/**
 *
 * @author 000ssg
 */
public class StubVirtualData<T> implements VirtualData {

    T owner;
    // stub resource base path
    String resPath;
    // stubbed resource base path (REST entry point)
    String basePath;
    // stub data provider (base)
    StubContext context;
    // stub generators
    Map<String, Stub> stubs = new HashMap<>();
    // per-stub resources
    Map<String, WR> resources = new HashMap<>();

    public StubVirtualData(String resPath, String basePath, T owner, Stub... stubs) {
        this.owner = owner;
        this.resPath = resPath;
        this.basePath = basePath != null ? basePath : resPath;
        if (stubs != null) {
            for (Stub st : stubs) {
                for (String t : st.getTypes()) {
                    this.stubs.put(t, st);
                }
            }
        }
    }

    public StubVirtualData(String resPath, String basePath, T owner, String... stubs) {
        this.owner = owner;
        this.resPath = resPath;
        this.basePath = basePath != null ? basePath : resPath;
        if (stubs != null) {
            for (String st : stubs) {
                Stub stub = initNamedStub(st);
                if (stub != null) {
                    this.stubs.put(st, stub);
                }
            }
        }
    }

    /**
     * creates instance of stub by name. By defaults supports "js" and "jq" as
     * Stub.StubJQuery.
     *
     * @param name
     * @return
     */
    public Stub initNamedStub(String name) {
        Stub stub = null;
        if ("js".equals(name) || "jq".equals(name)) {
            stub = new Stub.StubJQuery();
        } else if ("jw".equals(name)) {
            stub = new Stub.StubJQuery();
        }
        return stub;
    }

    public StubVirtualData configure(StubContext context) {
        this.context = context;
        return this;
    }

    public StubVirtualData configure(String realm, String... types) {
        if (types != null) {
            for (String type : types) {
                WR wr = new WR(realm, type);
                resources.put(wr.getPath(), wr);
            }
        }
        return this;
    }

    public long timestamp(String realm) {
        return 0;
    }

    @Override
    public byte[] get(HttpResourceBytes owner, HttpData httpData) {
        byte[] data = null;
        WR wr = resources.get(owner.path());
        if (wr != null) {
            data = wr.data;
            long timestamp = timestamp(wr.realm);
            if (data == null || timestamp == 0 || timestamp > owner.timestamp()) {
                synchronized (this) {
                    for (WR wri : resources.values()) {
                        try {
                            wri.update(httpData);
                        } catch (IOException ioex) {
                            ioex.printStackTrace();
                        }
                    }
                    owner.timestamp(timestamp);
                }
                data = wr.data;
            }
        }
        return data;
    }

    public T owner() {
        return owner;
    }

    public String path() {
        return resPath;
    }

    public Collection<WR> resources() {
        return resources.values();
    }

    public <Z extends StubContext> Z context() {
        return (Z) context;
    }

    /**
     * Prepare StubContext (by cloning base) for resource/request pair. By
     * default builds BASE_URL property (as combination of HttpData, stub base
     * and realm and namespace (as resource realm/type combination).
     *
     * @param wr
     * @param httpData
     * @return
     * @throws IOException
     */
    public StubContext getContextForWR(WR wr, HttpData httpData) throws IOException {
        try {
            return context.clone()
                    .setProperty(Stub.StubContext.BASE_URL, "http" + (httpData.isSecure() ? "s" : "") + "://" + httpData.getHead().getHeader1("host") + StubVirtualData.this.basePath + "/" + wr.realm)
                    .setProperty(Stub.StubContext.NAMESPACE, wr.realm + "_" + wr.type) //                    .setProperty("wampRealm", wr.realm)
                    ;
        } catch (Throwable th) {
            if (th instanceof IOException) {
                throw (IOException) th;
            }
            throw new IOException("Failed to prepare context for building resource: " + (wr != null ? wr.getPath() : "<none>"), th);
        }
    }

    public <Z> Z getObjectForWR(WR wr, HttpData httpData) throws IOException {
        return (Z) owner;
    }

    public class WR {

        public String path;
        public String realm;
        public String type;
        public byte[] data;
        public long updated;

        public WR(String realm, String type) {
            this.realm = realm;
            this.type = type;
            path = StubVirtualData.this.resPath + (realm != null && !realm.isEmpty() ? "/" + realm : "") + "/script." + type;
        }

        public WR(String realm, String type, String path) {
            this.path = path;
            this.realm = realm;
            this.type = type;
        }

        public String getPath() {
            return path;
        }

        public void update(HttpData httpData) throws IOException {
            try {
                Stub stub = stubs.get(type);
                String text = stub.generate(getContextForWR(this, httpData), getObjectForWR(this, httpData));
                data = text.getBytes("UTF-8");
            } catch (Throwable th) {
                if (th instanceof IOException) {
                    throw (IOException) th;
                }
                throw new IOException("Failed to update resource: " + getPath(), th);
            }
        }
    }
}
