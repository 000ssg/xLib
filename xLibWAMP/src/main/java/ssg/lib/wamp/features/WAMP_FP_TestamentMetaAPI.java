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
package ssg.lib.wamp.features;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPFeatureProvider;
import ssg.lib.wamp.WAMPRealm;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.events.WAMPBroker;
import ssg.lib.wamp.flows.WAMPMessagesFlow;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.nodes.WAMPNode;
import ssg.lib.wamp.nodes.WAMPNode.WAMPNodeListener;
import ssg.lib.wamp.rpc.impl.dealer.DealerLocalProcedure;
import ssg.lib.wamp.rpc.impl.dealer.DealerProcedure;
import ssg.lib.wamp.rpc.impl.dealer.WAMPRPCDealer;
import ssg.lib.wamp.util.RB;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.util.WAMPTools;

/**
 *
 * @author 000ssg
 */
public class WAMP_FP_TestamentMetaAPI implements WAMPFeatureProvider, WAMPNodeListener {

    public static final String TM_RPC_ADD_TESTAMENT = "wamp.session.add_testament"; //  add a Testament which will be published on a particular topic when the Session is detached or destroyed
    public static final String TM_RPC_FLUSH_TESTAMENT = "wamp.session.flish_testament"; // remove the Testaments for that Session, either for when it is detached or destroyed.

    public static String TM_OPTION_PUBLISH_OPTIONS = "publish_options";
    public static String TM_OPTION_SCOPE = "scope";
    public static String TM_SCOPE_DETACHED = "detached";
    public static String TM_SCOPE_DESTROYED = "destroyed";

    static Map<WAMPSession, Collection<Object[]>[]> testaments = WAMPTools.createSynchronizedMap(true);

    @Override
    public WAMPFeature[] getFeatures(WAMP.Role role) {
        return new WAMPFeature[]{WAMPFeature.x_testament_meta_api};
    }

