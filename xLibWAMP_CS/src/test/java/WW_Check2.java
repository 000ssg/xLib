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
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import ssg.lib.cs.ws.WebSocket_CSGroup;
import ssg.lib.di.base.DFRecorder;
import ssg.lib.net.CS;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.nodes.WAMPClient;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.nodes.WAMPNode.WAMPNodeListenerDebug;
import ssg.lib.wamp.WAMPRealm;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.auth.WAMPAuth;
import ssg.lib.wamp.cs.WAMPClient_WSProtocol;
import ssg.lib.wamp.cs.WAMPRouter_WSProtocol;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.messages.WAMPMessageType;
import ssg.lib.wamp.rpc.impl.callee.CalleeProcedure.Callee;
import ssg.lib.wamp.rpc.impl.callee.WAMPRPCCallee;
import ssg.lib.wamp.rpc.impl.caller.WAMPRPCCaller;
import ssg.lib.wamp.rpc.impl.WAMPRPCListener.WAMPRPCListenerBase;
import ssg.lib.wamp.rpc.impl.callee.CalleeCall;
import ssg.lib.wamp.util.WAMPTools;
import ssg.lib.websocket.WebSocket;
import ssg.lib.websocket.WebSocketExtension;
import ssg.lib.websocket.WebSocketFrame;
import ssg.lib.websocket.extensions.WebSocketExtensionGZip;
import ssg.lib.websocket.extensions.WebSocketExtensionTimestamp;

/**
 *
 * @author 000ssg
 */
public class WW_Check2 {

    static ObjectMapper mapper = new ObjectMapper();
    static int[] TRACE_MESSAGES = new int[]{WAMPMessageType.T_ABORT, WAMPMessageType.T_ERROR};

    public static boolean isTraceAllowed(Object obj) {
        WAMPMessage msg = (obj instanceof WAMPMessage) ? (WAMPMessage) obj : null;
        String smsg = (obj instanceof String) ? (String) obj : null;
        int type = 0;
        try {
            if (msg != null) {
                type = msg.getType().getId();
            } else if (smsg != null) {
                smsg = smsg.substring(smsg.indexOf("[") + 1).trim().replace("\n", " ");
                type = Integer.parseInt(smsg.substring(0, smsg.indexOf(",")));
            }
        } catch (Throwable th) {
            return false;
        }
        return Arrays.binarySearch(TRACE_MESSAGES, type) >= 0;
    }

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

    public static String dumpText(String s, int maxLen) {
        s = s.replace("\n", " ").replace("  ", " ").replace("  ", " ").replace("  ", " ").replace("  ", " ").replace("  ", " ");
        if (s.length() > maxLen) {
            s = s.substring(0, maxLen / 2)
                    + "..."
                    + s.substring(s.length() - (maxLen / 2));
            ;
        }
        return s;
    }

    public static void dumpCalls(boolean[] established, boolean[] registered, AtomicInteger[][] calls) {
        WW_Check1.dumpCalls(established, registered, calls, false);
    }

    public static void dumpCalls(boolean[] established, boolean[] registered, AtomicInteger[][] calls, boolean summaryOnly) {
        StringBuilder sb = new StringBuilder();
        sb.append("----- CALLS INFO");
        int[] ii = new int[(calls != null && calls.length > 0) ? calls[0].length : 0];
        for (int i = 0; i < calls.length; i++) {
            if (!summaryOnly) {
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
            }
            for (int j = 0; j < calls[i].length; j++) {
                String s = "" + calls[i][j].get();
                ii[j] += calls[i][j].get();
                if (!summaryOnly) {
                    sb.append(s);
                    for (int k = 0; k < 10 - s.length(); k++) {
                        sb.append(' ');
                    }
                }
            }
        }
        sb.append("\n-- ALL   ");
        for (int j : ii) {
            String s = "" + j;
            sb.append(s);
            for (int k = 0; k < 10 - s.length(); k++) {
                sb.append(' ');
            }
        }

        try {
            ThreadGroup tg = Thread.currentThread().getThreadGroup();
            while (tg.getParent() != null) {
                tg = tg.getParent();
            }
            Thread[] ths = new Thread[tg.activeCount() + 100];
            int c = tg.enumerate(ths);
            sb.append("\n-- Threads running: " + c);
        } catch (Throwable th) {
            int a = 0;
        }

        System.out.println(sb);
    }

