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
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.nodes.WAMPClient;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.nodes.WAMPRouter;
import ssg.lib.wamp.rpc.impl.WAMPRPCListener;
import ssg.lib.wamp.rpc.impl.WAMPRPCListener.WAMPRPCListenerBase;
import ssg.lib.wamp.rpc.impl.callee.CalleeCall;
import ssg.lib.wamp.rpc.impl.callee.CalleeProcedure.Callee;
import ssg.lib.wamp.rpc.impl.dealer.WAMPRPCRegistrations.RPCMeta.InvocationPolicy;
import ssg.lib.wamp.util.WAMPTools;
import ssg.lib.wamp.stat.WAMPStatistics;

/**
 *
 * @author 000ssg
 */
public class WAMP_TestRPC implements Runnable {

    String wsURI = "ws://localhost:30020/ws";
    String wsRealm = "OPENPOINT.TEST";
    WAMPFeature[] wsFeatures = new WAMPFeature[]{WAMPFeature.shared_registration};

    Thread runner;

    WAMPRouter router;
    List<WAMPClient> clients = WAMPTools.createSynchronizedList();

    public URI getRouterURI() {
        try {
            return new URI(wsURI);
        } catch (URISyntaxException usex) {
        }
        return null;
    }

    public String getRealm() {
        return wsRealm;
    }

    public WAMPFeature[] getFeatures() {
        return wsFeatures;
    }

    public void start() {
        if (runner == null) {
            if (router == null) {
                router = new WAMPRouter(Role.broker, Role.dealer)
                        .configure(WAMPFeature.registration_meta_api)
                        .configure(getFeatures())
                        .configure(new WAMPStatistics("router"));
            }
            runner = new Thread(this);
            runner.setDaemon(true);
            runner.start();
        }
    }

    public void stop() {
        if (clients != null) {
            for (WAMPClient c : clients) {
                try {
                    c.disconnect("OK");
                } catch (Throwable th) {
                }
            }
        }
        if (runner != null) {
            runner.interrupt();
        }
    }

