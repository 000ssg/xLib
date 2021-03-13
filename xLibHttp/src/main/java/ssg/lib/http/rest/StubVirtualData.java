/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
 * @author sesidoro
 */
public class StubVirtualData<T> implements VirtualData {

    T owner;
    String path;
    StubContext context;
    Map<String, Stub> stubs = new HashMap<>();
    Map<String, WR> resources = new HashMap<>();

    public StubVirtualData(T owner, String path, Stub... stubs) {
        this.owner = owner;
        this.path = path;
        if (stubs != null) {
            for (Stub st : stubs) {
                for (String t : st.getTypes()) {
                    this.stubs.put(t, st);
                }
            }
        }
    }

    public StubVirtualData(T owner, String path, String... stubs) {
        this.owner = owner;
        this.path = path;
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

    public StubVirtualData configure(String realm, String type, String path) {
        WR wr = new WR(path, realm, type);
        resources.put(wr.getPath(), wr);
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
        return path;
    }

    public Collection<WR> resources() {
        return resources.values();
    }

    public <Z extends StubContext> Z context() {
        return (Z) context;
    }

    public StubContext getContextForWR(WR wr, HttpData httpData) throws IOException {
        try {
            return context.clone()
                    .setProperty(Stub.StubContext.BASE_URL, "http" + (httpData.isSecure() ? "s" : "") + "://" + httpData.getHead().getHeader1("host") + StubVirtualData.this.path)
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
            path = StubVirtualData.this.path + "/script." + type + "?" + realm;
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
