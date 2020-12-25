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
package ssg.lib.wamp.features;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMPConstantsBase;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPFeatureProvider;
import ssg.lib.wamp.WAMPRealm;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.WAMPSessionState;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ID;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_METHOD;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_PROVIDER;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ROLE;
import ssg.lib.wamp.events.WAMPBroker;
import ssg.lib.wamp.flows.WAMPMessagesFlow;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.messages.WAMP_DT;
import ssg.lib.wamp.nodes.WAMPNode.WAMPNodeListener;
import ssg.lib.wamp.rpc.impl.dealer.DealerLocalProcedure;
import ssg.lib.wamp.rpc.impl.dealer.DealerProcedure;
import ssg.lib.wamp.rpc.impl.dealer.WAMPRPCDealer;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.util.WAMPTools;

/**
 *
 * @author sesidoro
 */
public class WAMP_FP_SessionMetaAPI implements WAMPFeatureProvider, WAMPNodeListener {

    public static final String SM_RPC_SESSION_COUNT = "wamp.session.count"; // Obtains the number of sessions currently attached to the realm.
    public static final String SM_RPC_SESSION_LIST = "wamp.session.list"; // Retrieves a list of the session IDs for all sessions currently attached to the realm.
    public static final String SM_RPC_SESSION_GET = "wamp.session.get"; // Retrieves information on a specific session.
    public static final String SM_RPC_SESSION_KILL = "wamp.session.kill"; // Kill a single session identified by session ID.
    public static final String SM_RPC_SESSION_KILL_BY_AUTHID = "wamp.session.kill_by_authid"; // Kill all currently connected sessions that have the specified authid.
    public static final String SM_RPC_SESSION_KILL_BY_AUTHROLE = "wamp.session.kill_by_authrole"; // Kill all currently connected sessions that have the specified authrole.
    public static final String SM_RPC_SESSION_KILL_ALL = "wamp.session.kill_all"; // Kill all currently connected sessions in the caller's realm.

    public static final String SM_EVENT_ON_JOIN = "wamp.session.on_join";
    public static final String SM_EVENT_ON_LEAVE = "wamp.session.on_leave";

    public static final String SM_ERROR_NO_SESSION = "wamp.error.no_such_session";

    static Map<WAMPRealm, Collection<WAMPSession>> sessions = WAMPTools.createSynchronizedMap(true);

    @Override
    public WAMPFeature[] getFeatures(WAMP.Role role) {
        return new WAMPFeature[]{WAMPFeature.x_session_meta_api};
    }

