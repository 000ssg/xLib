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
package ssg.lib.wamp.cs;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
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
import java.util.concurrent.atomic.AtomicLong;
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
import ssg.lib.wamp.events.WAMPBroker;
import ssg.lib.wamp.events.WAMPEventListener.WAMPEventListenerBase;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_REG_META_TOPIC_ON_CREATE;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_REG_META_TOPIC_ON_DELETE;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_REG_META_TOPIC_ON_REGISTER;
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
public class WAMP_CS {

    public static final String ROUTER_META_REALM = "router.state.info";
    public static final String ROUTER_STATE = "router.state.info";
    public static boolean TRACE_MESSAGES = true;

    public static void main(String... args) throws Exception {
        WebSocket.allExtensions.clear();
//        WebSocket.allExtensions.put("monitor", new WebSocketExtensionMonitor());

        CS cs = new CS();
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

        WAMPRouter_WSProtocol router = new WAMPRouter_WSProtocol() {
            long next = System.currentTimeMillis();

            {
                wampRouter.setAgent("WAMP_CS");
                wampRouter.configure(WAMPFeature.registration_meta_api, WAMPFeature.shared_registration);
                wampRouter.setMaxPendingMessagesQueue(0);
                setMaxInputQueueSize(0);
            }

            @Override
            public boolean onProduce(Channel provider, WebSocket ws) throws IOException {
                boolean r = super.onProduce(provider, ws);
                if (r) {
                    stateNotify();
                }
                return r;
            }

            @Override
            public boolean onConsume(Channel provider, WebSocket ws, Object message) throws IOException {
                if (TRACE_MESSAGES) {
                    System.out.println("[" + System.currentTimeMillis() + "][WR-" + ws.id + "]-IN: " + ("" + message).replace("\n", " ").replace("  ", " ").replace("  ", " ").replace("  ", " ").replace("  ", " ").replace("  ", " "));
                }
                boolean r = super.onConsume(provider, ws, message);
                if (r) {
                    stateNotify();
                }
                return r;
            }

            @Override
            public void onSend(WebSocket ws, Object message) {
                if (TRACE_MESSAGES) {
                    System.out.println("[" + System.currentTimeMillis() + "][WR-" + ws.id + "]-OU: " + ("" + message).replace("\n", " ").replace("  ", " ").replace("  ", " ").replace("  ", " ").replace("  ", " ").replace("  ", " "));
                }
            }

            @Override
            public void onStop(Object... parameters) {
                super.onStop(parameters);
            }

            @Override
            public void onStarted(Object... parameters) {
                super.onStarted(parameters);
                stateNotify();
            }

            @Override
            public void initialize(Channel provider, WebSocket ws) {
                super.initialize(provider, ws);
                //ws.setFrameMonitor(fm);
                //ws.getProcessor().DUMP_FRAMES=true;
            }

            @Override
            public void delete(Channel provider, WebSocket ws) {
                super.delete(provider, ws);
//                System.out.println("------------------------ Extension counters: just after close of " + provider
//                        + "\n  timestamp: " + WebSocketExtensionTimestamp.getPrepareCount() + " / " + WebSocketExtensionTimestamp.getRestoreCount()
//                        + "\n  gzip     : " + WebSocketExtensionGZip.getPrepareCount() + " / " + WebSocketExtensionGZip.getRestoreCount()
//                );
            }

            private AtomicLong nextMetaId = new AtomicLong(1);

            public void stateNotify() {
                if (1 == 1 || System.currentTimeMillis() < next) {
                    return;
                }
                next = System.currentTimeMillis() + 3000;
                WAMPRealm realm = wampRouter.find(ROUTER_META_REALM);
                if (realm != null) {
                    WAMPBroker broker = realm.getActor(Role.broker);
                    if (broker != null) {
                        try {
                            Map<String, Object> meta = wampRouter.getMeta(null);
                            broker.doEvent(
                                    null,
                                    0,
                                    ROUTER_STATE,
                                    nextMetaId.getAndIncrement(),
                                    WAMPTools.EMPTY_DICT,
                                    WAMPTools.EMPTY_LIST,
                                    meta);
                        } catch (WAMPException wex) {
                            wex.printStackTrace();
                        }
                    }
                }
            }

        };
        WAMPClient_WSProtocol clients = new WAMPClient_WSProtocol();

        WebSocket_CSGroup wsGroup = new WebSocket_CSGroup();
        wsGroup.addWebSocketProtocolHandler(
                router,
                clients
        );
        File recorderFolder = new File("./target/io");
        recorderFolder.mkdirs();
        DFRecorder<ByteBuffer, SocketChannel> drS = new DFRecorder<ByteBuffer, SocketChannel>(recorderFolder) {
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
        }.fullRecorder(new File(recorderFolder, "full.rec"));

        //wsGroup.getDI().filter(drS);
        cs.addCSGroup(wsGroup);

        int wsPort0 = 30010;
        int wsPort = 30020;
        wsGroup.addListenerAt(cs, InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), wsPort0);
        wsGroup.addListenerAt(cs, InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), wsPort);
        router.wampRouter.addWAMPNodeListener(new WAMPNodeListenerDebug("ROUTER: ").filter((session, msg, mf, error) -> {
            return false;//msg == null || msg.getType().getId() != WAMPMessageType.T_EVENT;
        }));

        cs.start();
        NetTools.delay(1000);
        System.out.println(""
                + "\n======================================="
                + "\n===== listening at " + wsPort
                + "\n===== " + (cs.toString()).replace("\n", "\n===== ")
                + "\n========================================");

        if (1 == 0) {

            WAMPClient spy = clients.connect(
                    new URI("ws://localhost:" + wsPort + "/ws"),
                    WAMP.WS_SUB_PROTOCOL_JSON,
                    null,
                    "spy",
                    "OPENPOINT.TEST.1",
                    Role.subscriber);
            WAMPClient rpc1 = clients.connect(
                    new URI("ws://localhost:" + wsPort + "/ws"),
                    WAMP.WS_SUB_PROTOCOL_JSON,
                    new WAMPFeature[]{WAMPFeature.shared_registration},
                    "rpc.1",
                    "OPENPOINT.TEST.1",
                    Role.callee);
            WAMPClient rpc2 = clients.connect(
                    new URI("ws://localhost:" + wsPort + "/ws"),
                    WAMP.WS_SUB_PROTOCOL_JSON,
                    new WAMPFeature[]{WAMPFeature.shared_registration},
                    "rpc.2",
                    "OPENPOINT.TEST.1",
                    Role.caller);

//        spy.addWAMPNodeListener(new WAMPNodeListenerDebug("SPY : "));
            rpc1.addWAMPNodeListener(new WAMPNodeListenerDebug("RPC1: "));
            rpc2.addWAMPNodeListener(new WAMPNodeListenerDebug("RPC2: "));

            spy.addWAMPEventListener(new WAMPEventListenerBase(
                    RPC_REG_META_TOPIC_ON_CREATE,
                    WAMPTools.EMPTY_DICT,
                    (subscriptionId, publicationId, options, arguments, argumentsKw) -> {
                        System.out.println(""
                                + "SPY: onCreate: " + subscriptionId + "/" + publicationId
                                + ":"
                                + " options=" + options
                                + ((args != null) ? ", args=" + arguments : "")
                                + ((argumentsKw != null) ? ", argsKw=" + argumentsKw : "")
                        );
                    }));
            spy.addWAMPEventListener(new WAMPEventListenerBase(
                    RPC_REG_META_TOPIC_ON_REGISTER,
                    WAMPTools.EMPTY_DICT,
                    (subscriptionId, publicationId, options, arguments, argumentsKw) -> {
                        System.out.println(""
                                + "SPY: onRegister: " + subscriptionId + "/" + publicationId
                                + ":"
                                + " options=" + options
                                + ((args != null) ? ", args=" + arguments : "")
                                + ((argumentsKw != null) ? ", argsKw=" + argumentsKw : "")
                        );
                    }));
            spy.addWAMPEventListener(new WAMPEventListenerBase(
                    RPC_REG_META_TOPIC_ON_DELETE,
                    WAMPTools.EMPTY_DICT,
                    (subscriptionId, publicationId, options, arguments, argumentsKw) -> {
                        System.out.println(""
                                + "SPY: onDelete: " + subscriptionId + "/" + publicationId
                                + ":"
                                + " options=" + options
                                + ((args != null) ? ", args=" + arguments : "")
                                + ((argumentsKw != null) ? ", argsKw=" + argumentsKw : "")
                        );
                    }));

            rpc1.addExecutor(WAMPTools.createDict("invoke", "roundrobin"),
                    "PROC_1",
                    new Callee() {
                @Override
                public Future invoke(CalleeCall call, ExecutorService executor, final String name, final List args, final Map argsKw) throws WAMPException {
                    System.out.println(Thread.currentThread().getName() + ":invoke[" + args + "|" + argsKw + "]");
                    return executor.submit(new Callable() {
                        @Override
                        public Object call() throws Exception {
                            return "Invoked PROC_1 with " + args + "|" + argsKw;
                        }
                    });
                }
            });

            for (int i = 0; i < 5; i++) {
                List lst = null;
                Map map = null;
                if (i == 0) {
                } else if (i % 2 == 0) {
                    lst = Arrays.asList(new Object[]{"aaa", 1, 3.1415926, 'F', new URI("ws://aa.ss.d+er"), new URL("http://localhost:23334/yyt?as=34")});
                } else {
                    lst = WAMPTools.EMPTY_LIST;
                    map = WAMPTools.createMap(true);
                    map.put("aaa", 3.1415926);
                    map.put("F", new URI("ws://aa.ss.d+er"));
                    map.put("http://localhost:23334/yyt?as=34", "dfg");
                }
                rpc2.addWAMPRPCListener(new WAMPRPCListenerBase(WAMPTools.EMPTY_DICT, "PROC_1", lst, map) {
                    @Override
                    public void onCancel(long callId, String reason) {
                        System.out.println("cancelled " + getProcedure() + ": " + reason);
                    }

                    @Override
                    public boolean onResult(long callId, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                        System.out.println(""
                                + "result of " + getProcedure()
                                + ":"
                                + " callId=" + callId
                                + ", details=" + details
                                + ((args != null) ? ", args=" + args : "")
                                + ((argsKw != null) ? ", argsKw=" + argsKw : "")
                        );
                        return true;
                    }

                    @Override
                    public void onError(long callId, String error, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                        System.out.println(""
                                + "error in " + getProcedure()
                                + ":"
                                + " callId=" + callId
                                + ", error=" + error
                                + ", details=" + details
                                + ((args != null) ? ", args=" + args : "")
                                + ((argsKw != null) ? ", argsKw=" + argsKw : "")
                        );
                    }
                });
            }
        }

        final long[] ram = new long[3];
        cs.getScheduledExecutorService().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                StringBuilder sb = new StringBuilder();
                Runtime rt = Runtime.getRuntime();

                long freeRAM = rt.freeMemory();
                long maxRAM = rt.maxMemory();
                long totalRAM = rt.totalMemory();

                long minDelta = 1024;

                if (Math.abs(ram[0] - freeRAM) > minDelta || Math.abs(ram[1] - maxRAM) > minDelta || Math.abs(ram[2] - totalRAM) > minDelta) {
                    sb.append("STAT WAMP_CS[" + System.currentTimeMillis() + "] used/free/total/max RAM=" + ((maxRAM - freeRAM) / 1024 / 1024f) + " / " + freeRAM / 1024 / 1024f + " / " + totalRAM / 1024 / 1024f + " / " + maxRAM / 1024 / 1024f + " MB");
                    ram[0] = freeRAM;
                    ram[1] = maxRAM;
                    ram[2] = totalRAM;
                    String s = router.wampRouter.getStatistics().dumpStatistics(false);
                    if (s.contains("\n")) {
                        sb.append("\n  " + s.replace("\n", "\n  "));
                    }
                    System.out.println(sb.toString());
                }
            }
        }, 0, 5, java.util.concurrent.TimeUnit.SECONDS);

        NetTools.delay(1000 * 60 * 60 * 15);

        while (true) {
            WAMPRealm realm = router.getRouter().find("OPENPOINT_COMMON");
            if (realm == null) {
                break;
            }
        }

        cs.stop();
    }
}
