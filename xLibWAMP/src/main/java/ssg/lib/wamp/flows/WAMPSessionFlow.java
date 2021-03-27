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
package ssg.lib.wamp.flows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.WAMPSession.WAMPParty;
import ssg.lib.wamp.WAMPSessionState;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.messages.WAMPMessageType;
import static ssg.lib.wamp.messages.WAMPMessageType.ABORT;
import static ssg.lib.wamp.messages.WAMPMessageType.ERROR;
import static ssg.lib.wamp.messages.WAMPMessageType.T_ABORT;
import static ssg.lib.wamp.messages.WAMPMessageType.T_GOODBYE;
import static ssg.lib.wamp.messages.WAMPMessageType.T_HELLO;
import static ssg.lib.wamp.messages.WAMPMessageType.T_WELCOME;
import static ssg.lib.wamp.messages.WAMPMessageType.WELCOME;
import ssg.lib.wamp.WAMPConstantsBase;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.auth.WAMPAuth;
import ssg.lib.wamp.auth.WAMPAuthProvider;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ID;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_METHOD;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_METHODS;
import static ssg.lib.wamp.messages.WAMPMessageType.T_AUTHENTICATE;
import static ssg.lib.wamp.messages.WAMPMessageType.T_CHALLENGE;
import static ssg.lib.wamp.messages.WAMPMessageType.T_ERROR;
import static ssg.lib.wamp.messages.WAMPMessageType.T_INVOCATION;
import static ssg.lib.wamp.messages.WAMPMessageType.T_PUBLISHED;
import static ssg.lib.wamp.messages.WAMPMessageType.T_REGISTERED;
import static ssg.lib.wamp.messages.WAMPMessageType.T_RESULT;
import static ssg.lib.wamp.messages.WAMPMessageType.T_SUBSCRIBED;
import static ssg.lib.wamp.messages.WAMPMessageType.T_UNREGISTERED;
import static ssg.lib.wamp.messages.WAMPMessageType.T_UNSUBSCRIBED;
import ssg.lib.wamp.util.WAMPTools;

/**
 *
 * @author 000ssg
 */
public class WAMPSessionFlow implements WAMPMessagesFlow {

    // per-role incoming message types sorted by ID!
    int[] typesRouter = new int[]{T_HELLO, T_ABORT, T_AUTHENTICATE, T_GOODBYE};
    int[] typesClient = new int[]{T_WELCOME, T_ABORT, T_CHALLENGE, T_GOODBYE};

    Map<String, WAMPAuthProvider> authProviders = new HashMap<>();

    public void configure(WAMPAuthProvider... waps) {
        if (waps != null) {
            for (WAMPAuthProvider wap : waps) {
                if (wap == null) {
                    continue;
                }
                if (authProviders.containsKey(wap.name())) {
                    continue;
                }
                authProviders.put(wap.name(), wap);
            }
        }
    }

    @Override
    public boolean canHandle(WAMPSession session, WAMPMessage msg) {
        int type = msg.getType().getId();
        if (session.hasLocalRole(WAMP.Role.router)) {
            return Arrays.binarySearch(typesRouter, type) >= 0;
        } else if (session.hasLocalRole(WAMP.Role.client)) {
            return Arrays.binarySearch(typesClient, type) >= 0;
        } else {
            try {
                checkAbortCondition(session, msg);
            } catch (WAMPException wex) {
                wex.printStackTrace();
            }
            return false;
        }
    }

    @Override
    public WAMPFlowStatus handle(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null) {
            return WAMPFlowStatus.failed;
        }
        WAMPMessageType type = msg.getType();
        boolean isRouter = session.hasLocalRole(WAMP.Role.router);

