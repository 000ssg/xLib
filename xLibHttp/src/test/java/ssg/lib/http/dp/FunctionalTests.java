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
package ssg.lib.http.dp;

import java.nio.ByteBuffer;
import java.util.List;
import ssg.lib.common.TaskExecutor;
import ssg.lib.common.TaskProvider.TaskPhase;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.HttpSession;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.http.base.HttpResponse;

/**
 *
 * @author 000ssg
 */
public class FunctionalTests {

    public static void main(String... args) throws Exception {
        HttpStaticDataProcessor dp = new HttpStaticDataProcessor();
        //dp.DEBUG = true;
        //dp.useDataPipes=false;
        HttpResourceCollection rc = new HttpResourceCollection("/", "src/test/resources/replacements/");
        rc.contentTypes.put(".txt", "text/plain");
        dp.add(rc);

        HttpApplication app = new HttpApplication("Test App", "/appl");
        HttpSession sess = new HttpSession("/", app);
        HttpRequest req = new HttpRequest(ByteBuffer.wrap("GET /A.txt HTTP/1.1\r\n\r\n".getBytes()));
        req.setContext(sess);
        dp.onHeaderLoaded(req);

        TaskExecutor te = new TaskExecutor.TaskExecutorSimple();
        if (dp.useDataPipes) {
            te.execute(dp, dp.getTasks(TaskPhase.initial));
        } else {
            te.execute(dp, dp.fetchRunnable(req));
        }

        System.out.println("\nOUTPUT: ");
        HttpResponse res = req.getResponse();
        List<ByteBuffer> bbs = null;
        while (true) {
            bbs = res.get();
            if (BufferTools.hasRemaining(bbs)) {
                System.out.println("BLOCK:\n  | " + BufferTools.toText("ISO-8859-1", bbs).replace("\n", "\n  | "));
            } else {
                if (res.isSent()) {
                    break;
                }
            }
        }
        int a = 0;
    }
}
