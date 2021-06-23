/*
 * The MIT License
 *
 * Copyright 2021 000ssg.
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

import ssg.lib.http.HttpApplication;
import ssg.lib.http_cs.HttpRunner.HttpConfig;
import ssg.lib.httpapi_cs.APIRunner.APIConfig;
import ssg.lib.net.MCS.MCSConfig;

/**
 *
 * @author 000ssg
 */
public class Test_API_Conf {

    public static class TestA {

        public long getTime() {
            return System.currentTimeMillis();
        }
    }
    public static class TestB {

        public long getTimestamp() {
            return System.currentTimeMillis();
        }
    }

    public static void main(String... args) throws Exception {
        APIRunner runner = new APIRunner(new HttpApplication("Test application", "/teatApp"))
                // initialize API adapter and configure context(s) -> instanceof of TestS class
                .configureAPIAdapter(new APIAdapter()
                        .configureContext(TestA.class, new TestA())
                )
                .configuration(
                        // configure MCS, Http, and API component levels
                        new MCSConfig("net_mcs").init(
                                "net_mcs_acceptors=3"
                        ),
                        new HttpConfig("app_http").init(
                                "app_http_rest=rest"
                        ),
                        new APIConfig("app_api").init(
                                "app_api_api=namespace=aaa;name=A;item=ssg.lib.httpapi_cs.Test_API_Conf$TestA",
                                "app_api_api=namespace=bbb;name=B;item=ssg.lib.httpapi_cs.Test_API_Conf$TestB",
                                "app_api_stub=*,*,js,jq",
                                "")
                );
        runner.start();
        System.out.println(runner);
        runner.stop();
    }
}
