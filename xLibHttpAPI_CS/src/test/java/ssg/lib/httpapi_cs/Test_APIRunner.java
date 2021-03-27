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
package ssg.lib.httpapi_cs;

import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import ssg.lib.api.API_Publisher;
import ssg.lib.api.util.Reflective_API_Builder;
import ssg.lib.common.CommonTools;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.rest.StubVirtualData;
import ssg.lib.httpapi_cs.APIRunner.APIGroup;

/**
 *
 * @author 000ssg
 */
public class Test_APIRunner {

    public static class DemoHW {

        public long time() {
            return System.currentTimeMillis();
        }

        public String getHello(String who) {
            return "Hello, " + who + "!";
        }
    }

    public static void main(String... args) throws Exception {
        int port = 30001;

        APIRunner r = new APIRunner(new HttpApplication("A", "/app"), new APIStatistics().createChild(null, "test-apirunner"))
                .configureHttp(port)
                .configureREST("rest")
                .configureAPI("demo", "test", new API_Publisher()
                        .configure(Reflective_API_Builder.buildAPI("test", null, DemoHW.class))
                        .configureContext(new DemoHW())
                );
        APIGroup ag = (APIGroup) ((Map) r.getAPIGroups().get("demo")).values().iterator().next();
        String router_root = r.getApp() != null ? r.getApp().getRoot() + "/" : "/";
        String rest_root = "/app/rest";
        r.configureStub(new StubVirtualData(router_root.substring(0, router_root.length() - 1), rest_root, ag.apis.getAPI("test"), "js", "jw")
                .configure(new StubAPIContext(null, null, true))
                .configure("test", "js", "jw"));

        r.start();
        try {
            for (String s : new String[]{
                "http://localhost:" + port + "/app/rest/test/test.DemoHW.getHello?who=a",
                "http://localhost:" + port + "/app/rest/test/test.DemoHW.time",
                "http://localhost:" + port + "/app/test/script.js",}) {
                long started = System.nanoTime();
                URL url = new URL(s);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                try {
                    conn.setDoInput(true);
                    conn.setDoOutput(false);
                    conn.connect();
                    byte[] data = CommonTools.loadInputStream(conn.getErrorStream() != null
                            ? conn.getErrorStream()
                            : conn.getResponseCode() > 300
                            ? new ByteArrayInputStream((conn.getResponseCode() + " " + conn.getResponseMessage()).getBytes())
                            : conn.getInputStream()
                    );
                    System.out.println(""
                            + "--   Request: " + s
                            + "\n  Response[" + (System.nanoTime() - started) / 1000000f + "ms, " + data.length + "]:\n" + new String(data));
                } finally {
                    conn.disconnect();
                }
            }
        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
            r.stop();
        }

        System.out.println("API stat: " + r.getAPIStatistics(null).dumpStatistics(false).replace("\n", "\n  "));
        for (Object gs : r.getAPIGroups().values()) {
            for (Object gi : ((Map) gs).values()) {
                APIGroup g = (APIGroup) gi;
                if (g != null && g.apiStat != null) {
                    System.out.println("  group stat: " + g.apiStat.dumpStatistics(false).replace("\n", "\n    "));
                }
            }
        }
        System.exit(0);
    }

}
