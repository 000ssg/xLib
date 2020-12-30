
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ssg.lib.wamp.WAMP;
import static ssg.lib.wamp.WAMPConstantsBase.INFO_CloseNormal;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPFeatureProvider;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.auth.WAMPAuthProvider;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ID;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_METHOD;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ROLE;
import ssg.lib.wamp.auth.impl.WAMPAuthCRA;
import ssg.lib.wamp.auth.impl.WAMPAuthTicket;
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
 * @author sesidoro
 */
public class TestWorld implements Runnable {

    static boolean TRACE_MESSAGES = false;

    List<WAMPTransportList.WAMPTransportLoop> transports = WAMPTools.createSynchronizedList();
    List<WAMPClient> clients = WAMPTools.createSynchronizedList();

    // authenticators
    public WAMPAuthProvider wapcra = new WAMPAuthCRA("testCRA");
    WAMPAuthProvider wapcra2 = new WAMPAuthCRA("testCRA2");
    WAMPAuthProvider wapcra3 = new WAMPAuthCRA("testCRA3");
    public WAMPAuthProvider wapticket = new WAMPAuthTicket("testTicket") {
        @Override
        public Map<String, Object> verifyTicket(WAMPSession session, String authid, String ticket) throws WAMPException {
            Map<String, Object> map = WAMPTools.createDict(K_AUTH_ID, authid, K_AUTH_METHOD, name());
            map.put(K_AUTH_ROLE, authid.endsWith("-2") || authid.endsWith("-4") ? "admin" : authid.endsWith("-3") ? "trainer" : "student");
            return map;
        }
    };

    // features
    WAMPFeatureProvider wfpSessionMeta = new WAMP_FP_SessionMetaAPI();

    // the router
    WAMPRouter router = new WAMPRouter(WAMP.Role.broker, WAMP.Role.dealer)
            .configure(wapcra, wapticket)
            .configure(WAMPFeature.x_session_meta_api, wfpSessionMeta);
    Thread runner;

    public WAMPRouter getRouter() {
        return router;
    }

