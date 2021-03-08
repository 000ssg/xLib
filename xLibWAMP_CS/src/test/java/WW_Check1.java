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
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.cs.WAMPClient_WSProtocol;
import ssg.lib.wamp.events.WAMPEventListener;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_REG_META_TOPIC_ON_CREATE;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_REG_META_TOPIC_ON_DELETE;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_REG_META_TOPIC_ON_REGISTER;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_REG_META_TOPIC_ON_UNREGISTER;
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
public class WW_Check1 {

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
        WW_Check1.dumpCalls(established, registered, calls, true);
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
        System.out.println(sb);
    }

    public static void main(String... args) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("\n===============================================================================================");
        for (int i = 0; i < 7; i++) {
            sb.append("\n====---------------------------------------------------------------------------------------====");
        }
        sb.append("\n===============================================================================================");

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

        };
        WebSocket_CSGroup wsGroup = new WebSocket_CSGroup();
        wsGroup.addWebSocketProtocolHandler(
                clients
        );
        //wsGroup.getDI().filter(drC);
        cs.addCSGroup(wsGroup);
        cs.start();

        int wsPort = 30020;
        URI wsURI = new URI("ws://localhost:" + wsPort + "/ws");

        // prepare spy
        WAMPClient spy = clients.connect(wsURI, WAMP.WS_SUB_PROTOCOL_JSON, null, null, "agent_spy", "OPENPOINT.TEST2", Role.subscriber);
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
            callers[i] = clients.connect(wsURI, WAMP.WS_SUB_PROTOCOL_JSON, new WAMPFeature[]{WAMPFeature.shared_registration}, null, "agent_" + i, "OPENPOINT.TEST2", Role.callee, Role.caller, Role.publisher, Role.subscriber);
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
        for (int k = 0; k < 1000; k++) {
            for (int i = 0; i < callers.length; i++) {
                if (callers[i].isSessionEstablished()) {
                    established[i] = true;
                    if (!registered[i]) {
                        if ((i % 2) == 0) {
                            callers[i].setMaxPendingMessagesQueue(0);
                            Field fs = callers[i].getClass().getDeclaredField("session");
                            fs.setAccessible(true);
                            WAMPSession sess = (WAMPSession) fs.get(callers[i]);
                            WAMPRPCCallee rpcc = sess.getRealm().getActor(Role.callee);
                            if (rpcc != null) {
                                rpcc.setMaxQueuedTasks(10);
                                rpcc.setMaxConcurrentTasks(10);
                            }
                            registered[i] = true;
                            final int ii = i;
                            callers[i].addExecutor(
                                    WAMPTools.createDict("invoke", "roundrobin"),
                                    "AAA", new Callee() {
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
                        } else {
                            Field fs = callers[i].getClass().getDeclaredField("session");
                            fs.setAccessible(true);
                            WAMPSession sess = (WAMPSession) fs.get(callers[i]);
                            WAMPRPCCaller rpcc = sess.getRealm().getActor(Role.caller);
                            if (rpcc != null) {
                                rpcc.setMaxConcurrentCalls(50);
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
        dumpCalls(established, registered, calls);

        System.out.println(sb.toString() + "\nwait 30 sec...");
        NetTools.delay(1000 * 30);
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

        cs.stop();
        System.out.println("------------------------ Extension counters:"
                + "\n  timestamp: " + WebSocketExtensionTimestamp.getPrepareCount() + " / " + WebSocketExtensionTimestamp.getRestoreCount()
                + "\n  gzip     : " + WebSocketExtensionGZip.getPrepareCount() + " / " + WebSocketExtensionGZip.getRestoreCount()
        );

        for (WAMPClient cons : callers) {
            System.out.println("\n---- CALLEE/R:\n  " + cons.toString().replace("\n", "\n  "));
        }

    }
}