    public static boolean isItemOf(int[] items, int value) {
        return items != null && Arrays.binarySearch(items, value) >= 0;
    }

    public static void main(String... args) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("\n===============================================================================================");
        for (int i = 0; i < 7; i++) {
            sb.append("\n====---------------------------------------------------------------------------------------====");
        }
        sb.append("\n===============================================================================================");

        // queues restrictions
        final int routerMaxPending = 0;
        final int routerMaxQueue = 0;
        final int callerMaxPending = 0;
        final int callerMaxSendQueue = 10;
        final int calleeMaxPending = 0;
        final int calleeMaxQueue = 0;
        final int calleeMaxConcurrent = 30;

        // test counts
        int maxClients = 30;
        int[] callees = new int[]{1, 6, 7, 12, 15};
        int maxCycles = 100;
        final int maxInProcDelay = 10;
        int maxCompletionWait = 1000 * 20;
        int maxPostCompletionWait = 1000 * 1;

        WebSocket.allExtensions.clear();
//        WebSocket.allExtensions.put("monitor", new WebSocketExtensionMonitor());

        File folder = new File("./target/dfr");
        folder.mkdirs();
        DFRecorder<ByteBuffer, SocketChannel> drS = new DFRecorder<ByteBuffer, SocketChannel>(folder) {
            @Override
            public void writeRecord(SocketChannel provider, ByteChannel os, boolean input, Collection<ByteBuffer> data) throws IOException {
                List<ByteBuffer> bufs = new ArrayList<>();
                bufs.add(ByteBuffer.wrap(("\n"
                        + "[" + System.currentTimeMillis()
                        + "][" + (input ? "R" : "W") + "][" + Thread.currentThread().getName()
                        + "][" + provider.getRemoteAddress() + "]").getBytes("UTF-8")));
                bufs.add(ByteBuffer.wrap(("  " + BufferTools.dump(data).replace("\n", "\n  ")).getBytes("UTF-8")));
                super.writeRecord(provider, os, input, bufs);
            }
        }.fullRecorder(new File(folder, "full_s.rec"));
        DFRecorder<ByteBuffer, SocketChannel> drC = new DFRecorder<ByteBuffer, SocketChannel>(folder) {
            @Override
            public void writeRecord(SocketChannel provider, ByteChannel os, boolean input, Collection<ByteBuffer> data) throws IOException {
                List<ByteBuffer> bufs = new ArrayList<>();
                bufs.add(ByteBuffer.wrap(("\n"
                        + "[" + System.currentTimeMillis()
                        + "][" + (input ? "R" : "W") + "][" + Thread.currentThread().getName()
                        + "][" + provider.getLocalAddress() + "]").getBytes("UTF-8")));
                bufs.add(ByteBuffer.wrap(("  " + BufferTools.dump(data).replace("\n", "\n  ")).getBytes("UTF-8")));
                super.writeRecord(provider, os, input, bufs);
            }
        }.fullRecorder(new File(folder, "full_c.rec"));

        // prepare async WS client context
        final WebSocket.FrameMonitor fm = new WebSocket.FrameMonitor() {
            boolean compact = false;

            @Override
            public boolean check(long options, WebSocket ws, WebSocketFrame frame, WebSocketExtension processedBy) {
                return true;
            }

            @Override
            public void onCompletedFrame(WebSocket ws, WebSocketFrame frame, WebSocketExtension processedBy, ByteBuffer[] payload) {
                String x = "";
                if (frame != null && frame.getMask() != null) {
                    x += " m[";
                    for (byte b : frame.getMask()) {
                        x += ' ' + Integer.toHexString(0xFF & b);
                    }
                    x += "]";
                }
                System.out.println("RECV[" + ((ws != null) ? ws.id + "/" + (ws.isClient() ? "C" : "S") : processedBy) + ", op=" + frame.getOpCode() + ";fin=" + frame.isFinalFragment() + x + "]" + ((compact) ? " size=" + BufferTools.getRemaining(payload) : "\n  " + BufferTools.dump(payload).replace("\n", "\n  ")));
            }

            @Override
            public void onOutgoingFrame(WebSocket ws, WebSocketFrame frame, WebSocketExtension processedBy, ByteBuffer[] payload, Integer off) {
                String x = "";
                if (frame != null && frame.getMask() != null) {
                    x += " m[";
                    for (byte b : frame.getMask()) {
                        x += ' ' + Integer.toHexString(0xFF & b);
                    }
                    x += "]";
                }
                System.out.println("SEND[" + ((ws != null) ? ws.id + "/" + (ws.isClient() ? "C" : "S") : processedBy) + "," + off + ", op=" + frame.getOpCode() + ";fin=" + frame.isFinalFragment() + x + "]" + ((compact) ? " size=" + BufferTools.getRemaining(payload) : "\n  " + BufferTools.dump(payload).replace("\n", "\n  ")));
            }
        };

