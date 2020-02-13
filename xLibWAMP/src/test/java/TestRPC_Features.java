
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.nodes.WAMPClient;
import ssg.lib.wamp.nodes.WAMPRouter;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_PROGRESSIVE_CALL_PROGRESS;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_PROGRESSIVE_CALL_REQUEST;
import ssg.lib.wamp.rpc.impl.WAMPRPCListener.WAMPRPCListenerBase;
import ssg.lib.wamp.rpc.impl.callee.CalleeCall;
import ssg.lib.wamp.rpc.impl.callee.CalleeProcedure.Callee;
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
public class TestRPC_Features {

    public static void main(String... args) throws Exception {
        final WAMPRouter router = new WAMPRouter(Role.dealer, Role.broker);
        router.addDefaultFeatures(
                WAMPFeature.sharded_registration,
                WAMPFeature.shared_registration,
                WAMPFeature.progressive_call_results,
                WAMPFeature.call_canceling
        );

        final WAMPClient[] callees = new WAMPClient[3];
        for (int i = 0; i < callees.length; i++) {
            WAMPTransportList.WAMPTransportLoop crt = new WAMPTransportList.WAMPTransportLoop();
            router.onNewTransport(crt.remote);
            WAMPClient callee = new WAMPClient()
                    .configure(
                            crt.local,
                            new WAMPFeature[]{
                                WAMPFeature.sharded_registration,
                                WAMPFeature.shared_registration,
                                WAMPFeature.progressive_call_results
                            },
                            "callee_" + i,
                            "testRealm",
                            Role.callee
                    );
            callees[i] = callee;
            callee.connect();
        }

        final WAMPClient[] callers = new WAMPClient[3];
        for (int i = 0; i < callers.length; i++) {
            WAMPTransportList.WAMPTransportLoop crt = new WAMPTransportList.WAMPTransportLoop();
            router.onNewTransport(crt.remote);
            WAMPClient caller = new WAMPClient()
                    .configure(
                            crt.local,
                            (i == 0 || i == 2) ? new WAMPFeature[]{WAMPFeature.call_canceling} : new WAMPFeature[]{WAMPFeature.progressive_call_results, WAMPFeature.call_canceling},
                            "caller_" + i + "_" + (((i == 0) ? "no_partial" : "partial")),
                            "testRealm",
                            Role.caller
                    );
            callers[i] = caller;
            caller.connect();
        }

        Thread runner = new Thread(() -> {
            Thread.currentThread().setName("runner");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    router.runCycle();
                    for (WAMPClient c : callees) {
                        if (c.isConnected()) {
                            c.runCycle();
                        }
                    }
                    for (WAMPClient c : callers) {
                        if (c.isConnected()) {
                            c.runCycle();
                        }
                    }
                    Thread.sleep(10);
                } catch (Throwable th) {
                    if (th instanceof InterruptedException) {
                    } else {
                        th.printStackTrace();
                    }
                    break;
                }
            }
        });
        runner.setDaemon(true);
        runner.start();

        callees[0].addExecutor(WAMPTools.createDict("invoke", "roundrobin"), "A", new Callee() {
            @Override
            public Future invoke(final CalleeCall call, ExecutorService executor, final String name, final List args, final Map argsKw) throws WAMPException {
                return executor.submit(new Callable() {
                    @Override
                    public Object call() throws Exception {
                        String[] text = ("[" + call.getAgent() + "][" + name + "]Line 1.|[" + call.getAgent() + "][" + name + "]Line 2.|[" + call.getAgent() + "][" + name + "]Line 3.").split("\\|");
                        if (call.isProgressiveResult()) {
                            for (int i = 0; i < text.length - 1; i++) {
                                partial(call, Collections.singletonList(text[i]), null);
                            }
                            return text[text.length - 1];
                        } else {
                            return text;
                        }
                    }
                });
            }
        });

        Callee sharedProc = new Callee() {
            @Override
            public Future invoke(final CalleeCall call, ExecutorService executor, final String name, final List args, final Map argsKw) throws WAMPException {
                return executor.submit(new Callable() {
                    @Override
                    public Object call() throws Exception {
                        String[] text = ("SHARED[" + call.getAgent() + "][" + name + "] Line 1.|SHARED[" + call.getAgent() + "][" + name + "] Line 2.|SHARED[" + call.getAgent() + "][" + name + "] Line 3.").split("\\|");
                        if (call.isProgressiveResult()) {
                            for (int i = 0; i < text.length - 1; i++) {
                                partial(call, Collections.singletonList(text[i]), null);
                                Thread.sleep((long) (Math.random() * 100));
                            }
                            return text[text.length - 1];
                        } else {
                            return text;
                        }
                    }
                });
            }
        };
        Callee shardedProc = new Callee() {
            @Override
            public Future invoke(final CalleeCall call, ExecutorService executor, final String name, final List args, final Map argsKw) throws WAMPException {
                return executor.submit(new Callable() {
                    @Override
                    public Object call() throws Exception {
                        String[] text = ("SHARDED[" + call.getAgent() + "][" + name + "] Line 1.|SHARDED[" + call.getAgent() + "][" + name + "] Line 2.|SHARDED[" + call.getAgent() + "][" + name + "] Line 3.").split("\\|");
                        if (call.isProgressiveResult()) {
                            for (int i = 0; i < text.length - 1; i++) {
                                partial(call, Collections.singletonList(text[i]), null);
                                Thread.sleep((long) (Math.random() * 110));
                            }
                            return text[text.length - 1];
                        } else {
                            return text;
                        }
                    }
                });
            }
        };

        for (WAMPClient c : callees) {
            c.addExecutor(WAMPTools.createDict("invoke", "roundrobin"), "B", sharedProc);
            c.addExecutor(WAMPTools.createDict("invoke", "sharded"), "C", shardedProc);
        }

        System.out.println(""
                + "\n---------------------------------------"
                + "\n================================ A - progressive test"
                + "\n---------------------------------------"
        );

        for (int i = 0; i < 5; i++) {
            for (final WAMPClient c : callers) {
                c.addWAMPRPCListener(new WAMPRPCListenerBase(WAMPTools.createDict(RPC_PROGRESSIVE_CALL_REQUEST, true), "A", null, null) {
                    @Override
                    public void onCancel(long callId, String reason) {
                        System.out.println("cancelled[" + c.getAgent() + ", " + callId + "] " + reason);
                    }

                    @Override
                    public boolean onResult(long callId, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                        System.out.println("result[" + c.getAgent() + ", " + callId + "] " + details + "; " + args + "; " + argsKw);
                        return !(details.containsKey(RPC_PROGRESSIVE_CALL_PROGRESS) && (Boolean) details.get(RPC_PROGRESSIVE_CALL_PROGRESS));
                    }

                    @Override
                    public void onError(long callId, String error, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                        System.out.println("error[" + c.getAgent() + ", " + callId + "] " + error + "; " + details + "; " + args + "; " + argsKw);
                    }
                });
                Thread.sleep(100);
            }
        }

        System.out.println(""
                + "\n---------------------------------------"
                + "\n================================ B - shared test"
                + "\n---------------------------------------"
        );

        for (int i = 0; i < 5; i++) {
            for (final WAMPClient c : callers) {
                c.addWAMPRPCListener(new WAMPRPCListenerBase(WAMPTools.createDict(RPC_PROGRESSIVE_CALL_REQUEST, true), "B", null, null) {
                    @Override
                    public void onCancel(long callId, String reason) {
                        System.out.println("cancelled[" + c.getAgent() + ", " + callId + "] " + reason);
                    }

                    @Override
                    public boolean onResult(long callId, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                        System.out.println("result[" + c.getAgent() + ", " + callId + "] " + details + "; " + args + "; " + argsKw);
                        return !(details.containsKey(RPC_PROGRESSIVE_CALL_PROGRESS) && (Boolean) details.get(RPC_PROGRESSIVE_CALL_PROGRESS));
                    }

                    @Override
                    public void onError(long callId, String error, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                        System.out.println("error[" + c.getAgent() + ", " + callId + "] " + error + "; " + details + "; " + args + "; " + argsKw);
                    }
                });
                Thread.sleep(100);
            }
        }

        System.out.println(""
                + "\n---------------------------------------"
                + "\n================================ C - sharded test"
                + "\n---------------------------------------"
        );

        final AtomicInteger shardedCallID=new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            for (final WAMPClient c : callers) {
                if (c.supportsFeature(WAMPFeature.progressive_call_results)) {
                    System.out.println("----------------------------- "+c.getAgent());
                    c.addWAMPRPCListener(new WAMPRPCListenerBase(WAMPTools.createDict(RPC_PROGRESSIVE_CALL_REQUEST, true), "C", null, null) {
                        int callNum=shardedCallID.incrementAndGet();
                        int partsCount=0;
                        @Override
                        public void onCancel(long callId, String reason) {
                            System.out.println("cancelled[" + c.getAgent() + ", " + callId + "] " + reason);
                        }

                        @Override
                        public boolean onResult(long callId, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                            System.out.println("result[" + c.getAgent() + ", " + callId + ", "+callNum+"#"+(partsCount++)+"] " + details + "; " + args + "; " + argsKw);
                            return !(details.containsKey(RPC_PROGRESSIVE_CALL_PROGRESS) && (Boolean) details.get(RPC_PROGRESSIVE_CALL_PROGRESS));
                        }

                        @Override
                        public void onError(long callId, String error, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                            System.out.println("error[" + c.getAgent() + ", " + callId + "] " + error + "; " + details + "; " + args + "; " + argsKw);
                        }
                    });
                    Thread.sleep(100);
                }
            }
        }

        Thread.sleep(1000 * 10);

        for (WAMPClient c : callers) {
            if (c.isConnected()) {
                c.disconnect("OK");
            }
        }
        for (WAMPClient c : callees) {
            if (c.isConnected()) {
                c.disconnect("OK");
            }
        }

        runner.interrupt();
    }
}
