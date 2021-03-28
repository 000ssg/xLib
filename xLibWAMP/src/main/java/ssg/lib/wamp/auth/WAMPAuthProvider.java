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
package ssg.lib.wamp.auth;

import java.util.Map;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.util.WAMPException;

/**
 * Abstraction of WAMP authentication provider. Must handle challenge (client)
 * and authentication (router) messages.
 *
 * Flow: on HELLO msg router may produce CHALLENGE (based on requested/available
 * auth method providers). On CHALLENGE client generates AUTHENTICATE response.
 * On AUTHENTICATE router validates result and generates proper WELCOME. On
 * WELCOME client keeps locally WAMPAuth.
 *
 * @author 000ssg
 */
public interface WAMPAuthProvider {

    public static final String K_AUTH_METHODS = "authmethods";
    public static final String K_AUTH_METHOD = "authmethod";
    public static final String K_AUTH_ID = "authid";
    public static final String K_AUTH_ROLE = "authrole";
    public static final String K_AUTH_PROVIDER = "authprovider";

    /**
     * Authentication method name
     *
     * @return
     */
    String name();

    /**
     * If does not need challenge, use authenticated() directly on hello message.
     *
     * @param session
     * @param msg
     * @return
     * @throws WAMPException
     */
    boolean needChallenge(WAMPSession session, WAMPMessage msg) throws WAMPException;

    /**
     * Produce challenge message (on router)
     *
     * @param session
     * @param msg HELLO message (or ?)
     * @return
     * @throws WAMPException
     */
    WAMPMessage challenge(WAMPSession session, WAMPMessage msg) throws WAMPException;

    /**
     * Produce authenticate message as response to challenge (on client)
     *
     * @param session
     * @param msg CHALLENGE message
     * @return
     * @throws WAMPException
     */
    WAMPMessage authenticate(WAMPSession session, WAMPMessage msg) throws WAMPException;

    /**
     * Verify authenticate message from client and return true if authenticated
     * or false otherwise.
     *
     * @param session
     * @param msg AUTHENTICATE message
     * @return authentication data for welcome response...
     * @throws WAMPException
     */
    Map<String, Object> authenticated(WAMPSession session, WAMPMessage msg) throws WAMPException;

    /**
     * Returns true if given session has associated challenge info
     *
     * @param session
     * @return
     */
    boolean isChallenged(WAMPSession session);
}
