/*
 * The MIT License
 *
 * Copyright 2021 000ssg.
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

import java.util.List;
import java.util.Map;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPFeatureProvider;
import ssg.lib.wamp.WAMPRealm;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.flows.WAMPMessagesFlow;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.nodes.WAMPNode;
import ssg.lib.wamp.nodes.WAMPNode.WAMPNodeListener;
import ssg.lib.wamp.rpc.impl.dealer.DealerLocalProcedure;
import ssg.lib.wamp.rpc.impl.dealer.DealerProcedure;
import ssg.lib.wamp.rpc.impl.dealer.WAMPRPCDealer;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.util.WAMPTools;
import ssg.lib.wamp.util.RB;
import ssg.lib.wamp.auth.WAMPAuth;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ID;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_METHOD;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_PROVIDER;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ROLE;
import ssg.lib.wamp.events.WAMPBroker;
import static ssg.lib.wamp.features.WAMP_FP_SessionMetaAPI.SM_EVENT_ON_JOIN;
import ssg.lib.wamp.util.WAMPNodeSessionManagement;

/**
 *
 * @author 000ssg
 */
public class WAMP_FP_VirtualSession implements WAMPFeatureProvider, WAMPNodeListener {

    public static final String FEATURE_virtual_session = "virtual_session";
    public static final WAMPFeature virtual_session = new WAMPFeature(
            FEATURE_virtual_session,
            WAMP.Role.dealer,
            WAMP.Role.caller);
    public static final String VS_REGISTER = "virtual_session.register";
    public static final String VS_UNREGISTER = "virtual_session.unregister";

    WAMPNodeSessionManagement sessionManager;

    //////////////////////////////////////////////////////// WAMPFeatureProvider
    @Override
    public WAMPFeature[] getFeatures(WAMP.Role role) {
        return WAMP.Role.hasRole(role, WAMP.Role.dealer, WAMP.Role.caller) ? new WAMPFeature[]{virtual_session} : null;
    }

    @Override
    public void prepareFeature(WAMPSession session) throws WAMPException {
    }

    @Override
    public DealerProcedure[] getFeatureProcedures(WAMP.Role role) {
        return new DealerProcedure[]{
            registerVirtualSession,
            unregisterVirtualSession
        };
    }

    /////////////////////////////////////////////////////////// WAMPNodeListener
    @Override
    public void onCreatedRealm(WAMPNode node, WAMPRealm realm) {
        if (sessionManager == null && node instanceof WAMPNodeSessionManagement) {
            sessionManager = (WAMPNodeSessionManagement) node;
        }
    }

    @Override
    public void onEstablishedSession(WAMPSession session) {
    }

    @Override
    public void onClosedSession(WAMPSession session) {
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

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    DealerLocalProcedure registerVirtualSession = new DealerLocalProcedure(VS_REGISTER) {
        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.supportsFeature(virtual_session) && session.hasLocalRole(WAMP.Role.dealer) && session.getRealm().getActor(WAMP.Role.dealer) instanceof WAMPRPCDealer) {
                Map<String, Object> authInfo = msg.getDict(4);
                WAMPAuth auth = new WAMPAuth(authInfo);
                Long sessId = sessionManager.nextSessionId();
                session.getVirtualAuths().put(sessId, auth);

                session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT, WAMPTools.createList(sessId)));

                WAMPBroker broker = session.getRealm().getActor(WAMP.Role.broker);
                if (broker != null) {
                    synchronized (session) {
                        Map<String, Object> argsKw = WAMPTools.createDict(map -> {
                            map.put("session", sessId);
                            map.put(K_AUTH_ID, auth.getAuthid());
                            map.put(K_AUTH_ROLE, auth.getRole());
                            map.put(K_AUTH_METHOD, auth.getMethod());
                            map.put(K_AUTH_PROVIDER, auth.getDetails().get(K_AUTH_PROVIDER));
                            if (auth.getDetails().containsKey("transport")) {
                                map.put("transport", auth.getDetails().get("transport"));
                            }
                        });
                        broker.doEvent(null,
                                0,
                                SM_EVENT_ON_JOIN,
                                session.getId(),
                                WAMPTools.EMPTY_DICT,
                                WAMPTools.EMPTY_LIST,
                                argsKw);
                    }
                }
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Map<String, Object> getReflectionMeta() {
            return RB.root()
                    .procedure(RB.function(VS_REGISTER)
                            .parameter(0, "auth", "dict", false)
                            .returns("id"))
                    .data();
        }

        @Override
        public WAMPFeature[] featuredBy() {
            return new WAMPFeature[]{virtual_session};
        }
    };

    DealerLocalProcedure unregisterVirtualSession = new DealerLocalProcedure(VS_UNREGISTER) {
        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.supportsFeature(virtual_session) && session.hasLocalRole(WAMP.Role.dealer) && session.getRealm().getActor(WAMP.Role.dealer) instanceof WAMPRPCDealer) {
                List ids = msg.getList(3);
                if (ids != null && !ids.isEmpty() && ids.get(0) instanceof Long) {
                    session.killVirtualAuths((Long) ids.get(0));
                }

                session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT, WAMPTools.EMPTY_LIST));
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Map<String, Object> getReflectionMeta() {
            return RB.root()
                    .procedure(RB.function(VS_UNREGISTER)
                            .parameter(0, "virtual session id", "id", false))
                    .data();
        }

        @Override
        public WAMPFeature[] featuredBy() {
            return new WAMPFeature[]{virtual_session};
        }
    };
}
