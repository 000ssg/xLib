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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.nodes.WAMPClient;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.nodes.WAMPRouter;
import ssg.lib.wamp.rpc.impl.callee.CalleeProcedure.Callee;
import ssg.lib.wamp.rpc.impl.WAMPRPCListener.WAMPRPCListenerBase;
import ssg.lib.wamp.WAMPConstantsBase;
import ssg.lib.wamp.nodes.WAMPNode.WAMPNodeListenerDebug;
import static ssg.lib.wamp.rpc.impl.dealer.WAMPRPCRegistrations.RPC_COUNT_CALLEES;
import static ssg.lib.wamp.rpc.impl.dealer.WAMPRPCRegistrations.RPC_GET;
import static ssg.lib.wamp.rpc.impl.dealer.WAMPRPCRegistrations.RPC_LIST;
import static ssg.lib.wamp.rpc.impl.dealer.WAMPRPCRegistrations.RPC_LIST_CALLEES;
import static ssg.lib.wamp.rpc.impl.dealer.WAMPRPCRegistrations.RPC_LOOKUP;
import static ssg.lib.wamp.rpc.impl.dealer.WAMPRPCRegistrations.RPC_MATCH;
import ssg.lib.wamp.util.WAMPTools;

/**
 *
 * @author 000ssg
 */
public class WAMPCyleTesterRPC {

