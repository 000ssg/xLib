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

import java.util.ArrayList;
import java.util.List;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.rest.annotations.XMethod;
import ssg.lib.http.rest.annotations.XType;
import ssg.lib.http_cs.HttpRunner.HttpConfig;

/**
 *
 * @author 000ssg
 */
public class Test_HttpRunner_Configs {

    @XType
    public static class H1 {

        @XMethod
        public String getId() {
            return "ID";
        }
    }

    public static void main(String... args) throws Exception {
        List<HttpRunner> runners = new ArrayList<>();

        String pfx = HttpConfig.DEFAULT_BASE + ".";
        HttpConfig c = new HttpConfig(
                null,
                pfx + "httpPort=31001",
                pfx + "rest=r1",
                pfx + "stub=aa,js,jw",
                pfx + "stub=bb,js",
                pfx + "stub=cc,js",
                pfx + "context=A=" + H1.class.getName(),
                pfx + "context=B=" + H1.class.getName(),
                pfx + "publish=item=A",
                pfx + "publish=item=A;name=aaa/bbb",
                pfx + "dfgh=a",
                pfx + "authDomain",
                pfx + "tokenDelegate=type=jwt;secret=JWTSecret1;uri=http://localhost:22222/verifier",
                pfx + "tokenDelegate={'type':'token', 'secret':'TKNSecret1', 'uri':'http://localhost:22222/verifier', 'prefix': 'apk.'}"
        );
        HttpRunner r = new HttpRunner(new HttpApplication("A", "/a")).configuration(c);
        System.out.println("CONFIG:\n  " + c.toString().replace("\n", "\n  ") + "\n  " + r.toString().replace("\n", "\n  "));
        int a=0;
    }
}
