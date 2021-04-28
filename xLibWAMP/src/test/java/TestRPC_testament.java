
import ssg.lib.wamp.util.WAMPTransportList;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.events.WAMPEventListener;
import ssg.lib.wamp.features.WAMP_FP_TestamentMetaAPI;
import ssg.lib.wamp.nodes.WAMPClient;
import ssg.lib.wamp.nodes.WAMPNode;
import ssg.lib.wamp.rpc.impl.callee.WAMPRPCCallee;
import ssg.lib.wamp.stat.WAMPStatistics;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.util.WAMPTools;

/*
 * The MIT License
 *
 * Copyright 2020 000ssg.
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
public class TestRPC_testament {

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

    public static void doReflectionRPCTest(WAMPClient cl, String title, String listProc, String descrProc) throws WAMPException {
        Object pl = cl.call(listProc, null, null);
        System.out.println(title + " LIST: " + pl);
        Object pls = cl.call(descrProc, (List) pl, null);
        System.out.println(title + "S' DT: " + pls);
    }

    public static void main(String... args) throws Exception {
        WAMPNode.DUMP_ESTABLISH_CLOSE = false;
        WAMPTransportList.GLOBAL_ENABLE_TRACE_MESSAGES = true;
        

        TestWorld world = new TestWorld();
        world.getRouter()
                .configure(WAMPFeature.x_testament_meta_api, new WAMP_FP_TestamentMetaAPI())
                .configure(new WAMPStatistics());
        world.start();

        String realm = "test";
        WAMPClient cee1 = world.connect(realm, "cee1", new WAMP.Role[]{WAMP.Role.callee, WAMP.Role.caller, WAMP.Role.publisher}, "user-1", world.wapticket, WAMPFeature.shared_registration, WAMPFeature.x_testament_meta_api);
        WAMPClient cee2 = world.connect(realm, "cee2", new WAMP.Role[]{WAMP.Role.callee, WAMP.Role.caller}, "user-1", world.wapticket, WAMPFeature.shared_registration, WAMPFeature.x_testament_meta_api);
        WAMPClient cee3 = world.connect(realm, "cee3", new WAMP.Role[]{WAMP.Role.callee, WAMP.Role.caller}, "user-1", world.wapticket, WAMPFeature.shared_registration, WAMPFeature.x_testament_meta_api);

        cee1.waitEstablished(10000L);
        cee2.waitEstablished(100L);
        cee3.waitEstablished(100L);
        
        int cnt=0;
        for (WAMPClient c : new WAMPClient[]{cee1, cee1, cee2, cee3}) {
            c.call(WAMP_FP_TestamentMetaAPI.TM_RPC_ADD_TESTAMENT,
                    WAMPTools.createList(
                            "cee.testament.destroyed",
                            WAMPTools.createList("AAA",c.getAgent(), cnt++),
                            WAMPTools.createDict("A", "B")),
                    WAMPTools.createDict(
                            WAMP_FP_TestamentMetaAPI.TM_OPTION_SCOPE,
                            WAMP_FP_TestamentMetaAPI.TM_SCOPE_DESTROYED)
            );
            c.call(WAMP_FP_TestamentMetaAPI.TM_RPC_ADD_TESTAMENT,
                    WAMPTools.createList(
                            "cee.testament.detached",
                            WAMPTools.createList("AAA"),
                            WAMPTools.createDict("A", "B")),
                    WAMPTools.createDict(
                            WAMP_FP_TestamentMetaAPI.TM_OPTION_SCOPE,
                            WAMP_FP_TestamentMetaAPI.TM_SCOPE_DETACHED)
            );
        }

        System.out.println("\n--------------------------------------\n--- router info\n--------------------------------\n"+world.getRouter().toString().replace("\n", "\n--  "));
        
        WAMPClient cli = world.connect(realm, "cli", new WAMP.Role[]{WAMP.Role.caller, WAMP.Role.subscriber}, "user-2", world.wapticket);
        cli.addWAMPEventListener(new WAMPEventListener() {
            long subscriptionId;

            @Override
            public String getTopic() {
                return "cee.testament.destroyed";
            }

            @Override
            public Map<String, Object> getOptions() {
                return WAMPTools.EMPTY_DICT;
            }

            @Override
            public long getSubscriptionId() {
                return subscriptionId;
            }

            @Override
            public void onSubscribed(long subscriptionId) {
                this.subscriptionId = subscriptionId;
            }

            @Override
            public WAMPEventListener.WAMPEventHandler handler() {
                return new WAMPEventListener.WAMPEventHandler() {
                    @Override
                    public void onEvent(long subscriptionId, long publicationId, Map<String, Object> options, List arguments, Map<String, Object> argumentsKw) {
                        System.out.println("EVENT[" + subscriptionId + "/" + publicationId + "][" + options + "]: "+getTopic()
                                + "\n  args  : " + arguments
                                + "\n  argsKw: " + argumentsKw
                        );
                    }
                };
            }
        });

        Thread.sleep(100);
            cee2.call(WAMP_FP_TestamentMetaAPI.TM_RPC_FLUSH_TESTAMENT,
                    WAMPTools.EMPTY_LIST,
                    WAMPTools.createDict(
                            WAMP_FP_TestamentMetaAPI.TM_OPTION_SCOPE,
                            WAMP_FP_TestamentMetaAPI.TM_SCOPE_DESTROYED)
            );
        
        Thread.sleep(100);

        cee1.disconnect("aaa.bbb");
        cee2.disconnect("aaa.ccc");
        cee3.disconnect("aaa.ddd");

        Thread.sleep(1000);
        System.out.println("\n--------------------------------------\n--- router info\n--------------------------------\n"+world.getRouter().toString().replace("\n", "\n--  "));
        world.stop();
    }
}
