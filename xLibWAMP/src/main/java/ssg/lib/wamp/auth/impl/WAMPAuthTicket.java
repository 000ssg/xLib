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
package ssg.lib.wamp.auth.impl;

import java.util.List;
import java.util.Map;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.auth.WAMPAuthProvider;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ID;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_METHOD;
import ssg.lib.wamp.messages.WAMPMessage;
import static ssg.lib.wamp.messages.WAMPMessageType.T_AUTHENTICATE;
import static ssg.lib.wamp.messages.WAMPMessageType.T_CHALLENGE;
import static ssg.lib.wamp.messages.WAMPMessageType.T_HELLO;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.util.WAMPTools;

/**
 *
 * @author 000ssg
 */
public abstract class WAMPAuthTicket implements WAMPAuthProvider {

    String defaultTicket;
    Map<Long, Map<String, Object>> wip = WAMPTools.createSynchronizedMap();

    public WAMPAuthTicket() {
    }

    public WAMPAuthTicket(String defaultTicket) {
        this.defaultTicket = defaultTicket;
    }

    @Override
    public String name() {
        return "ticket";
    }

    @Override
    public WAMPMessage challenge(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (session.getLocal().isRouter()) {
            // router
            switch (msg.getType().getId()) {
                case T_HELLO: { // build CHALLENGE msg
                    Map<String, Object> details = msg.getDict(1);
                    List<String> authMethods = (List<String>) details.get(K_AUTH_METHODS);
                    String authid = (String) details.get(K_AUTH_ID);
                    if (authMethods != null && authMethods.contains(name())) {
                        Map<String, Object> dict = WAMPTools.createDict(K_AUTH_METHOD, name(), K_AUTH_ID, authid);
                        wip.put(session.getId(), dict);
                        return WAMPMessage.challenge(name(), WAMPTools.EMPTY_DICT);
                    }
                }
                break;
            }
        } else {
            // client
        }
        return null;
    }

    @Override
    public WAMPMessage authenticate(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (session.getLocal().isRouter()) {
        } else {
            // client
            switch (msg.getType().getId()) {
                case T_CHALLENGE: { // build AUTHENTICATE msg as response to CHALLENGE
                    String authMethod = msg.getString(0);
                    if (name().equals(authMethod)) {
                        return WAMPMessage.authenticate(getTicket(session, msg), WAMPTools.EMPTY_DICT);
                    }
                    break;
                }
            }
        }
        return null;
    }

    @Override
    public Map<String, Object> authenticated(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (session.getLocal().isRouter()) {
            // router
            switch (msg.getType().getId()) {
                case T_AUTHENTICATE: { // verify AUTHENTICATE msg
                    Map<String, Object> auth = wip.remove(session.getId());
                    Map<String, Object> ticketInfo = verifyTicket(session, (String) auth.get(K_AUTH_ID), msg.getString(0));
                    if (ticketInfo != null && !ticketInfo.isEmpty()) {
                        Map<String, Object> authInfo = WAMPTools.createDict(K_AUTH_METHOD, name());
                        authInfo.put(K_AUTH_ID, auth.get(K_AUTH_ID));
                        authInfo.put(K_AUTH_PROVIDER, (auth.containsKey(K_AUTH_PROVIDER) ? auth.get(K_AUTH_PROVIDER) : "self"));
                        authInfo.putAll(ticketInfo);
                        return authInfo;
                    }
                }
                break;
            }
        } else {
        }
        return null;
    }

    @Override
    public boolean isChallenged(WAMPSession session) {
        return wip.containsKey(session.getId());
    }

    public String getTicket(WAMPSession session, WAMPMessage msg) {
        return defaultTicket;
    }

    /**
     * Ticket verification should ensure ticket can/is valid for the authid and
     * return related set of properties, like authrole, authprovider etc.
     *
     * @param session
     * @param authid
     * @param ticket
     * @return
     * @throws WAMPException
     */
    public abstract Map<String, Object> verifyTicket(WAMPSession session, String authid, String ticket) throws WAMPException;
}