    public static void main(String... args) throws Exception {
        WAMPRouter router = new WAMPRouter(Role.dealer, Role.broker);
        router.addWAMPNodeListener(new WAMPNodeListenerDebug("ROUTER: "));

        List<WAMPTransportList.WAMPTransportLoop> transports = WAMPTools.createSynchronizedList();
        List<WAMPClient> clients = WAMPTools.createSynchronizedList();

        List<WAMPClient> callees = WAMPTools.createSynchronizedList();
        List<WAMPClient> callers = WAMPTools.createSynchronizedList();

        String[] realms = new String[]{"realm.1.com", "realm.2.com"};
        Role[][] roleGroups = new Role[][]{{Role.caller}, {Role.callee}};
        for (int i = 0; i < 5; i++) {
            WAMPTransportList.WAMPTransportLoop transport = new WAMPTransportList.WAMPTransportLoop();
            transports.add(transport);

            String realm = ((i < 3) ? realms[0] : realms[1]);
            Role[] roles = roleGroups[i % 2];

            final WAMPClient client = new WAMPClient().configure(transport.local, "test.agent." + i, realm, (i > 0) ? roles : new Role[]{Role.publisher});
            //client.addWAMPNodeListener(new WAMPNodeListenerDebug("CLIENT#" + i + ": "));
            clients.add(client);
            client.connect();
            router.onNewTransport(transport.remote);
            if (client.hasRole(Role.caller)) {
                callers.add(client);
            }
            if (client.hasRole(Role.callee)) {
                callees.add(client);
                final int order = i;
                final String procA = "proc_A";
                final String procB = "proc_B";
                if (client.hasRole(Role.callee)) {
                    client.addExecutor(WAMPTools.EMPTY_DICT, procA, new Callee() {
                        @Override
                        public Future invoke(ExecutorService executor, final String name, final List args, final Map argsKw) throws WAMPException {
                            return executor.submit(new Callable() {
                                @Override
                                public Object call() throws Exception {
                                    String old = Thread.currentThread().getName();
                                    try {
                                        Thread.currentThread().setName("exec_" + procA);
                                        if (System.currentTimeMillis() % 2 == 0) {
                                            throw new WAMPException("Event time is not allowed for execution");
                                        }
                                        return new Object[]{
                                            procA,
                                            System.currentTimeMillis(),
                                            args,
                                            argsKw
                                        };
                                    } finally {
                                        Thread.currentThread().setName(old);
                                    }
                                }
                            });
                        }
                    });
                }

                if (1 == 1 || i % 2 == 0) {
                    if (client.hasRole(Role.callee)) {
                        client.addExecutor(WAMPTools.EMPTY_DICT, procB, new Callee() {
                            @Override
                            public Future invoke(ExecutorService executor, final String name, final List args, final Map argsKw) throws WAMPException {
                                return executor.submit(new Callable() {
                                    @Override
                                    public Object call() throws Exception {
                                        String old = Thread.currentThread().getName();
                                        try {
                                            Thread.currentThread().setName("exec_" + procA);
                                            if (System.currentTimeMillis() % 8 == 0) {
                                                throw new WAMPException("Not every time is allowed for execution");
                                            }
                                            long started = System.nanoTime();
                                            try {
                                                Thread.sleep((long) (20 * Math.random() + 10));
                                            } catch (Throwable th) {
                                            }
                                            return new Object[]{
                                                procB,
                                                System.currentTimeMillis(),
                                                (System.nanoTime() - started) / 1000000f,
                                                args,
                                                argsKw
                                            };
                                        } finally {
                                            Thread.currentThread().setName(old);
                                        }
                                    }
                                });
                            }
                        });
                    }
                }
            }
        }

        try {
            // do registrations if any
            for (int i = 0; i < 5; i++) {
                router.runCycle();
                for (WAMPClient client : clients) {
                    client.runCycle();
                }
            }

            for (int i = 0; i < 100; i++) {
                router.runCycle();
                for (WAMPClient client : clients) {
                    client.runCycle();
                }

                final String realm = ((i < 3) ? realms[0] : realms[1]);
                final int order = i;
                final String procA = "proc_A";
                final String procB = "proc_B";

                if (i % 3 == 0 && i > 0) {
                    for (WAMPClient client : callers) {
                        client.addWAMPRPCListener(new WAMPRPCListenerBase(
                                WAMPTools.EMPTY_DICT,
                                procA,
                                Collections.singletonList("Calling proc A at " + i)
                        ) {
                            @Override
                            public void onCancel(long callId, String reason) {
                                System.out.println("CALL[" + callId + "/" + getProcedure() + "].cancel: " + reason);
                            }

                            @Override
                            public boolean onResult(long callId, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                                System.out.println("CALL[" + callId + "/" + getProcedure() + "].result: "
                                        + ((!details.isEmpty()) ? "\n || details: " + details.replace("\n", "\n || ") : "")
                                        + ((args != null) ? "\n || args   : " + args.toString().replace("\n", "\n || ") : "")
                                        + ((argsKw != null) ? "\n || argsKw : " + argsKw.toString().replace("\n", "\n || ") : "")
                                );
                                return true;
                            }

                            @Override
                            public void onError(long callId, String error, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                                System.out.println("CALL[" + callId + "/" + getProcedure() + "].error: " + error
                                        + ((!details.isEmpty()) ? "\n ** details: " + details.replace("\n", "\n ** ") : "")
                                        + ((args != null) ? "\n ** args   : " + args.toString().replace("\n", "\n ** ") : "")
                                        + ((argsKw != null) ? "\n ** argsKw : " + argsKw.toString().replace("\n", "\n ** ") : "")
                                );
                            }
                        }
                        );
                    }
                }
                if (i % 7 == 0 && i > 0) {
                    for (WAMPClient client : callers) {
                        client.addWAMPRPCListener(new WAMPRPCListenerBase(
                                WAMPTools.EMPTY_DICT,
                                procB,
                                Collections.singletonList("Calling proc B at " + i)
                        ) {
                            @Override
                            public void onCancel(long callId, String reason) {
                                System.out.println("CALL[" + callId + "/" + getProcedure() + "].cancel: " + reason);
                            }

                            @Override
                            public boolean onResult(long callId, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                                System.out.println("CALL[" + callId + "/" + getProcedure() + "].result: "
                                        + ((!details.isEmpty()) ? "\n || details: " + details.replace("\n", "\n || ") : "")
                                        + ((args != null) ? "\n || args   : " + args.toString().replace("\n", "\n || ") : "")
                                        + ((argsKw != null) ? "\n || argsKw : " + argsKw.toString().replace("\n", "\n || ") : "")
                                );
                                return true;
                            }

                            @Override
                            public void onError(long callId, String error, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                                System.out.println("CALL[" + callId + "/" + getProcedure() + "].error: " + error
                                        + ((!details.isEmpty()) ? "\n ** details: " + details.replace("\n", "\n ** ") : "")
                                        + ((args != null) ? "\n ** args   : " + args.toString().replace("\n", "\n ** ") : "")
                                        + ((argsKw != null) ? "\n ** argsKw : " + argsKw.toString().replace("\n", "\n ** ") : "")
                                );
                            }
                        }
                        );
                    }
                }

                if (i > 0 && i % 17 == 0) {
                    for (WAMPClient client : callers) {
                        String[] pns = new String[]{RPC_LIST,
                            RPC_LOOKUP,
                            RPC_MATCH,
                            RPC_GET,
                            RPC_LIST_CALLEES,
                            RPC_COUNT_CALLEES
                        };
                        final String pn = pns[0];

                        client.addWAMPRPCListener(new WAMPRPCListenerBase(
                                WAMPTools.EMPTY_DICT,
                                pn,
                                null //Collections.singletonList("Calling '"+pn+"' at " + i)
                        ) {
                            @Override
                            public void onCancel(long callId, String reason) {
                                System.out.println("CALL[" + callId + "/" + getProcedure() + "].cancel: " + reason);
                            }

                            @Override
                            public boolean onResult(long callId, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                                System.out.println("CALL[" + callId + "/" + getProcedure() + "].result: "
                                        + ((!details.isEmpty()) ? "\n || details: " + details.replace("\n", "\n || ") : "")
                                        + ((args != null) ? "\n || args   : " + args.toString().replace("\n", "\n || ") : "")
                                        + ((argsKw != null) ? "\n || argsKw : " + argsKw.toString().replace("\n", "\n || ") : "")
                                );
                                return true;
                            }

                            @Override
                            public void onError(long callId, String error, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                                System.out.println("CALL[" + callId + "/" + getProcedure() + "].error: " + error
                                        + ((!details.isEmpty()) ? "\n ** details: " + details.replace("\n", "\n ** ") : "")
                                        + ((args != null) ? "\n ** args   : " + args.toString().replace("\n", "\n ** ") : "")
                                        + ((argsKw != null) ? "\n ** argsKw : " + argsKw.toString().replace("\n", "\n ** ") : "")
                                );
                            }
                        }
                        );
                    }
                }

                if (i > 0 && i % 15 == 0 && !callers.isEmpty()) {
                    try {
                        WAMPClient client = callees.get((i / 15) - 1);
                        System.out.println("... unregister for " + client.getAgent() + (" [" + ((i / 15) - 1) + "]") + " proc '" + procB + "'");
                        client.removeExecutor(procB);
                    } catch (Throwable th) {
                    }
                }
                if (i > 0 && i % 25 == 0 && !callees.isEmpty()) {
                    try {
                        WAMPClient client = callees.get((i / 25) - 1);
                        System.out.println("... unregister for " + client.getAgent() + (" [" + ((i / 25) - 1) + "]") + " from '" + procA + "'");
                        client.removeExecutor(procA);
                    } catch (Throwable th) {
                    }
                }

                if (1 == 0) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(i);
                    sb.append(":\n  router: ");
                    sb.append(router.toString().replace("\n", "\n  "));
                    for (WAMPClient client : clients) {
                        sb.append("\n  client: ");
                        sb.append(client.toString().replace("\n", "\n  "));
                    }
                    System.out.println(sb);
                }
            }

            System.out.println("\n========================================================= finalize interactions");
            // finalize interactioons if any
            for (int i = 0; i < 5; i++) {
                router.runCycle();
                for (WAMPClient client : clients) {
                    client.runCycle();
                }
                Thread.sleep(500);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
            System.out.println("\n========================================================= disconnect clients");

            for (WAMPClient client : clients) {
                System.out.println("CLIENT: " + client.getAgent());
                client.disconnect(WAMPConstantsBase.SystemShutdown);
                for (int i = 0; i < 3; i++) {
                    try {
                        router.runCycle();
                        client.runCycle();
                    } catch (Throwable th) {
                        break;
                    }
                }
            }

            System.out.println("\n========================================================= shutdown");
        }
        int a = 0;

    }
}
