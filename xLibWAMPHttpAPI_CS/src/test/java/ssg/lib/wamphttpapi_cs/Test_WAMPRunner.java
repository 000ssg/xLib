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

import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import ssg.lib.api.API_Publisher;
import ssg.lib.api.util.Reflective_API_Builder;
import ssg.lib.common.CommonTools;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.dp.HttpResourceBytes;
import ssg.lib.http.dp.HttpStaticDataProcessor;
import ssg.lib.httpapi_cs.APIStatistics;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.features.WAMP_FP_Reflection;
import ssg.lib.wamp.nodes.WAMPNode;

/**
 *
 * @author sesidoro
 */
public class Test_WAMPRunner {

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

        // prevent output from WAMP session establish/close events
        WAMPNode.DUMP_ESTABLISH_CLOSE = false;

        // build runner with default http application and embedded 
        // (within app) WAMP and REST (for same subpath "wamp")
        // register reflection-based API (DemoHW class) and register instance (context)
        WAMPRunner r = new WAMPRunner(new HttpApplication("A", "/app"), new APIStatistics())
                .configureWAMPRouter("wamp")
                .configureAPI("demo", "test", new API_Publisher()
                        .configure(Reflective_API_Builder.buildAPI("test", null, DemoHW.class))
                        .configureContext(new DemoHW())
                )
                .configureHttp(port)
                .configureREST("wamp");

        // add WAMP reflection support to enable automatically generated javascripts
        r.wamp().configureFeature(WAMPFeature.procedure_reflection, new WAMP_FP_Reflection());

        // add javascripts generation for WAMP for WAMP (authobahn.js "wamp")
        // and REST ("js" and "jw", jquery)
        String router_root = r.getApp() != null ? r.getApp().getRoot() + "/" : "/";
        StubWAMPVirtualData apiJS = new StubWAMPVirtualData(r.getRouter(), router_root + "wamp", "js", "jw", "wamp")
                .configure(new StubWAMPReflectionContext(null, null, true))
                .configure("demo", "js", "jw", "wamp")
                .configure("test", "js", "jw", "wamp")
                ;
        HttpStaticDataProcessor apiJS_DP = new HttpStaticDataProcessor();
        for (StubWAMPVirtualData.WR wr : apiJS.resources()) {
            System.out.println("  adding " + wr.getPath());
            apiJS_DP.add(new HttpResourceBytes(apiJS, wr.getPath(), "text/javascript; encoding=utf-8"));
        }
        if (r.getApp() != null) {
            r.getApp().configureDataProcessor(0, apiJS_DP);
        } else {
            r.getService().configureDataProcessor(0, apiJS_DP);
        }

        // start service and wait til self-configured
        r.start();
        Thread.sleep(1000);

        // use http/rest to execute WAMP methods and retrieve javascript texts.
        try {
            for (String s : new String[]{
                "http://localhost:" + port + "/app/wamp/demo/test.DemoHW.getHello?who=a",
                "http://localhost:" + port + "/app/wamp/demo/test.DemoHW.time",
                "http://localhost:" + port + "/app/wamp/demo/script.wamp",
                "http://localhost:" + port + "/app/wamp/demo/script.js",
                "http://localhost:" + port + "/app/wamp/demo/script.jw",
                "http://localhost:" + port + "/app/wamp/test/script.jw",}) {
                long started = System.nanoTime();
                URL url = new URL(s);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                Throwable error = null;
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
                            + "\n  Response[" + (System.nanoTime() - started) / 1000000f + "ms, " + data.length + "]: " + new String(data));
                } catch (Throwable th) {
                    error = th;
                    throw th;
                } finally {
                    conn.disconnect();
                    if (error != null) {
                        System.out.println(""
                                + "--   Request: " + s
                                + "\n  ERROR: " + error
                        );
                    }
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
