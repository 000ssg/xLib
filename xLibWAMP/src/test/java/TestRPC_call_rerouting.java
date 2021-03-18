
import ssg.lib.wamp.util.WAMPTransportList;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicIntegerArray;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.nodes.WAMPClient;
import ssg.lib.wamp.nodes.WAMPNode;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_CALL_INVOKE_KEY;
import ssg.lib.wamp.rpc.impl.callee.CalleeCall;
import ssg.lib.wamp.rpc.impl.callee.CalleeProcedure.Callee;
import ssg.lib.wamp.rpc.impl.callee.WAMPRPCCallee;
import ssg.lib.wamp.rpc.impl.caller.CallerCall.SimpleCallListener;
import ssg.lib.wamp.rpc.impl.dealer.WAMPRPCRegistrations;
import ssg.lib.wamp.stat.WAMPStatistics;
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
public class TestRPC_call_rerouting {

    static Field clientSessionF;

    static {
        try {
            clientSessionF = WAMPClient.class.getDeclaredField("session");
            clientSessionF.setAccessible(true);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public static WAMPClient setLimits(WAMPClient client, int maxConcurrentCalls) {
        try {
            WAMPSession session = (WAMPSession) clientSessionF.get(client);
            WAMPRPCCallee wc = session.getRealm().getActor(WAMP.Role.callee);
            wc.setMaxConcurrentTasks(maxConcurrentCalls);
            wc.setMaxQueuedTasks(maxConcurrentCalls + 2);
        } catch (Throwable th) {
        }
        return client;
    }

    public static void main(String... args) throws Exception {
        WAMPNode.DUMP_ESTABLISH_CLOSE = false;
        WAMPTransportList.GLOBAL_ENABLE_TRACE_MESSAGES = false;

        TestWorld world = new TestWorld();
        world.getRouter()
                .configure(WAMPFeature.call_reroute)
                .configure(new WAMPStatistics());
        world.start();

        String realm = "test";
        WAMPClient cee1 = setLimits(world.connect(realm, "cee1", new WAMP.Role[]{WAMP.Role.callee}, "user-1", world.wapticket, WAMPFeature.shared_registration, WAMPFeature.call_reroute), 2);
        WAMPClient cee2 = setLimits(world.connect(realm, "cee2", new WAMP.Role[]{WAMP.Role.callee}, "user-1", world.wapticket, WAMPFeature.shared_registration, WAMPFeature.call_reroute), 3);
        WAMPClient cee3 = setLimits(world.connect(realm, "cee3", new WAMP.Role[]{WAMP.Role.callee}, "user-1", world.wapticket, WAMPFeature.shared_registration, WAMPFeature.call_reroute), 2);

        final AtomicIntegerArray counters = new AtomicIntegerArray(4);

        Callee proc = new Callee() {
            @Override
            public Future invoke(CalleeCall call, ExecutorService executor, String name, List args, Map argsKw) throws WAMPException {
                return executor.submit(new Callable() {
                    @Override
                    public Object call() throws Exception {
                        counters.incrementAndGet(2);
                        //System.out.println("["+call.getAgent()+"] called: " + name + ": " + args);
                        long st = System.nanoTime();
                        String text = ("call[" + call.getAgent() + "][" + name + "] " + ": " + st + " " + (st - (Long) args.get(0)) / 1000000f);
                        Thread.sleep((long) (Math.random() * 10 + 5));
                        counters.incrementAndGet(3);
                        return text;
                    }
                });
            }
        };
        cee1.addExecutor(WAMPTools.createDict(RPC_CALL_INVOKE_KEY, WAMPRPCRegistrations.RPCMeta.InvocationPolicy.roundrobin), "aaa", proc);
        cee2.addExecutor(WAMPTools.EMPTY_DICT, "aaa", proc);
        cee3.addExecutor(WAMPTools.EMPTY_DICT, "aaa", proc);

        Thread.sleep(1000);

        WAMPClient cl = world.connect(realm, "cl", new WAMP.Role[]{WAMP.Role.caller}, "user-1", world.wapticket);
        int total=250;
        for (int i = 0; i < total; i++) {
            long nano = System.nanoTime();
            cl.call2("aaa", WAMPTools.createList(nano), null, new SimpleCallListener() {
                @Override
                public void onResult(Object result, String error) {
                    if (result != null) {
                        System.out.println("CALL OK: " + result);
                        counters.incrementAndGet(0);
                    } else {
                        System.out.println("CALL ER: " + error);
                        counters.incrementAndGet(1);
                    }
                }
            });
        }

        Thread.sleep(500);
        Thread.sleep(500);

        String s = world.getRouter().toString();

        String s1 = "  counters:"
                + "\n  OK: " + counters.get(0) // received OK
                + "\n  ER: " + counters.get(1) // received ERR
                + "\n  ST: " + counters.get(2) // started func
                + "\n  CT: " + counters.get(3) // ended func
                + "\n  CALLER: " + (counters.get(0) + counters.get(1))
                + "\n  DELTA : " + (counters.get(0) + counters.get(1) - counters.get(3));

        long timeout=System.currentTimeMillis()+1000*2;
        while((counters.get(0) + counters.get(1))<total && counters.get(2)<total && System.currentTimeMillis()<timeout){
            Thread.sleep(1);
        }
        
        world.stop();
        Thread.sleep(200);

        System.out.println("--------------------------------------------------------------------\n" + s);
        System.out.println("--------------------------------------------------------------------\n"
                +s1+"\n-----------------\n"
                + "  counters:"
                + "\n  OK: " + counters.get(0) // received OK
                + "\n  ER: " + counters.get(1) // received ERR
                + "\n  ST: " + counters.get(2) // started func
                + "\n  CT: " + counters.get(3) // ended func
                + "\n  CALLER: " + (counters.get(0) + counters.get(1))
                + "\n  DELTA : " + (counters.get(0) + counters.get(1) - counters.get(3))
        );
    }
}
