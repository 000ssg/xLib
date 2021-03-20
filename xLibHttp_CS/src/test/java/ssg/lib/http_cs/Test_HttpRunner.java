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
package ssg.lib.http_cs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import ssg.lib.common.CommonTools;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.HttpMatcher;
import ssg.lib.http.rest.MethodsProvider;
import ssg.lib.http.rest.ReflectiveMethodsProvider;

/**
 *
 * @author 000ssg
 */
public class Test_HttpRunner {

    public static class DemoHW {

        public Long getTime() {
            return System.currentTimeMillis();
        }

        public String getHello(String who) {
            return "Hello, " + who + "!";
        }
    }

    public static class DemoHW1 extends DemoHW {

        public byte[] getData() {
            return "Data".getBytes();
        }
    }

    public static class DemoHW2 {

        public Long getTimestamp() {
            return System.currentTimeMillis();
        }

        public String getHello(String who) {
            return "Hello2, " + who + "!";
        }

        public int error() throws IOException {
            throw new IOException("Demo error");
        }
    }

    public static void main(String... args) throws Exception {
        int port = 30001;
        HttpRunner r = new HttpRunner(new HttpApplication("aaa", "/app"))
                .configureHttp(port)
                .configureREST("rest");
        Collection<HttpMatcher> lst = r.getREST().registerProviders(
                new MethodsProvider[]{
                    new ReflectiveMethodsProvider().setClassNameInPath(true)
                },
                new DemoHW(),
                new DemoHW1(),
                new DemoHW2()
        );
        //System.out.println(lst);
        r.start();
        try {
            for (String s : new String[]{
                "http://localhost:" + port + "/app/rest/demoHW/hello?who=a",
                "http://localhost:" + port + "/app/rest/demoHW/time",
                "http://localhost:" + port + "/app/rest/demoHW2/error"
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
                            : conn.getResponseCode() > 300
                            ? new ByteArrayInputStream((conn.getResponseCode() + " " + conn.getResponseMessage()).getBytes())
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