    public <T extends WAMPClient> T connect(
            URI uri,
            String api,
            WAMPFeature[] features,
            String agent,
            String realm,
            Role... roles
    ) throws WAMPException {
        WAMPTransportList.WAMPTransportLoop transport = new WAMPTransportList.WAMPTransportLoop();
        WAMPClient client
                = (WAMP.Role.hasRole(Role.callee, roles) && !WAMP.Role.hasRole(Role.caller, roles))
                ? new WAMP_Callee().configure(
                        transport.local,
                        WAMPFeature.merge(features, WAMPFeature.shared_registration),
                        agent,
                        realm,
                        roles
                )
                : (!WAMP.Role.hasRole(Role.callee, roles) && WAMP.Role.hasRole(Role.caller, roles))
                ? new WAMP_Caller().configure(
                        transport.local,
                        WAMPFeature.merge(features, WAMPFeature.shared_registration),
                        agent,
                        realm,
                        roles
                )
                : new WAMPClient().configure(
                        transport.local,
                        features,
                        agent,
                        realm,
                        roles
                );
        clients.add(client);
        router.onNewTransport(transport.remote);
        client.connect();
        return (T) client;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                router.runCycle();
                if (!clients.isEmpty()) {
                    Collection<WAMPClient> cs = WAMPTools.createList();
                    cs.addAll(clients);
                    Collection<WAMPClient> closed = WAMPTools.createSet(false);
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

    public static class WAMP_Callee extends WAMPClient {

        public WAMP_Callee() {
        }

        public void addProcedure(String name, WAMPCallable callable) throws WAMPException {
            addExecutor(
                    WAMPTools.createDict("invoke", InvocationPolicy.roundrobin.name()),
                    name,
                    new Callee() {
                @Override
                public Future invoke(CalleeCall call, ExecutorService executor, final String name, final List args, final Map argsKw) throws WAMPException {
                    return executor.submit(callable.getCallable(name, args, argsKw));
                }
            });
        }

        public static interface WAMPCallable {

            Callable getCallable(String name, List args, Map argsKw);
        }
    }

    public static class WAMP_Caller extends WAMPClient {

        public WAMP_Caller() {
        }

        public WAMPRPCListener addCall(String name, CallListener cl) throws WAMPException {
            return addCall(name, null, null, cl);
        }

        public WAMPRPCListener addCall(String name, List parametersList, CallListener cl) throws WAMPException {
            return addCall(name, parametersList, null, cl);
        }

        public WAMPRPCListener addCall(String name, Map<String, Object> parametersMap, CallListener cl) throws WAMPException {
            return addCall(name, null, parametersMap, cl);
        }

        public WAMPRPCListener addCall(String name, List parametersList, Map<String, Object> parametersMap, final CallListener cl) throws WAMPException {
            WAMPRPCListener c = new WAMPRPCListenerBase(
                    WAMPTools.EMPTY_DICT,
                    name,
                    parametersList,
                    parametersMap
            ) {
                @Override
                public void onCancel(long callId, String reason) {
                    cl.onResult(null, null, null, "" + reason);
                }

                @Override
                public boolean onResult(long callId, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                    cl.onResult(args, argsKw, null, null);
                    return true;
                }

                @Override
                public void onError(long callId, String error, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                    cl.onResult(args, argsKw, "" + error, null);
                }
            };
            addWAMPRPCListener(c);
            return c;
        }

        public static interface CallListener {

            void onResult(List args, Map<String, Object> argsKw, String error, String cancel);
        }
    }

    public static void main(String... args) throws WAMPException, InterruptedException {
        WAMP_TestRPC wamp = new WAMP_TestRPC();
        wamp.start();

        List<String> procNames = WAMPTools.createList("A:", "B:", "C:");

        final boolean silent = true;

        WAMP_Callee[] callees = new WAMP_Callee[2];
        WAMP_Caller[] callers = new WAMP_Caller[10];

        for (int i = 0; i < callees.length; i++) {
            callees[i] = wamp.connect(wamp.getRouterURI(), "api", wamp.getFeatures(), "callee_" + i, wamp.getRealm(), WAMP.Role.callee);
        }
        for (int i = 0; i < callers.length; i++) {
            callers[i] = wamp.connect(wamp.getRouterURI(), "api", wamp.getFeatures(), "caller_" + i, wamp.getRealm(), WAMP.Role.caller);
        }

        for (int i = 0; i < callees.length; i++) {
            final int idx = i;
            int nIdx = (int) (Math.random() * procNames.size());
            if (nIdx == procNames.size()) {
                nIdx = procNames.size() - 1;
            }
            callees[i].addProcedure(procNames.get(nIdx), (name, list, map) -> {
                return new Callable() {
                    @Override
                    public Object call() throws Exception {
                        return name + "[" + idx + "]: " + list + ((map != null) ? ", " + map : "");
                    }
                };
            });
        }

        final AtomicInteger callsCounter = new AtomicInteger();
        for (int i = 0; i < 1000; i++) {
            for (WAMP_Caller caller : callers) {
                int nIdx = (int) (Math.random() * procNames.size());
                if (nIdx == procNames.size()) {
                    nIdx = procNames.size() - 1;
                }

                callsCounter.incrementAndGet();
                caller.addCall(procNames.get(nIdx), Arrays.asList(new Object[]{
                    i, "a", "vvb", 1, Math.PI
                }), (list, map, error, cancel) -> {
                    try {
                        StringBuilder sb = new StringBuilder();
                        if (error != null) {
                            sb.append("ERROR");
                        } else if (cancel != null) {
                            sb.append("CANCEL");
                        } else {
                            sb.append("RESULT");
                        }
                        sb.append(": ");
                        if (list != null) {
                            sb.append(" list=" + list);
                        }
                        if (map != null) {
                            sb.append(" map=" + map);
                        }
                        if (error != null) {
                            sb.append("  " + error);
                        }
                        if (cancel != null) {
                            sb.append("  " + cancel);
                        }

                        if (!silent) {
                            System.out.println(sb.toString() + " / " + callsCounter.get());
                        }
                    } finally {
                        callsCounter.decrementAndGet();
                    }
                });
            }
        }

        Thread.sleep(1000);

        while (callsCounter.get() > 0) {
            Thread.sleep(10);
        }

        System.out.println(wamp.router.toString());
        for (WAMP_Callee callee : callees) {
            System.out.println("\n---------------------------------------\n" + callee.toString());
        }
        for (WAMP_Caller caller : callers) {
            System.out.println("\n---------------------------------------\n" + caller.toString());
        }

        wamp.stop();
    }
}
