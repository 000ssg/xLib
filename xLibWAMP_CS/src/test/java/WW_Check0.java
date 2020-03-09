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
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.codehaus.jackson.map.ObjectMapper;
import ssg.lib.common.CommonTools;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.common.net.NetTools;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.nodes.WAMPClient;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.nodes.WAMPRouter;
import ssg.lib.wamp.cs.WAMPTransportJSON;
import ssg.lib.wamp.events.WAMPEventListener;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_REG_META_TOPIC_ON_CREATE;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_REG_META_TOPIC_ON_DELETE;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_REG_META_TOPIC_ON_REGISTER;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_REG_META_TOPIC_ON_UNREGISTER;
import ssg.lib.wamp.rpc.impl.callee.CalleeProcedure.Callee;
import ssg.lib.wamp.rpc.impl.WAMPRPCListener.WAMPRPCListenerBase;
import ssg.lib.wamp.rpc.impl.callee.CalleeCall;
import ssg.lib.wamp.util.WAMPTools;
import ssg.lib.websocket.WebSocket;
import ssg.lib.websocket.WebSocketExtension;
import ssg.lib.websocket.WebSocketFrame;

/**
 *
 * @author 000ssg
 */
public class WW_Check0 {

    static ObjectMapper mapper = new ObjectMapper();

    public static void dumpString(String s, int maxLen) {
        s = "[" + s.length() + "]" + s;
        if (s.length() > maxLen) {
            s = s.substring(0, maxLen / 2)
                    + "..."
                    + s.substring(s.length() - (maxLen / 2));
            ;
        }
        System.out.println(s);
    }

    public static void dumpCalls(boolean[] established, boolean[] registered, AtomicInteger[][] calls) {
        StringBuilder sb = new StringBuilder();
        sb.append("----- CALLS INFO");
        for (int i = 0; i < calls.length; i++) {
            sb.append("\n-- " + i + " ");
            if (i < 100) {
                sb.append(' ');
            }
            if (i < 10) {
                sb.append(' ');
            }
            if (established[i]) {
                sb.append("E");
            } else {
                sb.append(" ");
            }
            if (registered[i]) {
                sb.append("R");
            } else {
                sb.append(" ");
            }
            sb.append(' ');
            for (int j = 0; j < calls[i].length; j++) {
                String s = "" + calls[i][j].get();
                sb.append(s);
                for (int k = 0; k < 10 - s.length(); k++) {
                    sb.append(' ');
                }
            }
        }
        System.out.println(sb);
    }

    public static class WCS implements Runnable {

        Thread runner;

        WAMPRouter router = new WAMPRouter(Role.broker, Role.dealer)
                .configure(WAMPFeature.shared_registration, WAMPFeature.registration_meta_api);
        List<WAMPClient> clients = new ArrayList<>();

        public void start() {
            if (runner == null) {
                runner = new Thread(this);
                runner.setDaemon(true);
                runner.start();
            }
        }

        public void stop() {
            if (runner != null) {
                runner.interrupt();
            }
        }

        public WAMPClient connect(
                URI uri,
                String api,
                WAMPFeature[] features,
                String agent,
                String realm,
                Role... roles
        ) throws WAMPException {
            WAMPTransportJSON.WAMPJSONTransportLoop transport = new WAMPTransportJSON.WAMPJSONTransportLoop();
            WAMPClient client = new WAMPClient().configure(
                    transport.local,
                    features,
                    agent,
                    realm,
                    roles
            );
            clients.add(client);
            router.onNewTransport(transport.remote);
            client.connect();
            return client;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    router.runCycle();
                    if (!clients.isEmpty()) {
                        Collection<WAMPClient> cs = new ArrayList<>();
                        cs.addAll(clients);
                        Collection<WAMPClient> closed = new HashSet<>();
                        for (WAMPClient client : cs) {
                            if (client.isConnected()) {
                                client.runCycle();
                            }
                            if (!client.isSessionEstablished()) {
                                if (!client.isConnected()) {
                                    closed.add(client);
                                }
                            }
                        }
                        if (!closed.isEmpty()) {
                            clients.removeAll(closed);
                        }
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        }
    }

