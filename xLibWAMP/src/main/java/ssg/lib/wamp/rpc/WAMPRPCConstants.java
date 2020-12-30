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
package ssg.lib.wamp.rpc;

/**
 * Constants defined in standard (and custom extensions) for use in RPC flow,
 * both names (keys) and values.
 *
 * @author 000ssg
 */
public class WAMPRPCConstants {

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////// CALL/INVOCATION
    ////////////////////////////////////////////////////////////////////////////
    public static final String RPC_CALL_INVOKE_KEY = "invoke"; // values -> enum WAMPRPCRegistrations.RPCMeta.InvocationPolicy
    public static final String RPC_CALL_MATCH_KEY = "match"; // values -> below...
    public static final String RPC_CALL_TIMEOUT = "timeout"; // vzlues - time, ms
    public static final String RPC_CALL_MATCH_EXACT = "exact";
    public static final String RPC_CALL_MATCH_PREFIX = "prefix";
    public static final String RPC_CALL_MATCH_WILDCARD = "wildcard";

    ////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////// CANCEL
    ////////////////////////////////////////////////////////////////////////////
    public static final String RPC_CANCEL_OPT_MODE_KEY = "mode"; // values -> below...
    public static final String RPC_CANCEL_OPT_MODE_SKIP = "skip";
    public static final String RPC_CANCEL_OPT_MODE_KILL = "kill";
    public static final String RPC_CANCEL_OPT_MODE_KILLNOWAIT = "killnowait";

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////// PRCOGRESSIVE RESULT
    ////////////////////////////////////////////////////////////////////////////
    public static final String RPC_PROGRESSIVE_CALL_REQUEST_KEY = "receive_progress"; // value - boolean
    public static final String RPC_PROGRESSIVE_CALL_PROGRESS_KEY = "progress"; // value - boolean

    ////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////// RPC REGISTRATIONS META
    ////////////////////////////////////////////////////////////////////////////
    // Subscriptions
    public static final String RPC_REG_META_TOPIC_ON_CREATE = "wamp.registration.on_create";
    public static final String RPC_REG_META_TOPIC_ON_REGISTER = "wamp.registration.on_register";
    public static final String RPC_REG_META_TOPIC_ON_UNREGISTER = "wamp.registration.on_unregister";
    public static final String RPC_REG_META_TOPIC_ON_DELETE = "wamp.registration.on_delete";

    // RPC names
    public static final String RPC_REG_META_PROC_LIST = "wamp.registration.list";
    public static final String RPC_REG_META_PROC_LOOKUP = "wamp.registration.lookup";
    public static final String RPC_REG_META_PROC_MATCH = "wamp.registration.match";
    public static final String RPC_REG_META_PROC_GET = "wamp.registration.get";
    public static final String RPC_REG_META_PROC_LIST_CALLEES = "wamp.registration.list_callees";
    public static final String RPC_REG_META_PROC_COUNT_CALLEES = "wamp.registration.count_callees";

    // RPC caller disclosure
    public static final String RPC_CALLER_ID_DISCLOSE_ME = "disclose_me";
    public static final String RPC_CALLER_ID_DISCLOSE_CALLER = "disclose_caller";
    public static final String RPC_CALLER_ID_KEY = "caller";

    ////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////// NON_STANDARD
    ////////////////////////////////////////////////////////////////////////////
    /**
     * Introduced to pass actually called procedure for prefix/wildcard match
     * contexts... what is in WAMP spec?
     */
    public static final String RPC_INVOCATION_PROCEDURE_EXACT_KEY = "procedure"; // value - actual procedure name

}
