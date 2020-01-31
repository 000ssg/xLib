/*
 * The MIT License
 *
 * Copyright 2020 Sergey Sidorov/000ssg@gmail.com
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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.base.HttpRequest;

/**
 *
 * @author 000ssg
 */
public class DemoHttp {

    public static class AC implements Channel {

        boolean closed = false;

        @Override
        public boolean isOpen() {
            return !closed;
        }

        @Override
        public void close() throws IOException {
            closed = true;
        }

        @Override
        public String toString() {
            return "AC{" + "closed=" + closed + '}';
        }

    }

    public static void log(String title, HttpData http) throws IOException {
        System.out.println(title + ": " + http.toString().replace("\r\n", "\\r\\n").replace("\n", "\\n"));
        if(!http.getBody().isEmpty()) {
            System.out.println("    | "+http.getBody().toString().replace("\n", "\n    | "));
        }
    }

    public static void main(String... args) throws Exception {

        Channel ch = new AC();
        HttpRequest reqC = new HttpRequest(true).append(
                ByteBuffer.wrap((""
                        + "GET /index.php?a=w HTTP/1.1"
                        + "\r\nHost:1.1.1.1"
                        + "\r\nUser-Agent: Test"
                        + "\r\nConnection: keep-alive"
                        + "\r\n"
                        + "\r\n").getBytes())
        );
        log("REQ C 0", reqC);
        HttpRequest reqS = new HttpRequest(reqC.getAll());
        log("REQ C 1", reqC);
        log("REQ S 0", reqS);
        byte[] rd = "Got it".getBytes();
        reqS.getResponse().setResponseCode(200, "OK");
        reqS.getResponse().setHeader(HttpData.HH_CONTENT_LENGTH, "" + rd.length);
        reqS.getResponse().onHeaderLoaded();
        reqS.add(ByteBuffer.wrap(rd));
        log("REQ S 1", reqS);
        log("RES S 0", reqS.getResponse());
        reqC.add(reqS.getResponse().getAll());
        log("RES S 1", reqS.getResponse());
        log("RES C 0", reqC.getResponse());

        int a = 0;
    }
}