    public static void main(String... args) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("\n===============================================================================================");
        for (int i = 0; i < 7; i++) {
            sb.append("\n====---------------------------------------------------------------------------------------====");
        }
        sb.append("\n===============================================================================================");

        WebSocket.allExtensions.clear();

        // prepare async WS client context
        final WebSocket.FrameMonitor fm = new WebSocket.FrameMonitor() {
            boolean compact = true;

            @Override
            public boolean check(long options, WebSocket ws, WebSocketFrame frame, WebSocketExtension processedBy) {
                return true;
            }

            @Override
            public void onCompletedFrame(WebSocket ws, WebSocketFrame frame, WebSocketExtension processedBy, ByteBuffer[] payload) {
                System.out.println("RECV[" + ((ws != null) ? ws.id + "/" + (ws.isClient() ? "C" : "S") : processedBy) + ", op=" + frame.getOpCode() + ";fin=" + frame.isFinalFragment() + "]" + ((compact) ? " size=" + BufferTools.getRemaining(payload) : "\n  " + BufferTools.dump(payload).replace("\n", "\n  ")));
            }

            @Override
            public void onOutgoingFrame(WebSocket ws, WebSocketFrame frame, WebSocketExtension processedBy, ByteBuffer[] payload, Integer off) {
                System.out.println("SEND[" + ((ws != null) ? ws.id + "/" + (ws.isClient() ? "C" : "S") : processedBy) + "," + off + ", op=" + frame.getOpCode() + ";fin=" + frame.isFinalFragment() + "]" + ((compact) ? " size=" + BufferTools.getRemaining(payload) : "\n  " + BufferTools.dump(payload).replace("\n", "\n  ")));
            }
        };

        WCS wcs = new WCS();
        wcs.start();
        WCS clients = wcs;

//        CS cs = new CS();
//        WAMPClient_WSProtocol clients = new WAMPClient_WSProtocol() {
//            @Override
//            public void initialize(Channel provider, WebSocket ws) {
//                super.initialize(provider, ws);
//                //ws.setFrameMonitor(fm);
//                if (ws instanceof WebSocketChannel) {
//                    ((WebSocketChannel) ws).DUMP = true;
//                }
//            }
//        };
//        WebSocket_CSGroup wsGroup = new WebSocket_CSGroup();
//        wsGroup.addWebSocketProtocolHandler(
//                clients
//        );
//        cs.addCSGroup(wsGroup);
//        cs.start();
        int wsPort = 30020;
        URI wsURI = new URI("ws://localhost:" + wsPort + "/ws");

