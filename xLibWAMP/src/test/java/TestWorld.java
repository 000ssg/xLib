
import ssg.lib.wamp.util.WAMPTransportList;
import java.util.List;
import java.util.Map;
import ssg.lib.wamp.WAMP;
import static ssg.lib.wamp.WAMPConstantsBase.INFO_CloseNormal;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPFeatureProvider;
import ssg.lib.wamp.auth.WAMPAuthProvider;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ID;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_METHOD;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ROLE;
import ssg.lib.wamp.auth.impl.WAMPAuthCRA;
import ssg.lib.wamp.auth.impl.WAMPAuthTicket;
import ssg.lib.wamp.features.WAMP_FP_Reflection;
import ssg.lib.wamp.features.WAMP_FP_SessionMetaAPI;
import ssg.lib.wamp.nodes.WAMPClient;
import ssg.lib.wamp.nodes.WAMPNode;
import ssg.lib.wamp.nodes.WAMPRouter;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.util.WAMPTools;

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
 * @author 000ssg
 */
public class TestWorld implements Runnable {

    static boolean TRACE_MESSAGES = false;

    List<WAMPTransportList.WAMPTransportLoop> transports = WAMPTools.createSynchronizedList();
    List<WAMPClient> clients = WAMPTools.createSynchronizedList();

    // authenticators
    public WAMPAuthProvider wapcra = new WAMPAuthCRA("testCRA");
    WAMPAuthProvider wapcra2 = new WAMPAuthCRA("testCRA2");
    WAMPAuthProvider wapcra3 = new WAMPAuthCRA("testCRA3");
    public WAMPAuthProvider wapticket = new WAMPAuthTicket("testTicket", (session, authid, ticket) -> {
        Map<String, Object> map = WAMPTools.createDict(K_AUTH_ID, authid, K_AUTH_METHOD, authid);
        map.put(K_AUTH_ROLE, authid.endsWith("-2") || authid.endsWith("-4") ? "admin" : authid.endsWith("-3") ? "trainer" : "student");
        return map;
    });

    // features
    WAMPFeatureProvider wfpSessionMeta = new WAMP_FP_SessionMetaAPI();
    WAMPFeatureProvider wfpReflection = new WAMP_FP_Reflection();

    // the router
    WAMPRouter router = new WAMPRouter(WAMP.Role.broker, WAMP.Role.dealer)
            .configure(wapcra, wapticket)
            .configure(WAMPFeature.x_session_meta_api, wfpSessionMeta)
            .configure(WAMPFeature.procedure_reflection, wfpReflection)
            .configure(WAMPFeature.topic_reflection, wfpReflection);
    Thread runner;

    public WAMPRouter getRouter() {
        return router;
    }

    public synchronized void doNodeCycle(WAMPNode node) {
        try {
            node.runCycle();
        } catch (WAMPException wex) {
            wex.printStackTrace();
        }
    }

    public void doRouterCycle() {
        doNodeCycle(router);
    }

    @Override
    public void run() {
        String old = Thread.currentThread().getName();
        Thread.currentThread().setName("WORLD-RUNNER");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                doNodeCycle(router);
                WAMPClient[] cs = null;
                synchronized (clients) {
                    cs = clients.toArray(new WAMPClient[clients.size()]);
                }
                for (WAMPClient client : cs) {
                    doNodeCycle(client);
                    doNodeCycle(router);
                }
                try {
                    Thread.sleep(1);
                } catch (Throwable th) {
                    break;
                }
            }
        } finally {
            runner = null;
            Thread.currentThread().setName(old);
        }
    }

    public void start() {
        if (runner == null || runner.isInterrupted()) {
            runner = new Thread(this);
            runner.setDaemon(true);
            runner.start();
        }
    }

    public void stop() {
        if (runner != null) {
            for (WAMPClient client : clients.toArray(new WAMPClient[clients.size()])) {
                if (client != null && client.isConnected()) {
                    try {
                        client.disconnect(INFO_CloseNormal);
                    } catch (WAMPException wex) {
                        wex.printStackTrace();
                    }
                }
            }
            try {
                Thread.sleep(100);
            } catch (Throwable th) {
            }
            if (!runner.isInterrupted()) {
                runner.interrupt();
            }
        }
    }

    public WAMPClient connect(String realm, String agent, WAMP.Role[] roles, String userid, WAMPAuthProvider authProvider, WAMPFeature... features) throws WAMPException {
        WAMPTransportList.WAMPTransportLoop transport = new WAMPTransportList.WAMPTransportLoop();
        transport.remote.TRACE_MESSAGES = "WR";
        transport.local.TRACE_MESSAGES = "WC";
        transports.add(transport);

        final WAMPClient client = new WAMPClient()
                .configure(authProvider)
                .configure(WAMPFeature.x_session_meta_api, wfpSessionMeta)
                .configure(WAMPFeature.procedure_reflection, wfpReflection)
                .configure(features);
        client.configure(transport.local, agent, realm, roles);
        //client.addWAMPNodeListener(new WAMPNodeListenerDebug("CLIENT#" + i + ": "));
        clients.add(client);
        router.onNewTransport(transport.remote);

        if (userid != null) {
            client.connect(userid);
        } else {
            client.connect();
        }

        return client;
    }
}
