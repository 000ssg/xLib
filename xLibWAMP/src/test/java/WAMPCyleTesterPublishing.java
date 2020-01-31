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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.nodes.WAMPClient;
import ssg.lib.wamp.nodes.WAMPRouter;
import ssg.lib.wamp.events.WAMPEventListener.WAMPEventListenerBase;
import ssg.lib.wamp.util.WAMPTools;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author sesidoro
 */
public class WAMPCyleTesterPublishing {

    static boolean TRACE_MESSAGES = true;

    public static void main(String... args) throws Exception {
        WAMPRouter router = new WAMPRouter();
        //router.addWAMPNodeListener(new WAMPNodeListenerDebug("ROUTER: "));

        List<WAMPTransportList.WAMPTransportLoop> transports = WAMPTools.createSynchronizedList();
        List<WAMPClient> clients = WAMPTools.createSynchronizedList();

        List<WAMPClient> publishers = WAMPTools.createSynchronizedList();
        List<WAMPClient> subscribers = WAMPTools.createSynchronizedList();

        String[] realms = new String[]{"realm.1.com", "realm.2.com"};
        Role[][] roleGroups = new Role[][]{{Role.subscriber}, {Role.publisher}};
        for (int i = 0; i < 5; i++) {
            WAMPTransportList.WAMPTransportLoop transport = new WAMPTransportList.WAMPTransportLoop();
            transport.remote.TRACE_MESSAGES = "WR";
            transport.local.TRACE_MESSAGES = "WC";
            transports.add(transport);

            String realm = ((i < 3) ? realms[0] : realms[1]);
            Role[] roles = roleGroups[i % 2];

            final WAMPClient client = new WAMPClient().configure(transport.local, "test.agent." + i, realm, roles);
            //client.addWAMPNodeListener(new WAMPNodeListenerDebug("CLIENT#" + i + ": "));
            clients.add(client);
            router.onNewTransport(transport.remote);
            client.connect();
            if (client.hasRole(Role.publisher)) {
                publishers.add(client);
            }
            if (client.hasRole(Role.subscriber)) {
                subscribers.add(client);
                final int order = i;
                final String topicA = realm + "_topic_A";
                final String topicB = realm + "_topic_B";
                client.addWAMPEventListener(new WAMPEventListenerBase(
                        topicA,
                        WAMPTools.EMPTY_DICT,
                        (subscriptionId, publicationId, options, arguments, argumentsKw) -> {
                            System.out.println("CLIENT " + order + " onEvent: " + publicationId + "/" + subscriptionId + " -> " + arguments + ", " + argumentsKw);
                        }
                ));

                if (i % 2 == 0) {
                    client.addWAMPEventListener(new WAMPEventListenerBase(
                            topicB,
                            WAMPTools.EMPTY_DICT,
                            (subscriptionId, publicationId, options, arguments, argumentsKw) -> {
                                System.out.println("CLIENT " + order + " onEvent: " + publicationId + "/" + subscriptionId + " -> " + arguments + ", " + argumentsKw);
                            }
                    ));
                }
            }
        }

        for (int i = 0; i < 100; i++) {
            router.runCycle();
            for (WAMPClient client : clients) {
                client.runCycle();
            }

            if (i % 3 == 0 && i > 0) {
                for (WAMPClient client : publishers) {
                    if (client.canPublish()) {
                        client.publish(
                                WAMPTools.EMPTY_DICT,
                                client.getRealm() + "_topic_A",
                                Arrays.asList(new String[]{"AAA_" + i, "BBB_" + System.nanoTime()}),
                                null
                        );
                    }
                }
            }
            if (i % 7 == 0 && i > 0) {
                for (WAMPClient client : publishers) {
                    if (client.canPublish()) {
                        Map map = WAMPTools.createMap();
                        map.put("dfr", 1);
                        map.put("dfrr", i);
                        client.publish(
                                WAMPTools.EMPTY_DICT,
                                client.getRealm() + "_topic_B",
                                Arrays.asList(new String[]{"Aaa_" + i, "Bbb_" + System.nanoTime()}),
                                map
                        );
                    }
                }
            }
            if (i > 0 && i % 15 == 0 && !subscribers.isEmpty()) {
                try {
                    WAMPClient client = subscribers.get((i / 15) - 1);
                    System.out.println("... unsubscribe for " + client.getAgent() + (" [" + ((i / 15) - 1) + "]") + " from '" + client.getRealm() + "_topic_B" + "'");
                    client.removeWAMPEventListener(client.getRealm() + "_topic_B");
                } catch (Throwable th) {
                }
            }
            if (i > 0 && i % 25 == 0 && !subscribers.isEmpty()) {
                try {
                    WAMPClient client = subscribers.get((i / 25) - 1);
                    System.out.println("... unsubscribe for " + client.getAgent() + (" [" + ((i / 25) - 1) + "]") + " from '" + client.getRealm() + "_topic_A" + "'");
                    client.removeWAMPEventListener(client.getRealm() + "_topic_A");
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

        for (WAMPTransportList.WAMPTransportLoop loop : transports) {
            System.out.println("\n" + loop.getStat());
        }

    }
}