        // prepare spy
        WAMPClient spy = clients.connect(wsURI, WAMP.WS_SUB_PROTOCOL_JSON, null, "agent_spy", "OPENPOINT.TEST2", Role.subscriber);
        if (1 == 0) {
            spy.addWAMPEventListener(new WAMPEventListener.WAMPEventListenerBase(
                    RPC_REG_META_TOPIC_ON_CREATE,
                    WAMPTools.EMPTY_DICT,
                    (subscriptionId, publicationId, options, arguments, argumentsKw) -> {
                        System.out.println(""
                                + "SPY: onCreate: " + subscriptionId + "/" + publicationId
                                + ":"
                                + " options=" + options
                                + ((arguments != null) ? ", args=" + arguments : "")
                                + ((argumentsKw != null) ? ", argsKw=" + argumentsKw : "")
                        );
                    }));
            spy.addWAMPEventListener(new WAMPEventListener.WAMPEventListenerBase(
                    RPC_REG_META_TOPIC_ON_REGISTER,
                    WAMPTools.EMPTY_DICT,
                    (subscriptionId, publicationId, options, arguments, argumentsKw) -> {
                        System.out.println(""
                                + "SPY: onRegister: " + subscriptionId + "/" + publicationId
                                + ":"
                                + " options=" + options
                                + ((arguments != null) ? ", args=" + arguments : "")
                                + ((argumentsKw != null) ? ", argsKw=" + argumentsKw : "")
                        );
                    }));
            spy.addWAMPEventListener(new WAMPEventListener.WAMPEventListenerBase(
                    RPC_REG_META_TOPIC_ON_UNREGISTER,
                    WAMPTools.EMPTY_DICT,
                    (subscriptionId, publicationId, options, arguments, argumentsKw) -> {
                        System.out.println(""
                                + "SPY: onUnregister: " + subscriptionId + "/" + publicationId
                                + ":"
                                + " options=" + options
                                + ((arguments != null) ? ", args=" + arguments : "")
                                + ((argumentsKw != null) ? ", argsKw=" + argumentsKw : "")
                        );
                    }));
            spy.addWAMPEventListener(new WAMPEventListener.WAMPEventListenerBase(
                    RPC_REG_META_TOPIC_ON_DELETE,
                    WAMPTools.EMPTY_DICT,
                    (subscriptionId, publicationId, options, arguments, argumentsKw) -> {
                        System.out.println(""
                                + "SPY: onDelete: " + subscriptionId + "/" + publicationId
                                + ":"
                                + " options=" + options
                                + ((arguments != null) ? ", args=" + arguments : "")
                                + ((argumentsKw != null) ? ", argsKw=" + argumentsKw : "")
                        );
                    }));
        }
        // prepare
        WAMPClient[] callers = new WAMPClient[10];
        for (int i = 0; i < callers.length; i++) {
            callers[i] = clients.connect(wsURI, WAMP.WS_SUB_PROTOCOL_JSON, new WAMPFeature[]{WAMPFeature.shared_registration}, "agent_" + i, "OPENPOINT.TEST2", Role.callee, Role.caller, Role.publisher, Role.subscriber);
//            callers[i].addWAMPNodeListener(new WAMPNodeListenerDebug("CLIENT[" + i + "]: ").filter((session, msg, mf, error) -> {
//                return msg == null || msg.getType().getId() != WAMPMessageType.T_EVENT;
//            }));
        }

        // connect/register
        for (int i = 0; i < callers.length; i++) {
            if (!callers[i].isConnected()) {
                callers[i].connect();
            }
        }