    @Override
    public void run() {
        String old=Thread.currentThread().getName();
        Thread.currentThread().setName("WORLD-RUNNER");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    router.runCycle();
                } catch (WAMPException wex) {
                    wex.printStackTrace();
                }
                WAMPClient[] cs = null;
                synchronized (clients) {
                    cs = clients.toArray(new WAMPClient[clients.size()]);
                }
                for (WAMPClient client : cs) {
                    try {
                        client.runCycle();
                    } catch (WAMPException wex) {
                        wex.printStackTrace();
                    }
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

    public static void main(String... args) throws Exception {
        WAMPNode.DUMP_ESTABLISH_CLOSE = false;
        WAMPTransportList.GLOBAL_ENABLE_TRACE_MESSAGES = false;

        TestWorld world = new TestWorld();
        world.start();

        String[] realms = new String[]{"realm.1.com", "realm.2.com"};

        final WAMPClient client = world.connect(realms[0], "publisher.1", new WAMP.Role[]{WAMP.Role.publisher, WAMP.Role.subscriber, WAMP.Role.caller, WAMP.Role.callee}, "user1", world.wapticket, WAMPFeature.x_session_meta_api);

//        client.addWAMPEventListener(new WAMPEventListener.WAMPEventListenerBase(
//                WAMP_FP_SessionMetaAPI.SM_EVENT_ON_JOIN,
//                WAMPTools.EMPTY_DICT,
//                (subscriptionId, publicationId, options, arguments, argumentsKw) -> {
//                    System.out.println("CLIENT " + client.getAgent() + " onEvent[" + WAMP_FP_SessionMetaAPI.SM_EVENT_ON_JOIN + "]: " + publicationId + "/" + subscriptionId + " -> " + arguments + ", " + argumentsKw);
//                }
//        ));
//
//        client.addWAMPEventListener(new WAMPEventListener.WAMPEventListenerBase(
//                WAMP_FP_SessionMetaAPI.SM_EVENT_ON_LEAVE,
//                WAMPTools.EMPTY_DICT,
//                (subscriptionId, publicationId, options, arguments, argumentsKw) -> {
//                    System.out.println("CLIENT " + client.getAgent() + " onEvent[" + WAMP_FP_SessionMetaAPI.SM_EVENT_ON_LEAVE + "]: " + publicationId + "/" + subscriptionId + " -> " + arguments + ", " + argumentsKw);
//                }
//        ));
        System.out.println("SLEEP 100");
        Thread.sleep(100);

        List<WAMPClient> cs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            WAMPClient iclient = world.connect(realms[0], "publisher.x." + i, new WAMP.Role[]{WAMP.Role.publisher, WAMP.Role.subscriber}, "user-" + i, world.wapticket);
            cs.add(iclient);
        }
        for (int i = 0; i < 3; i++) {
            WAMPClient iclient = world.connect(realms[0], "publisher.x." + 3, new WAMP.Role[]{WAMP.Role.publisher, WAMP.Role.subscriber}, "user-" + 3, world.wapticket);
            cs.add(iclient);
        }

        System.out.println("SLEEP 1000");
        Thread.sleep(1000);
        System.out.println("---------------------------------------\n-------------- CLIENT SUMMARY 1\n----------------------------------\n  "
                + client.toString().replace("\n", "\n  ")
                + "\n-----------------------------------------------"
        );
        Long count = (Long) ((List) client.call(WAMP_FP_SessionMetaAPI.SM_RPC_SESSION_COUNT, null, null)).get(0);
        System.out.println("SESSION COUNT: " + count);
        List<Long> sessionList = (List) client.call(WAMP_FP_SessionMetaAPI.SM_RPC_SESSION_LIST, null, null);
        System.out.println("SESSIONS LIST: " + sessionList);
        List<Long> sessionList1 = (List) client.call(WAMP_FP_SessionMetaAPI.SM_RPC_SESSION_LIST, WAMPTools.createList("admin"), null);
        System.out.println("SESSIONS LIST (admin): " + sessionList1);
        List<Long> sessionList2 = (List) client.call(WAMP_FP_SessionMetaAPI.SM_RPC_SESSION_LIST, WAMPTools.createList("trainer"), null);
        System.out.println("SESSIONS LIST (trainer): " + sessionList2);
        List<Long> sessionList3 = (List) client.call(WAMP_FP_SessionMetaAPI.SM_RPC_SESSION_LIST, WAMPTools.createList("admin", "trainer"), null);
        System.out.println("SESSIONS LIST (admin,trainer): " + sessionList3);
        Number killedAdmins = (Number) ((List) client.call(WAMP_FP_SessionMetaAPI.SM_RPC_SESSION_KILL_BY_AUTHROLE, WAMPTools.createList("admin"), null)).get(0);
        System.out.println("SESSIONS KILLED (admin): " + killedAdmins);
        sessionList = (List) client.call(WAMP_FP_SessionMetaAPI.SM_RPC_SESSION_LIST, null, null);
        System.out.println("SESSIONS LIST: " + sessionList);
        sessionList1 = (List) client.call(WAMP_FP_SessionMetaAPI.SM_RPC_SESSION_LIST, WAMPTools.createList("admin"), null);
        System.out.println("SESSIONS LIST (admin): " + sessionList1);
        sessionList3 = (List) client.call(WAMP_FP_SessionMetaAPI.SM_RPC_SESSION_LIST, WAMPTools.createList("admin", "trainer"), null);
        System.out.println("SESSIONS LIST (admin,trainer): " + sessionList3);

        Number killedUser3 = (Number) ((List) client.call(WAMP_FP_SessionMetaAPI.SM_RPC_SESSION_KILL_BY_AUTHID, WAMPTools.createList("user-3"), null)).get(0);
        System.out.println("SESSIONS KILLED (user-3): " + killedUser3);
        sessionList3 = (List) client.call(WAMP_FP_SessionMetaAPI.SM_RPC_SESSION_LIST, WAMPTools.createList("admin", "trainer"), null);
        System.out.println("SESSIONS LIST (admin,trainer): " + sessionList3);

        client.call(WAMP_FP_SessionMetaAPI.SM_RPC_SESSION_LIST, null, null, (id, opt, lst, map) -> {
            System.out.println("SESSIONS LIST (async1," + id + "): " + (map != null ? map : lst));
            return true;
        });
        client.call2(WAMP_FP_SessionMetaAPI.SM_RPC_SESSION_LIST, null, null, (result, error) -> {
            System.out.println("SESSIONS LIST (async2   ): " + (error != null ? "ERR: " + error : result));
        });

        System.out.println("---------------------------------------\n-------------- CLIENT SUMMARY 2\n----------------------------------\n  "
                + client.toString().replace("\n", "\n  ")
                + "\n-----------------------------------------------"
        );
        System.out.println("SLEEP 1000");
        Thread.sleep(1000);
        for (WAMPClient c : cs) {
            c.disconnect("aaa");
        }

        System.out.println("LAST SLEEP 1000");
        Thread.sleep(1000);
        //client.disconnect(INFO_CloseNormal);
        world.stop();
    }

}
