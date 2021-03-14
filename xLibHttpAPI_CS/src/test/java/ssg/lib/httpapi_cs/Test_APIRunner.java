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

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import ssg.lib.api.API;
import ssg.lib.api.API_Publisher;
import ssg.lib.api.util.Reflective_API_Builder;
import ssg.lib.common.CommonTools;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.dp.HttpResourceBytes;
import ssg.lib.http.dp.HttpStaticDataProcessor;
import ssg.lib.http.rest.StubVirtualData;
import ssg.lib.httpapi_cs.APIRunner.APIGroup;

/**
 *
 * @author sesidoro
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

        APIRunner r = new APIRunner(new HttpApplication("A", "/app"))
                .configureHttp(port)
                .configureREST("rest")
                .configureAPI("demo", "test", new API_Publisher()
                        .configure(Reflective_API_Builder.buildAPI("test", null, DemoHW.class))
                        .configureContext(new DemoHW())
                );
        APIGroup ag=(APIGroup)((Map)r.getAPIGroups().get("demo")).values().iterator().next();
        String router_root = r.getApp() != null ? r.getApp().getRoot() + "/" : "/";
        StubVirtualData<API> apiJS = new StubVirtualData(ag.apis.getAPI("test"), router_root.substring(0, router_root.length()-1) , "js", "jw")
                .configure(new StubAPIContext(null, null, true))
                .configure("demo", "js", "jw")
                //.configure("test", "js", "jw")
                ;

        HttpStaticDataProcessor apiJS_DP = new HttpStaticDataProcessor();
        for (StubVirtualData.WR wr : apiJS.resources()) {
            System.out.println("  adding " + wr.getPath());
            apiJS_DP.add(new HttpResourceBytes(apiJS, wr.getPath(), "text/javascript; encoding=utf-8"));
        }
        if (r.getApp() != null) {
            r.getApp().configureDataProcessor(0, apiJS_DP);
        } else {
            r.getService().configureDataProcessor(0, apiJS_DP);
        }

        r.start();
        try {
            for (String s : new String[]{
                "http://localhost:" + port + "/app/rest/test/test.DemoHW.getHello?who=a",
                "http://localhost:" + port + "/app/rest/test/test.DemoHW.time",
                "http://localhost:" + port + "/app/demo/script.js",
            }) {
                long started = System.nanoTime();
                URL url = new URL(s);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                try {
                    conn.setDoInput(true);
                    conn.setDoOutput(false);
                    conn.connect();
                    byte[] data = CommonTools.loadInputStream(conn.getErrorStream() != null
                            ? conn.getErrorStream()
                            : conn.getInputStream()
                    );
                    System.out.println(""
                            + "--   Request: " + s
                            + "\n  Response[" + (System.nanoTime() - started) / 1000000f + "ms, " + data.length + "]: " + new String(data));
                } finally {
                    conn.disconnect();
                }
            }
        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
            r.stop();
        }
        System.exit(0);
    }

}