    @Override
    public DealerProcedure[] getFeatureProcedures(WAMP.Role role) {
        if (WAMP.Role.dealer.equals(role)) {
            return new DealerProcedure[]{
                rpcAddTestament,
                rpcFlushTestament
            };
        } else {
            return null;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////// session meta api RPCs
    ////////////////////////////////////////////////////////////////////////////
    /*
wamp.session.add_testament to add a Testament which will be published on a particular topic when the Session is detached or destroyed.
wamp.session.flush_testaments to remove the Testaments for that Session, either for when it is detached or destroyed.
     */
    // RPC implementations
    DealerLocalProcedure rpcAddTestament = new DealerLocalProcedure(TM_RPC_ADD_TESTAMENT) {

        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.hasLocalRole(WAMP.Role.dealer) && session.hasLocalRole(WAMP.Role.broker) && session.getRealm().getActor(WAMP.Role.dealer) instanceof WAMPRPCDealer) {
                Collection<Object[]>[] rs = testaments.get(session);
                if (rs == null) {
                    rs = new Collection[]{new ArrayList<>(), new ArrayList<>()};
                    testaments.put(session, rs);
                }
                // 0 - topic, 1 - list args, 2 - kwArgs, 3 - options (when publishing)
                Object[] r = new Object[4];
                List lst=msg.getList(3);
                r[0] = lst.get(0);
                r[1] = lst.get(1);
                r[2] = lst.get(2);
                Map opts = msg.getDict(1);
                String scope = null;
                if (opts != null && opts.containsKey(TM_OPTION_SCOPE)) {
                    scope = (String) opts.get(TM_OPTION_SCOPE);
                }
                if (opts != null && opts.containsKey(TM_OPTION_PUBLISH_OPTIONS)) {
                    r[3] = (Map) opts.get(TM_OPTION_PUBLISH_OPTIONS);
                } else {
                    opts = null;
                }
                if (scope != null && TM_SCOPE_DETACHED.equals(scope)) {
                    rs[1].add(r);
                    System.out.println("... added detached event: "+Arrays.asList(r).toString());
                } else {
                    rs[0].add(r);
                    System.out.println("... added destroyed event: "+Arrays.asList(r).toString());
                }
                session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT));
                return true;
            } else {
                session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT));
                return false;
            }
        }

        @Override
        public Map<String, Object> getReflectionMeta() {
            return RB.root()
                    .procedure(RB.procedure(TM_RPC_ADD_TESTAMENT, "Adds a new testament.")
                            .parameter(0, "topic", "uri", false, "The topic to publish the event on.")
                            .parameter(1, "args", "list", false, "Positional arguments for the event.")
                            .parameter(2, "kwargs", "dict", false, "Keyword arguments for the event."
                                    + "\n  "
                            )
                            .parameter(-1, "publish_options", "dict", true, "Options for the event when it is published -- see Publish."
                                    + "Options. Not all options may be honoured (for example, acknowledge). "
                                    + "By default, there are no options.")
                            .parameter(-2, "scope", "string", true, "When the testament should be published. Valid values "
                                    + "are detached (when the WAMP session is detached, for example, when using Event Retention) "
                                    + "or destroyed (when the WAMP session is finalized and destroyed on the Broker). Default MUST be destroyed.")
                    )
                    .data();
        }

        @Override
        public WAMPFeature[] featuredBy() {
            return new WAMPFeature[]{WAMPFeature.x_testament_meta_api};
        }
    };

    DealerLocalProcedure rpcFlushTestament = new DealerLocalProcedure(TM_RPC_FLUSH_TESTAMENT) {

        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.hasLocalRole(WAMP.Role.dealer) && session.hasLocalRole(WAMP.Role.broker) && session.getRealm().getActor(WAMP.Role.dealer) instanceof WAMPRPCDealer) {
                Map opts = msg.getDict(3);
                Collection<Object[]>[] rs = testaments.get(session);
                if (rs != null) {
                    String scope = null;
                    if (opts != null && opts.containsKey(TM_OPTION_SCOPE)) {
                        scope = (String) opts.get(TM_OPTION_SCOPE);
                    }
                    if (scope != null && TM_SCOPE_DETACHED.equals(scope)) {
                        rs[1].clear();
                    } else {
                        rs[0].clear();
                    }
                }
                session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT));
                return true;
            } else {
                session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT));
                return false;
            }
        }

        @Override
        public Map<String, Object> getReflectionMeta() {
            return RB.root()
                    .procedure(RB.procedure(TM_RPC_FLUSH_TESTAMENT, "Remove testaments for the given scope.")
                            .parameter(-1, "scope", "string", true, "Which set of testaments to be removed. "
                                    + "Valid values are the same as wamp.session.add_testament, and the default "
                                    + "MUST be destroyed.")
                    )
                    .data();
        }

        @Override
        public WAMPFeature[] featuredBy() {
            return new WAMPFeature[]{WAMPFeature.x_testament_meta_api};
        }
    };

    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////// messaging
    ////////////////////////////////////////////////////////////////////////////
    //
    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////// handled session events
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onClosedSession(WAMPSession session) {
        WAMPRealm r = session.getRealm();
        try {
            // send testaments
            WAMPBroker broker = session.getRealm().getActor(WAMP.Role.broker);
            if (broker != null) {
                synchronized (session) {

                    Collection<Object[]>[] rs = testaments.get(session);
                    String scope = TM_SCOPE_DESTROYED;
                    if (rs != null) {
                        for (Object[] oo : TM_SCOPE_DETACHED.equals(scope) ? rs[1] : rs[0]) {
                            broker.doEvent(null,
                                    0,
                                    (String) oo[0],
                                    session.getId(),
                                    oo[3] != null ? (Map) oo[3] : WAMPTools.EMPTY_DICT,
                                    (List) oo[1],
                                    (Map) oo[2]);
                        }
                    }
                }
            }
        } catch (WAMPException wex) {
            wex.printStackTrace();
        }
        synchronized (r) {
            testaments.remove(session);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////// unhandled events
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void prepareFeature(WAMPSession session) throws WAMPException {
    }

    @Override
    public void onEstablishedSession(WAMPSession session) {
    }

    @Override
    public void onCreatedRealm(WAMPNode node, WAMPRealm realm) {
    }

    @Override
    public void onHandled(WAMPSession session, WAMPMessage msg, WAMPMessagesFlow mf) {
    }

    @Override
    public void onFailed(WAMPSession session, WAMPMessage msg, WAMPMessagesFlow mf) {
    }

    @Override
    public void onFatal(WAMPSession session, WAMPMessage msg) {
    }

    @Override
    public void onSent(WAMPSession session, WAMPMessage msg, Throwable error) {
    }

}