        CS cs = new CS();
        WAMPRouter_WSProtocol router = new WAMPRouter_WSProtocol(new Role[]{Role.dealer, Role.broker}) {
            {
                getRouter()
                        .configureAgent("WAMP_CS")
                        .configure(WAMPFeature.registration_meta_api, WAMPFeature.shared_registration);
                getRouter().setMaxPendingMessagesQueue(routerMaxPending);
                setMaxInputQueueSize(routerMaxQueue);
            }

            @Override
            public boolean onConsume(Channel provider, WebSocket ws, Object message) throws IOException {
                if (isTraceAllowed(message)) {
                    System.out.println("[" + System.currentTimeMillis() + "][WR-" + ws.id + "]-IN: " + dumpText("" + message, 100));
                }
                boolean r = super.onConsume(provider, ws, message);
                return r;
            }

            @Override
            public void onSend(WebSocket ws, Object message) {
                if (isTraceAllowed(message)) {
                    System.out.println("[" + System.currentTimeMillis() + "][WR-" + ws.id + "]-OU: " + dumpText("" + message, 100));
                }
            }

        };
        WAMPClient_WSProtocol clients = new WAMPClient_WSProtocol() {
            @Override
            public void initialize(Channel provider, WebSocket ws) {
                super.initialize(provider, ws);
                //ws.setFrameMonitor(fm);
//                if (ws instanceof WebSocketChannel) {
//                    ((WebSocketChannel) ws).DUMP = true;
//                }
                //ws.getProcessor().DUMP_FRAMES=true;
            }

            @Override
            public WebSocket.WebSocketAddons prepareAddons(Channel provider, boolean client, WebSocket.WebSocketAddons addOns) throws IOException {
                WebSocket.WebSocketAddons r = super.prepareAddons(provider, client, addOns);
                r.addExtensions( //new WebSocketExtensionTimestamp()
                        //, new WebSocketExtensionMonitor()
                        );
                return r;
            }

            @Override
            public boolean onConsume(Channel provider, WebSocket ws, Object message) throws IOException {
                if (isTraceAllowed(message)) {
                    System.out.println("[" + System.currentTimeMillis() + "][WC-" + ws.id + "]-IN: " + dumpText("" + message, 100));
                }
                boolean r = super.onConsume(provider, ws, message);
                return r;
            }

            @Override
            public void onSend(WebSocket ws, Object message) {
                if (isTraceAllowed(message)) {
                    System.out.println("[" + System.currentTimeMillis() + "][WC-" + ws.id + "]-OU: " + dumpText("" + message, 100));
                }
            }

            @Override
            public void onClientCreated(WAMPClient client) throws IOException {
                super.onClientCreated(client);
                try {
                    Field fs = client.getClass().getDeclaredField("session");
                    fs.setAccessible(true);
                    WAMPSession sess = (WAMPSession) fs.get(client);

                    {
                        WAMPRPCCaller rpcc = sess.getRealm().getActor(Role.caller);
                        if (rpcc != null) {
                            rpcc.setMaxConcurrentCalls(callerMaxSendQueue);
                            client.setMaxPendingMessagesQueue(callerMaxPending);
                        }
                    }

                    {
                        WAMPRPCCallee rpcc = sess.getRealm().getActor(Role.callee);
                        if (rpcc != null) {
                            rpcc.setMaxQueuedTasks(calleeMaxQueue);
                            rpcc.setMaxConcurrentTasks(calleeMaxConcurrent);
                            client.setMaxPendingMessagesQueue(calleeMaxPending);
                        }
                    }
                } catch (Throwable th) {
                }
            }

        };
        WebSocket_CSGroup wsGroup = new WebSocket_CSGroup();
        wsGroup.addWebSocketProtocolHandler(
                router,
                clients
        );
        //wsGroup.getDI().filter(drC);
        cs.addCSGroup(wsGroup);
        cs.start();

