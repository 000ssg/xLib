
import java.net.URI;
import ssg.lib.api.API_Publisher;
import ssg.lib.api.API_Publisher.API_Publishers;
import ssg.lib.api.util.Reflective_API_Builder;
import ssg.lib.api.wamp_cs.WAMP_CS_API;
import ssg.lib.common.net.NetTools;
import ssg.lib.http.dp.HttpResourceCollection;
import ssg.lib.http.dp.HttpStaticDataProcessor;
import ssg.lib.wamp.nodes.WAMPClient;

/*
 * The MIT License
 *
 * Copyright 2020 sesidoro.
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
/**
 *
 * @author sesidoro
 */
public class APINode {

    public static void main(String... args) throws Exception {
        // prepare APIs to publish
        Test_APIs.API1 api1 = new Test_APIs.API1();
        Test_APIs.API2 api2 = new Test_APIs.API2();

        // prepare publisher
        API_Publishers apis = new API_Publishers();
        apis.add("a", new API_Publisher()
                .configure(Reflective_API_Builder
                        .buildAPI("a", new Reflective_API_Builder.Reflective_API_Context(null), api1.getClass())).configureContext(api1));
        apis.add("b", new API_Publisher()
                .configure(Reflective_API_Builder.buildAPI("b", new Reflective_API_Builder.Reflective_API_Context(null), api2.getClass()))
                .configureContext(api2));

        // publish
        WAMP_CS_API node = new WAMP_CS_API()
                .router(30020)
                .rest(30011);

        
        // add HTML test pages
        node.getHttpService().getDataProcessors(null, null).addItem(new HttpStaticDataProcessor()
                .add(new HttpResourceCollection("/ui/*", "src/test/resources/ui"))
                //.add(new HttpResourceCollection("/*", "resource:ui"))
                .noCacheing()
        );

        node.start();
        System.out.println("Started " + node
                + "\n  WAMP URIs:\n    " + node.getRouterURIs().toString().replace(",", "\n    ")
                + "\n  WebSocket URIs:\n    " + node.getWebSocketURIs().toString().replace(",", "\n    ")
        );

        WAMPClient clientAPI = node.publishAPI(new URI("ws://localhost:30020"), "AB", apis, "a", "b");

        NetTools.delay(1000 * 60 * 15);

        System.out.println("Stopping " + node);

        node.stop();
    }
}