    @Override
    public DealerProcedure[] getFeatureProcedures(WAMP.Role role) {
        if (WAMP.Role.dealer.equals(role)) {
            return new DealerProcedure[]{
                rpcSessionCount,
                rpcSessionList,
                rpcSessionGet,
                rpcSessionKill,
                rpcSessionKillByAuthId,
                rpcSessionKillByAuthRole
            };
        } else {
            return null;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////// session meta api RPCs
    ////////////////////////////////////////////////////////////////////////////
    /*
wamp.session.count: Obtains the number of sessions currently attached to the realm.
wamp.session.list: Retrieves a list of the session IDs for all sessions currently attached to the realm.
wamp.session.get: Retrieves information on a specific session.
wamp.session.kill: Kill a single session identified by session ID.
wamp.session.kill_by_authid: Kill all currently connected sessions that have the specified authid.
wamp.session.kill_by_authrole: Kill all currently connected sessions that have the specified authrole.
wamp.session.kill_all: Kill all currently connected sessions in the caller's realm.
     */
    // RPC implementations
    DealerLocalProcedure rpcSessionCount = new DealerLocalProcedure(SM_RPC_SESSION_COUNT) {

        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.hasLocalRole(WAMP.Role.dealer) && session.getRealm().getActor(WAMP.Role.dealer) instanceof WAMPRPCDealer) {
                long count = 0;
                WAMPRealm r = session.getRealm();
                Collection<WAMPSession> rs = sessions.get(r);
                if (rs != null) {
                    List<String> authroles = msg.getDataLength() > 3 ? msg.getList(3) : null;
                    if (authroles == null || authroles.isEmpty()) {
                        count = rs.size();
                    } else {
                        for (WAMPSession ws : rs) {
                            if (ws != null && ws.getAuth() != null && authroles.contains(ws.getAuth().getRole())) {
                                count++;
                            }
                        }
                    }
                }
                session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT, WAMPTools.createList(count)));
                return true;
            } else {
                return false;
            }
        }
    };

    DealerLocalProcedure rpcSessionList = new DealerLocalProcedure(SM_RPC_SESSION_LIST) {

        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.hasLocalRole(WAMP.Role.dealer) && session.getRealm().getActor(WAMP.Role.dealer) instanceof WAMPRPCDealer) {
                List<Long> ids = new ArrayList<>();
                WAMPRealm r = session.getRealm();
                Collection<WAMPSession> rs = sessions.get(r);
                if (rs != null) {
                    List<String> authroles = msg.getDataLength()>3? msg.getList(3):null;
                    if (authroles == null || authroles.isEmpty()) {
                        for (WAMPSession ws : rs) {
                            ids.add(ws.getId());
                        }
                    } else {
                        for (WAMPSession ws : rs) {
                            if (ws != null && ws.getAuth() != null && authroles.contains(ws.getAuth().getRole())) {
                                ids.add(ws.getId());
                            }
                        }
                    }
                }
                session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT, ids));
                return true;
            } else {
                return false;
            }
        }
    };

    DealerLocalProcedure rpcSessionGet = new DealerLocalProcedure(SM_RPC_SESSION_GET) {

        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.hasLocalRole(WAMP.Role.dealer)) {
                WAMPRealm r = session.getRealm();
                Collection<WAMPSession> rs = sessions.get(r);
                if (rs != null) {
                    Long sessId = msg.getDataLength()>3 ? (Long) msg.getList(3).get(0) : null;
                    WAMPSession sess = null;
                    for (WAMPSession ws : rs) {
                        if (ws.getId() == sessId) {
                            sess = ws;
                            break;
                        }
                    }
                    if (sess != null) {
                        Map<String, Object> dict = WAMPTools.createDict("session", sessId);
                        if (sess.getAuth() != null) {
                            dict.put(K_AUTH_ID, sess.getAuth().getAuthid());
                            dict.put(K_AUTH_ROLE, sess.getAuth().getRole());
                            dict.put(K_AUTH_METHOD, sess.getAuth().getMethod());
                            dict.put(K_AUTH_PROVIDER, sess.getAuth().getDetails().get(K_AUTH_PROVIDER));
                            if (sess.getAuth().getDetails().containsKey("transport")) {
                                dict.put("transport", sess.getAuth().getDetails().get("transport"));
                            }
                            session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT, null, dict));
                        }
                    } else {
                        session.send(WAMPMessage.error(msg.getType().getId(), msg.getId(0), WAMPTools.EMPTY_DICT, SM_ERROR_NO_SESSION));
                    }
                }
                return true;
            } else {
                return false;
            }
        }
    };

    DealerLocalProcedure rpcSessionKill = new DealerLocalProcedure(SM_RPC_SESSION_KILL) {

        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.hasLocalRole(WAMP.Role.dealer)) {
                WAMPRealm r = session.getRealm();
                Collection<WAMPSession> rs = sessions.get(r);
                if (rs != null) {
                    Long sessId = msg.getDataLength()>3 ? (Long) msg.getList(3).get(0) : null;
                    WAMPSession sess = null;
                    if (sessId != session.getId()) {
                        for (WAMPSession ws : rs) {
                            if (ws.getId() == sessId) {
                                sess = ws;
                                break;
                            }
                        }
                    }

                    int count = kill(session, msg, sess);
                    if (count == -1) {
                        // parameter(s) error
                    } else if (count == 1) {
                        // empty response on successful operation
                        session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT));
                    } else {
                        session.send(WAMPMessage.error(msg.getType().getId(), msg.getId(0), WAMPTools.EMPTY_DICT, SM_ERROR_NO_SESSION));
                    }
                }
                return true;
            } else {
                return false;
            }
        }
    };

    DealerLocalProcedure rpcSessionKillByAuthId = new DealerLocalProcedure(SM_RPC_SESSION_KILL_BY_AUTHID) {

        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.hasLocalRole(WAMP.Role.dealer) && session.getRealm().getActor(WAMP.Role.dealer) instanceof WAMPRPCDealer) {
                List<WAMPSession> sesss = new ArrayList<>();
                WAMPRealm r = session.getRealm();
                Collection<WAMPSession> rs = sessions.get(r);
                if (rs != null) {
                    String authid = msg.getDataLength()>3 ? (String) msg.getList(3).get(0) : null;
                    for (WAMPSession ws : rs) {
                        if (ws != null && ws.getAuth() != null && authid.equals(ws.getAuth().getAuthid())) {
                            sesss.add(ws);
                        }
                    }
                }
                int count = kill(session, msg, sesss.toArray(new WAMPSession[sesss.size()]));
                if (count == -1) {
                    // parameter(s) error
                } else {
                    // response on successful operation
                    session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT, WAMPTools.createList(count)));
                }
                return true;
            } else {
                return false;
            }
        }
    };

    DealerLocalProcedure rpcSessionKillByAuthRole = new DealerLocalProcedure(SM_RPC_SESSION_KILL_BY_AUTHROLE) {

        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.hasLocalRole(WAMP.Role.dealer) && session.getRealm().getActor(WAMP.Role.dealer) instanceof WAMPRPCDealer) {
                List<WAMPSession> sesss = new ArrayList<>();
                WAMPRealm r = session.getRealm();
                Collection<WAMPSession> rs = sessions.get(r);
                if (rs != null) {
                    List<String> authroles = msg.getDataLength()>3 ? msg.getList(3) : null;
                    for (WAMPSession ws : rs) {
                        if (ws != null && ws.getAuth() != null && authroles.contains(ws.getAuth().getRole())) {
                            sesss.add(ws);
                        }
                    }
                }
                int count = kill(session, msg, sesss.toArray(new WAMPSession[sesss.size()]));
                if (count == -1) {
                    // parameter(s) error
                } else {
                    // response on successful operation
                    session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT, WAMPTools.createList(count)));
                }
                return true;
            } else {
                return false;
            }
        }
    };

    int kill(WAMPSession session, WAMPMessage msg, WAMPSession... targetSessions) throws WAMPException {
        int count = 0;
        Map<String, Object> details = msg.getDataLength()>4 ? msg.getDict(4):null;
        String reason = "wamp.close.normal";
        String message = null;
        if (details != null) {
            String reason2 = (String) details.get("reason");
            if (reason2 != null) {
                if (!WAMP_DT.validate(WAMP_DT.uri, reason2)) {
                    reason = null;
                    session.send(WAMPMessage.error(msg.getType().getId(), msg.getId(0), WAMPTools.EMPTY_DICT, WAMPConstantsBase.ERROR_InvalidURI));
                    return -1;
                } else {
                    reason = reason2;
                    message = (String) details.get("message");
                }
            }
        }
        for (WAMPSession sess : targetSessions) {
            if (sess != null && sess.getState() == WAMPSessionState.established && session.getId() != sess.getId()) {
                // close the session in arg
                sess.send(WAMPMessage.goodbye((message != null) ? WAMPTools.createDict("message", message) : WAMPTools.EMPTY_DICT, reason));
                count++;
            }
        }
        return count;
    }

    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////// messaging
    ////////////////////////////////////////////////////////////////////////////
    //
    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////// handled session events
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onEstablishedSession(WAMPSession session) {
        WAMPRealm r = session.getRealm();
        synchronized (r) {
            Collection<WAMPSession> rs = sessions.get(r);
            if (rs == null) {
                rs = WAMPTools.createSynchronizedSet(true, null);
                sessions.put(r, rs);
            }
            rs.add(session);
        }
        // send wamp.session.on_join
        try {
            WAMPBroker broker = session.getRealm().getActor(WAMP.Role.broker);
            if (broker != null) {
                synchronized (session) {
                    /*
session|id - The session ID of the session that joined
authid|string - The authentication ID of the session that joined
authrole|string - The authentication role of the session that joined
authmethod|string - The authentication method that was used for authentication the session that joined
authprovider|string- The provider that performed the authentication of the session that joined
transport|dict - Optional, implementation defined information about the WAMP transport the joined session is running over.
                     */
                    Map<String, Object> details = WAMPTools.createDict("session", session.getId());
                    if (session.getAuth() != null) {
                        details.put(K_AUTH_ID, session.getAuth().getAuthid());
                        details.put(K_AUTH_ROLE, session.getAuth().getRole());
                        details.put(K_AUTH_METHOD, session.getAuth().getMethod());
                        details.put(K_AUTH_PROVIDER, session.getAuth().getDetails().get(K_AUTH_PROVIDER));
                        if (session.getAuth().getDetails().containsKey("transport")) {
                            details.put("transport", session.getAuth().getDetails().get("transport"));
                        }
                    }

                    broker.doEvent(null,
                            0,
                            SM_EVENT_ON_JOIN,
                            session.getId(),
                            WAMPTools.EMPTY_DICT,
                            WAMPTools.EMPTY_LIST,
                            details);
                }
            }
        } catch (WAMPException wex) {
            wex.printStackTrace();
        }
    }

    @Override
    public void onClosedSession(WAMPSession session) {
        WAMPRealm r = session.getRealm();
        synchronized (r) {
            Collection<WAMPSession> rs = sessions.get(r);
            if (rs != null) {
                rs.remove(session);
            }
        }
        try {
            // send wamp.session.on_leave
            WAMPBroker broker = session.getRealm().getActor(WAMP.Role.broker);
            if (broker != null) {
                synchronized (session) {
                    broker.doEvent(null,
                            0,
                            SM_EVENT_ON_LEAVE,
                            session.getId(),
                            WAMPTools.EMPTY_DICT,
                            WAMPTools.createList(
                                    session.getId(),
                                    session.getAuth().getAuthid(),
                                    session.getAuth().getRole()
                            ),
                            null);
                }
            }
        } catch (WAMPException wex) {
            wex.printStackTrace();
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////// unhandled events
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onCreatedRealm(WAMPRealm realm) {
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