        // connect/register
        final boolean[] established = new boolean[callers.length];
        final boolean[] registered = new boolean[callers.length];
        // calls: [i][0 - invoke, 1 - finish (ok or error), 2 - error, 3 - invoked at]
        final AtomicInteger[][] calls = new AtomicInteger[callers.length][4];
        for (int i = 0; i < calls.length; i++) {
            for (int j = 0; j < calls[i].length; j++) {
                calls[i][j] = new AtomicInteger();
            }
        }
        Arrays.fill(registered, false);
        boolean hasRegistered = false;
        long startedAt = System.currentTimeMillis();
        for (int k = 0; k < 100; k++) {
            for (int i = 0; i < callers.length; i++) {
                if (callers[i].isSessionEstablished()) {
                    established[i] = true;
                    if (!registered[i]) {
                        if ((i % 2) == 0) {
                            registered[i] = true;
                            final int ii = i;
                            callers[i].addExecutor(
                                    WAMPTools.createDict("invoke", "roundrobin"), "AAA",
                                    new Callee() {
                                @Override
                                public Future invoke(CalleeCall call, ExecutorService executor, final String name, final List args, final Map argsKw) throws WAMPException {
                                    return executor.submit(new Callable() {
                                        @Override
                                        public Object call() throws Exception {
                                            calls[ii][3].incrementAndGet();
                                            int size = 1024;
                                            if (argsKw != null && argsKw.get("size") instanceof Number) {
                                                try {
                                                    size = ((Number) argsKw.get("size")).intValue();
                                                } catch (Throwable th) {
                                                }
                                            } else if (args != null && !args.isEmpty() && args.get(0) instanceof Number) {
                                                try {
                                                    size = ((Number) args.get(0)).intValue();
                                                } catch (Throwable th) {
                                                }
                                            }

                                            long delay = (long) (Math.random() * 2 + 0);
                                            NetTools.delay(delay);

                                            if (1 == 0) {
                                                System.out.println(""
                                                        + "invoked for AAA[" + ii + ", " + delay + "]"
                                                        + ":"
                                                        + ((args != null) ? " args=" + args : "")
                                                        + ((argsKw != null) ? " argsKw=" + argsKw : "")
                                                );
                                            }

                                            char[] cbuf = new char[size];
                                            Arrays.fill(cbuf, 'D');
                                            char[] cs = ("size=" + size + " " + ", delay=" + delay + "ms ").toCharArray();
                                            for (int i = 0; i < Math.min(cs.length, cbuf.length); i++) {
                                                cbuf[i] = cs[i];
                                            }
                                            return new String(cbuf);
                                        }
                                    });
                                }
                            });
                            if ((System.currentTimeMillis() - startedAt) > 1000 * 2) {
                                hasRegistered = true;
                            }
                        }
                    }
                }
                if (callers[i].isSessionEstablished()) {
                    established[i] = true;
                    if ((System.currentTimeMillis() - startedAt) > 1000 * 2) {
                        hasRegistered = true;
                    }

                    if (hasRegistered) {
                        if (calls[i][1].get() <= calls[i][0].get()) {
                            if ((i % 2) == 1) {
                                //System.out.println("Call[" + i + "]...");
                                final int ii = i;
                                List lst = new ArrayList();
                                Map<String, Object> map = WAMPTools.createMap(true);
                                lst.add(100 + i * (int) (Math.random() * 50));//000));
                                calls[i][0].incrementAndGet();
                                callers[i].addWAMPRPCListener(new WAMPRPCListenerBase(WAMPTools.EMPTY_DICT, "AAA", lst, map) {
                                    @Override
                                    public void onCancel(long callId, String reason) {
                                        System.out.println("cancelled " + getProcedure() + ": " + reason);
                                        calls[ii][1].incrementAndGet();
                                        calls[ii][2].incrementAndGet();
                                    }

                                    @Override
                                    public boolean onResult(long callId, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                                        if (1 == 0) {
                                            dumpString(""
                                                    + "result of " + getProcedure()
                                                    + ":"
                                                    + " callId=" + callId
                                                    + ", details=" + details
                                                    + ((args != null) ? ", args=" + args : "")
                                                    + ((argsKw != null) ? ", argsKw=" + argsKw : ""),
                                                    150
                                            );
                                        }

                                        calls[ii][1].incrementAndGet();
                                        return true;
                                    }

                                    @Override
                                    public void onError(long callId, String error, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                                        dumpString(""
                                                + "error in " + getProcedure()
                                                + ":"
                                                + " callId=" + callId
                                                + ", error=" + error
                                                + ", details=" + details
                                                + ((args != null) ? ", args=" + args : "")
                                                + ((argsKw != null) ? ", argsKw=" + argsKw : ""),
                                                150
                                        );
                                        calls[ii][1].incrementAndGet();
                                        calls[ii][2].incrementAndGet();
                                    }
                                });
                            }
                        }
                    }
                }
                if (!callers[i].isSessionEstablished()) {
                    // no session?
                    established[i] = false;
                    int a = 0;
                }
            }
            NetTools.delay(100);
            if (k % 10 == 0) {
                dumpCalls(established, registered, calls);
            }
        }

        System.out.println(sb.toString());
        dumpCalls(established, registered, calls);

        System.out.println(sb.toString() + "\nwait 5 sec...");
        NetTools.delay(1000 * 5);
        dumpCalls(established, registered, calls);

        // disconnect
        System.out.println(sb.toString() + "\ndisconnect all");
        for (int i = 0; i < callers.length; i++) {
            callers[i].disconnect("OK");
        }
        dumpCalls(established, registered, calls);

        CommonTools.wait(1000 * 15, () -> {
            boolean r = false;
            for (int i = 0; i < callers.length; i++) {
                if (callers[i].isConnected()) {
                    r = true;
                }
                break;
            }
            return r;
        });

        spy.disconnect("OK");

        System.out.println("STOPPED.");
        NetTools.delay(500);
        for (int i = 0; i < callers.length; i++) {
            if (!callers[i].isConnected()) {
                established[i] = false;
            }
        }
        dumpCalls(established, registered, calls);

        wcs.stop();

    }
}