        // process session flow message if no id or if open on router side...
        if (session.getId() == 0 || WAMPSessionState.open == session.getState() && session.hasLocalRole(Role.router)) {
            // expect WELCOME or ABORT or ERROR?
            checkAbortCondition(session, msg);
            switch (type.getId()) {
                case T_HELLO:
                    if (isRouter) {
                        List<String> authMethods = null;
                        String authid = null;

                        // process HELLO
                        {
                            Map<String, Object> details = msg.getDict(1);
                            String agent = null;
                            if (details.containsKey("agent")) {
                                agent = (String) details.get("agent");
                            }
                            Map<String, Map> remoteRoles = (Map<String, Map>) details.get("roles");
                            session.setRemote(new WAMPParty(agent, remoteRoles));

                            // fetch auth options
                            authMethods = (List<String>) details.get(K_AUTH_METHODS);
                            authid = (String) details.get(K_AUTH_ID);
                        }

                        // check if client roles match router roles
                        boolean rolesOk = false;

                        for (Role role : session.getRemote().getRoles().keySet()) {
                            switch (role) {
                                case subscriber:
                                case publisher:
                                    if (session.hasLocalRole(Role.broker)) {
                                        rolesOk = true;
                                    }
                                    break;
                                case callee:
                                case caller:
                                    if (session.hasLocalRole(Role.dealer)) {
                                        rolesOk = true;
                                    }
                                    break;
                            }
                            if (rolesOk) {
                                break;
                            }
                        }

                        // respond
                        if (rolesOk) {
                            if (session.getLocal().isRouter() && authMethods != null && !authMethods.isEmpty()) {
                                for (String am : authMethods) {
                                    WAMPAuthProvider ap = authProviders.get(am);
                                    if (ap != null) {
                                        WAMPMessage challenge = ap.challenge(session, msg);
                                        if (challenge != null) {
                                            session.send(challenge);
                                            return WAMPFlowStatus.handled;
                                        }
                                    }
                                }
                                // TODO: no auth method supported ERROR...
                                session.send(WAMPMessage.abort(WAMPTools.EMPTY_DICT, WAMPConstantsBase.ERROR_ProtocolViolation));
                                return WAMPFlowStatus.failed;
                            }
                            // prepare and send WELCOME
                            try {
                                session.getRealm().verifySession(session, null);
                                Map<String, Object> details = prepareWelcomeDetails(session);
                                session.send(WAMPMessage.welcome(session.getId(), details));
                                session.setState(WAMPSessionState.established);
                            } catch (WAMPException wex) {
                                wex.printStackTrace();
                                session.send(WAMPMessage.abort(WAMPTools.EMPTY_DICT, WAMPConstantsBase.ERROR_AuthorizationFailed));
                                return WAMPFlowStatus.failed;
                            }
                        } else {
                            session.send(WAMPMessage.abort(WAMPTools.EMPTY_DICT, WAMPConstantsBase.ERROR_NoSuchRole));
                            if (session.getCloseReason() == null) {
                                session.setCloseReason(WAMPConstantsBase.ERROR_NoSuchRole);
                            }
                            session.setState(WAMPSessionState.closed);
                        }
                        return WAMPFlowStatus.handled;
                    } else {
                        session.send(WAMPMessage.abort(WAMPTools.EMPTY_DICT, WAMPConstantsBase.ERROR_ProtocolViolation));
                        return WAMPFlowStatus.failed;
                    }
                case T_AUTHENTICATE:
                    if (isRouter) {
                        String authMethod = null;
                        for (Entry<String, WAMPAuthProvider> entry : authProviders.entrySet()) {
                            if (entry.getValue().isChallenged(session)) {
                                authMethod = entry.getKey();
                                break;
                            }
                        }
                        WAMPAuthProvider ap = authProviders.get(authMethod);
                        if (ap != null) {
                            Map<String, Object> authInfo = ap.authenticated(session, msg);
                            if (authInfo != null) {
                                try {
                                    WAMPAuth auth = new WAMPAuth(authInfo);
                                    session.getRealm().verifySession(session, auth);
                                    // prepare and send WELCOME
                                    Map<String, Object> details = prepareWelcomeDetails(session);
                                    details.putAll(authInfo);
                                    session.send(WAMPMessage.welcome(session.getId(), details));
                                    // set WAMPAuth on router
                                    session.setAuth(auth);
                                    session.setState(WAMPSessionState.established);
                                    return WAMPFlowStatus.handled;
                                } catch (WAMPException wex) {
                                    wex.printStackTrace();
                                    session.send(WAMPMessage.abort(WAMPTools.EMPTY_DICT, WAMPConstantsBase.ERROR_AuthorizationFailed));
                                    return WAMPFlowStatus.failed;
                                }
                            } else {
                                try {
                                    List<String> authMethods = (List<String>) session.helloMessage().getDict(1).get(K_AUTH_METHODS);
                                    for (String s : authMethods) {
                                        if (authMethod == null) {
                                            authMethod = s;
                                            ap = authProviders.get(authMethod);
                                            if (ap != null) {
                                                WAMPMessage challenge = ap.challenge(session, session.helloMessage());
                                                if (challenge != null) {
                                                    session.send(challenge);
                                                    return WAMPFlowStatus.handled;
                                                }
                                            }
                                            authMethod = null;
                                        }
                                        if (s.equals(authMethod)) {
                                            authMethod = null;
                                        }
                                    }
                                } catch (Throwable th) {
                                    th.printStackTrace();
                                }
                            }
                        }
                        // no auth or not authenticated...
                        session.send(WAMPMessage.abort(WAMPTools.EMPTY_DICT, WAMPConstantsBase.ERROR_ProtocolViolation));
                        return WAMPFlowStatus.failed;
                    } else {
                        session.send(WAMPMessage.abort(WAMPTools.EMPTY_DICT, WAMPConstantsBase.ERROR_ProtocolViolation));
                        return WAMPFlowStatus.failed;
                    }
                case T_CHALLENGE:
                    if (!isRouter) {
                        String authMethod = msg.getString(0);
                        WAMPAuthProvider ap = authProviders.get(authMethod);
                        if (ap != null) {
                            WAMPMessage auth = ap.authenticate(session, msg);
                            if (auth != null) {
                                session.send(auth);
                                return WAMPFlowStatus.handled;
                            }
                        }
                        session.send(WAMPMessage.abort(WAMPTools.EMPTY_DICT, WAMPConstantsBase.ERROR_ProtocolViolation));
                        return WAMPFlowStatus.failed;
                    } else {
                        session.send(WAMPMessage.abort(WAMPTools.EMPTY_DICT, WAMPConstantsBase.ERROR_ProtocolViolation));
                        return WAMPFlowStatus.failed;
                    }
                case T_WELCOME:
                    if (!isRouter) {
                        // accept and fetch WELCOME details: session id, remoter roles, agent...
                        session.setId(msg.getId(0));
                        Map<String, Object> details = msg.getDict(1);
                        String agent = null;
                        if (details.containsKey("agent")) {
                            agent = (String) details.get("agent");
                        }
                        Map<String, Map> remoteRoles = (Map<String, Map>) details.get("roles");
                        session.setRemote(new WAMPParty(agent, remoteRoles));
                        if (details.containsKey(K_AUTH_METHOD)) {
                            // set WAMPAuth on client
                            session.setAuth(new WAMPAuth(details));
                        }
                        session.setState(WAMPSessionState.established);
                        return WAMPFlowStatus.handled;
                    } else {
                        session.send(WAMPMessage.abort(WAMPTools.EMPTY_DICT, WAMPConstantsBase.ERROR_ProtocolViolation));
                        return WAMPFlowStatus.failed;
                    }
                case T_ABORT:
                    if (session.getCloseReason() == null) {
                        session.setCloseReason(msg.getUri(1));
                    }
                    session.setState(WAMPSessionState.closed);
                    return WAMPFlowStatus.handled;
                default:
                    throw new WAMPException("Unexpected message type: " + type + "\n  Allowed are: " + WELCOME.getName() + ", " + ABORT.getName() + ", " + ERROR[0].getName());
            }
        } else {
            switch (type.getId()) {
                case T_GOODBYE:
                    if (session.getCloseReason() == null) {
                        session.setCloseReason(msg.getUri(1));
                    }
                    if (session.getState() == WAMPSessionState.closing) {
                        // close, our goodbye is already send
                    } else {
                        session.send(WAMPMessage.goodbye(WAMPTools.EMPTY_DICT, WAMPConstantsBase.INFO_AckClose));
                    }
                    session.close();
                    return WAMPFlowStatus.handled;
            }
        }
        return WAMPFlowStatus.failed;
    }

    /**
     * Method to perform standard protocol violation conditions and do ABORT
     * when detected.
     *
     * @param session
     * @param message
     * @throws WAMPException
     */
    public void checkAbortCondition(WAMPSession session, WAMPMessage message) throws WAMPException {
        String reason = null;
        WAMPSessionState state = session.getState();
        boolean sessionEstablished = WAMPSessionState.established == state;
        int messageType = message.getType().getId();
        if (sessionEstablished) {
            switch (messageType) {
                case T_WELCOME: // Receiving WELCOME message, after session was established.
                case T_HELLO: //Receiving HELLO message, after session was established.
                case T_CHALLENGE: //Receiving CHALLENGE message, after session was established.
                case T_AUTHENTICATE: //Receiving AUTHENTICATE message, after session was established.
                    reason = WAMPConstantsBase.ERROR_ProtocolViolation;
                    break;
            }
        } else {
            switch (messageType) {
                case T_GOODBYE: //Receiving GOODBYE message, before session was established.
                case T_ERROR: //Receiving ERROR message, before session was established.
                //Receiving ERROR message with invalid REQUEST.Type.
                case T_SUBSCRIBED: //Receiving SUBSCRIBED message, before session was established.
                case T_UNSUBSCRIBED: //Receiving UNSUBSCRIBED message, before session was established.
                case T_PUBLISHED: //Receiving PUBLISHED message, before session was established.
                case T_RESULT: //Receiving RESULT message, before session was established.
                case T_REGISTERED: //Receiving REGISTERED message, before session was established.
                case T_UNREGISTERED: //Receiving UNREGISTERED message, before session was established.
                case T_INVOCATION: //Receiving INVOCATION message, before session was established.
                    reason = WAMPConstantsBase.ERROR_ProtocolViolation;
                    break;
            }
            if (reason != null) {
                switch (state) {
                    case open:
                    case established:
                        session.send(WAMPMessage.abort(WAMPTools.createDict("unexpected", message.toList()), reason));
                        break;
                    default:
                        throw new WAMPException("Failed to abort closing/closed session: " + reason);
                }
            }
        }
    }

    public Map<String, Object> prepareWelcomeDetails(WAMPSession session) {
        Map<String, Object> details = WAMPTools.createMap(true);
        Map<String, Map> roles = WAMPTools.createMap(true);
        details.put("roles", roles);
        if (session.getLocal().getAgent() != null) {
            details.put("agent", session.getLocal().getAgent());
        }
        Map<Role, Map<String, Object>> localRoles = session.getLocal().getRoles();
        for (Role r : localRoles.keySet()) {
            Map<String, Object> rmap = WAMPTools.createMap(true);
            rmap.putAll(localRoles.get(r));
            // add supported features if any
            Map<String, Object> fmap = null;
            for (WAMPFeature f : session.getLocal().features()) {
                if (Role.hasRole(r, f.scope())) {
                    if (fmap == null) {
                        fmap = WAMPTools.createMap(true);
                        rmap.put("features", fmap);
                    }
                    fmap.put(f.uri(), true);
                }
            }
            roles.put("" + r, rmap);
        }
        return details;
    }

    /**
     * Returns list of supported Auth methods.
     *
     * @return
     */
    public List<String> getAuthMethods() {
        List<String> lst = new ArrayList<>();
        lst.addAll(authProviders.keySet());
        return lst;
    }
}