        int wsPort = 30030;
        URI wsURI = new URI("ws://localhost:" + wsPort + "/ws");
        wsGroup.addListenerAt(cs, InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), wsPort);
//        // prepare spy
//        WAMPClient spy = clients.connect(wsURI, WAMP.WS_SUB_PROTOCOL_JSON, null, "agent_spy", "OPENPOINT.TEST2", Role.subscriber);
//        if (1 == 0) {
//            spy.addWAMPEventListener(new WAMPEventListener.WAMPEventListenerBase(
//                    WAMPRPCRegistrations.TOPIC_ON_CREATE,
//                    new HashMap(),
//                    (subscriptionId, publicationId, options, arguments, argumentsKw) -> {
//                        System.out.println(""
//                                + "SPY: onCreate: " + subscriptionId + "/" + publicationId
//                                + ":"
//                                + " options=" + options
//                                + ((arguments != null) ? ", args=" + arguments : "")
//                                + ((argumentsKw != null) ? ", argsKw=" + argumentsKw : "")
//                        );
//                    }));
//            spy.addWAMPEventListener(new WAMPEventListener.WAMPEventListenerBase(
//                    WAMPRPCRegistrations.TOPIC_ON_REGISTER,
//                    new HashMap(),
//                    (subscriptionId, publicationId, options, arguments, argumentsKw) -> {
//                        System.out.println(""
//                                + "SPY: onRegister: " + subscriptionId + "/" + publicationId
//                                + ":"
//                                + " options=" + options
//                                + ((arguments != null) ? ", args=" + arguments : "")
//                                + ((argumentsKw != null) ? ", argsKw=" + argumentsKw : "")
//                        );
//                    }));
//            spy.addWAMPEventListener(new WAMPEventListener.WAMPEventListenerBase(
//                    WAMPRPCRegistrations.TOPIC_ON_UNREGISTER,
//                    new HashMap(),
//                    (subscriptionId, publicationId, options, arguments, argumentsKw) -> {
//                        System.out.println(""
//                                + "SPY: onUnregister: " + subscriptionId + "/" + publicationId
//                                + ":"
//                                + " options=" + options
//                                + ((arguments != null) ? ", args=" + arguments : "")
//                                + ((argumentsKw != null) ? ", argsKw=" + argumentsKw : "")
//                        );
//                    }));
//            spy.addWAMPEventListener(new WAMPEventListener.WAMPEventListenerBase(
//                    WAMPRPCRegistrations.TOPIC_ON_DELETE,
//                    new HashMap(),
//                    (subscriptionId, publicationId, options, arguments, argumentsKw) -> {
//                        System.out.println(""
//                                + "SPY: onDelete: " + subscriptionId + "/" + publicationId
//                                + ":"
//                                + " options=" + options
//                                + ((arguments != null) ? ", args=" + arguments : "")
//                                + ((argumentsKw != null) ? ", argsKw=" + argumentsKw : "")
//                        );
//                    }));
//        }
        // prepare
        WAMPClient[] callers = new WAMPClient[maxClients];
        for (int i = 0; i < callers.length; i++) {
            callers[i] = clients.connect(wsURI, WAMP.WS_SUB_PROTOCOL_JSON, new WAMPFeature[]{WAMPFeature.shared_registration}, null, "agent_" + i, "OPENPOINT.TEST2", isItemOf(callees, i) ? Role.callee : Role.caller, Role.publisher, Role.subscriber);
            callers[i].addWAMPNodeListener(new WAMPNodeListenerDebug("CLIENT[" + i + "]: ").filter((session, msg, mf, error) -> {
                return false;//msg == null || msg.getType().getId() != WAMPMessageType.T_EVENT;
            }));
            //callers[i] = new WAMPClient(wsURI, "agent_" + i, "OPENSHIFT.TEST2", Role.callee, Role.caller, Role.publisher, Role.subscriber);
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
        for (int k = 0; k < maxCycles; k++) {
            for (int i = 0; i < callers.length; i++) {
                if (callers[i].isSessionEstablished()) {
                    established[i] = true;
                    if (!registered[i]) {
                        if (isItemOf(callees, i)) {
                            registered[i] = true;
                            final int ii = i;
                            callers[i].addExecutor(
                                    WAMPTools.createDict("invoke", "roundrobin"),
                                    "AAA",
                                    new Callee() {
                                @Override
                                public Future invoke(CalleeCall call, ExecutorService executor, final WAMPAuth auth, final String name, final List args, final Map argsKw) throws WAMPException {
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

                                            long delay = (long) (Math.random() * maxInProcDelay + 0);
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
                        } else {
                            // caller ?
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
                            if (!isItemOf(callees, i)) {
                                //System.out.println("Call[" + i + "]...");
                                final int ii = i;
                                List lst = WAMPTools.createList();
                                Map<String, Object> map = WAMPTools.createMap(true);
                                lst.add(10 + i * (int) (Math.random() * 50000));
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
        dumpCalls(established, registered, calls, false);

        System.out.println(sb.toString() + "\nwait " + (maxCompletionWait / 1000f) + " sec...");
        NetTools.delay(maxCompletionWait);
        dumpCalls(established, registered, calls, false);

        System.out.println(sb.toString() + "\nstatus before disconnect...");
        System.out.println("\n---- ROUTER:\n  " + router.getRouter().toString().replace("\n", "\n  "));
        for (WAMPClient cons : callers) {
            System.out.println("\n---- CALLEE/R:\n  " + cons.toString().replace("\n", "\n  "));
            Field fs = cons.getClass().getDeclaredField("session");
            fs.setAccessible(true);
            WAMPSession sess = (WAMPSession) fs.get(cons);
            System.out.println("    " + sess.getRealm().toString().replace("\n", "\n    "));
        }

        System.out.println(sb.toString() + "\nwait " + (maxPostCompletionWait / 1000f) + " sec. more..");
        NetTools.delay(maxPostCompletionWait);
        for (WAMPClient cons : callers) {
            System.out.println("\n---- CALLEE/R:\n  " + cons.toString().replace("\n", "\n  "));
            Field fs = cons.getClass().getDeclaredField("session");
            fs.setAccessible(true);
            WAMPSession sess = (WAMPSession) fs.get(cons);
            System.out.println("    " + sess.getRealm().toString().replace("\n", "\n    "));
            WAMPRealm realm = sess.getRealm();
            WAMPRPCCallee callee = realm.getActor(Role.callee);
            WAMPRPCCaller caller = realm.getActor(Role.caller);
            int a = 0;
        }

        // disconnect
        System.out.println(sb.toString() + "\ndisconnect all");
        for (int i = 0; i < callers.length; i++) {
            callers[i].disconnect("OK");
        }
        dumpCalls(established, registered, calls, false);

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

//        spy.disconnect("OK");
        System.out.println("STOPPED.");
        NetTools.delay(500);
        for (int i = 0; i < callers.length; i++) {
            if (!callers[i].isConnected()) {
                established[i] = false;
            }
        }
        dumpCalls(established, registered, calls, false);

        cs.stop();
        System.out.println("------------------------ Extension counters:"
                + "\n  timestamp: " + WebSocketExtensionTimestamp.getPrepareCount() + " / " + WebSocketExtensionTimestamp.getRestoreCount()
                + "\n  gzip     : " + WebSocketExtensionGZip.getPrepareCount() + " / " + WebSocketExtensionGZip.getRestoreCount()
        );

        System.out.println("\n---- ROUTER:\n  " + router.getRouter().toString().replace("\n", "\n  "));
        for (WAMPClient cons : callers) {
            System.out.println("\n---- CALLEE/R:\n  " + cons.toString().replace("\n", "\n  "));
            Field fs = cons.getClass().getDeclaredField("session");
            fs.setAccessible(true);
            WAMPSession sess = (WAMPSession) fs.get(cons);
            System.out.println("    " + sess.getRealm().toString().replace("\n", "\n    "));
        }

        dumpCalls(established, registered, calls, false);
    }
}
