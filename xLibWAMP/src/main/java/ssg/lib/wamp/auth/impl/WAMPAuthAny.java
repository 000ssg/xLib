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
package ssg.lib.wamp.auth.impl;

import java.util.Map;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.auth.WAMPAuth;
import ssg.lib.wamp.auth.WAMPAuthProvider;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.util.WAMPException;

/**
 *
 * @author 000ssg
 */
public class WAMPAuthAny implements WAMPAuthProvider {

    @Override
    public String name() {
        return "any";
    }

    @Override
    public WAMPMessage challenge(WAMPSession session, WAMPMessage msg) throws WAMPException {
        throw new UnsupportedOperationException("ANY does not use challenge-authenticate messages!");
    }

    @Override
    public WAMPMessage authenticate(WAMPSession session, WAMPMessage msg) throws WAMPException {
        throw new UnsupportedOperationException("ANY does not use challenge-authenticate messages!");
    }

    @Override
    public Map<String, Object> authenticated(WAMPSession session, WAMPMessage msg) throws WAMPException {
        WAMPAuth auth = session.getTransportAuth();
        if (auth != null) {
            session.getRealm().verifySession(session, auth);
            return auth.toMap();
        } else {
            return null;
        }
    }

    @Override
    public boolean isChallenged(WAMPSession session) {
        return false;
    }

    @Override
    public boolean needChallenge(WAMPSession session, WAMPMessage msg) throws WAMPException {
        return false;
    }

}
