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
package ssg.lib.wamp.messages;

import ssg.lib.wamp.WAMP.Role;

/**
 *
 * @author sesidoro
 */
public class WAMPMessageTypeAdvanced extends WAMPMessageType {

    // OPTIONAL message codes
    public static final int T_CHALLENGE = 4;
    public static final int T_AUTHENTICATE = 5;
    public static final int T_CANCEL = 49;
    public static final int T_INTERRUPT = 69;

    public WAMPMessageTypeAdvanced(int id, String[] signature, Role[] fromFlow, Role[] toFlow) {
        super(id, signature);
        // publishing - publish
        flow.put(id, new Role[][]{
            fromFlow,
            toFlow
        });

    }

    // Advanced: Authentication
    public static WAMPMessageType CHANNELNGE = new WAMPMessageTypeAdvanced(
            T_CHALLENGE,
            new String[]{
                WAMP_DT.uri.toNamed("AuthMethod"),
                WAMP_DT.dict.toNamed("Extra")
            },
            new Role[]{Role.router},
            new Role[]{Role.client}
    );

    public static WAMPMessageType AUTHENTICATE = new WAMPMessageTypeAdvanced(
            T_AUTHENTICATE,
            new String[]{
                WAMP_DT.uri.toNamed("Signature"),
                WAMP_DT.dict.toNamed("Extra")
            },
            new Role[]{Role.client},
            new Role[]{Role.router}
    );

    // Advanced: RPC
    public static final String RPC_CANCEL_OPT_MODE_SKIP = "skip";
    public static final String RPC_CANCEL_OPT_MODE_KILL = "kill";
    public static final String RPC_CANCEL_OPT_MODE_KILLNOWAIT = "killnowait";
    public static WAMPMessageType CANCEL = new WAMPMessageTypeAdvanced(
            T_CANCEL,
            new String[]{
                WAMP_DT.id.toNamed("CALL.Request"),
                WAMP_DT.dict.toNamed("Options")
            },
            new Role[]{Role.caller},
            new Role[]{Role.dealer}
    );
    public static WAMPMessageType INTERRUPT = new WAMPMessageTypeAdvanced(
            T_INTERRUPT,
            new String[]{
                WAMP_DT.uri.toNamed("INVOCATION.Request"),
                WAMP_DT.dict.toNamed("Options")
            },
            new Role[]{Role.dealer},
            new Role[]{Role.callee}
    );

}
