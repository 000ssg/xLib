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
package ssg.lib.wamp.rpc.impl;

import java.util.List;
import java.util.Map;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_PROGRESSIVE_CALL_PROGRESS_KEY;
import ssg.lib.wamp.rpc.impl.caller.CallerCall.CallListener;
import ssg.lib.wamp.util.WAMPTools;

/**
 *
 * @author 000ssg
 */
public interface WAMPRPCListener extends CallListener {

    public static enum CALL_STATE {
        prepared,
        running,
        cancelled,
        failed,
        partial,
        completed
    }

    Map<String, Object> getOptions();

    String getProcedure();

    List getArgs();

    Map<String, Object> getArgsKw();

    long getCallId();

    void onCall(long callId);

    void onCancel(long callId, String reason);

    CALL_STATE getState();

    void setState(CALL_STATE state);

    public static abstract class WAMPRPCListenerBase implements WAMPRPCListener {

        long callId;
        Map<String, Object> options;
        String procedure;
        List args;
        Map<String, Object> argsKw;
        private CALL_STATE state = CALL_STATE.prepared;

        public WAMPRPCListenerBase(Map<String, Object> options, String procedure) {
            this.options = options;
            this.procedure = procedure;
        }

        public WAMPRPCListenerBase(Map<String, Object> options, String procedure, List args) {
            this.options = options;
            this.procedure = procedure;
            this.args = args;
        }

        public WAMPRPCListenerBase(Map<String, Object> options, String procedure, List args, Map<String, Object> argsKw) {
            this.options = options;
            this.procedure = procedure;
            this.args = args;
            this.argsKw = argsKw;
        }

        @Override
        public Map<String, Object> getOptions() {
            // TODO: check if empty/no options should be modifiable...
            return (options != null) ? options : WAMPTools.EMPTY_DICT;
        }

        @Override
        public String getProcedure() {
            return procedure;
        }

        @Override
        public List getArgs() {
            return args;
        }

        @Override
        public Map<String, Object> getArgsKw() {
            return argsKw;
        }

        @Override
        public long getCallId() {
            return callId;
        }

        @Override
        public void onCall(long callId) {
            this.callId = callId;
            setState(CALL_STATE.running);
        }

        /**
         * @return the state
         */
        @Override
        public CALL_STATE getState() {
            return state;
        }

        /**
         * @param state the state to set
         */
        @Override
        public void setState(CALL_STATE state) {
            this.state = state;
        }
    }

    public static class WAMPRPCListenerSimple extends WAMPRPCListenerBase {

        ResultCallback resultCallback;
        ErrorCallback errorCallback;
        CancelCallback cancelCallback;

        public WAMPRPCListenerSimple(Map<String, Object> options, String procedure) {
            super(options, procedure);
        }

        public WAMPRPCListenerSimple(Map<String, Object> options, String procedure, List args) {
            super(options, procedure, args);
        }

        public WAMPRPCListenerSimple(Map<String, Object> options, String procedure, List args, Map<String, Object> argsKw) {
            super(options, procedure, args, argsKw);
        }

        public WAMPRPCListenerSimple configureResultCallback(ResultCallback resultCallback) {
            this.resultCallback = resultCallback;
            return this;
        }

        public WAMPRPCListenerSimple configureErrorCallback(ErrorCallback errorCallback) {
            this.errorCallback = errorCallback;
            return this;
        }

        public WAMPRPCListenerSimple configureCancelCallback(CancelCallback cancelCallback) {
            this.cancelCallback = cancelCallback;
            return this;
        }

        public WAMPRPCListenerSimple configureCallback(Object... callbacks) {
            if (callbacks != null) {
                for (Object callback : callbacks) {
                    if (callback instanceof ResultCallback) {
                        configureResultCallback((ResultCallback) callback);
                    }
                    if (callback instanceof ErrorCallback) {
                        configureErrorCallback((ErrorCallback) callback);
                    }
                    if (callback instanceof CancelCallback) {
                        configureCancelCallback((CancelCallback) callback);
                    }
                }
            }
            return this;
        }

        @Override
        public void onCancel(long callId, String reason) {
            if (cancelCallback != null) {
                cancelCallback.onCallback(callId, reason);
            }
        }

        @Override
        public boolean onResult(long callId, Map<String, Object> details, List args, Map<String, Object> argsKw) {
            if (resultCallback != null) {
                return resultCallback.onCallback(callId, details, args, argsKw);
            } else {
                return false;
            }
        }

        @Override
        public void onError(long callId, String error, Map<String, Object> details, List args, Map<String, Object> argsKw) {
            if (errorCallback != null) {
                errorCallback.onCallback(callId, error, details, args, argsKw);
            }
        }
    }

    public static class WAMPRPCListenerWrapper implements WAMPRPCListener {

        WAMPRPCListener base;

        public WAMPRPCListenerWrapper(WAMPRPCListener base) {
            this.base = base;
        }

        public WAMPRPCListener getBase() {
            return base;
        }

        @Override
        public Map<String, Object> getOptions() {
            return base.getOptions();
        }

        @Override
        public String getProcedure() {
            return base.getProcedure();
        }

        @Override
        public List getArgs() {
            return base.getArgs();
        }

        @Override
        public Map<String, Object> getArgsKw() {
            return base.getArgsKw();
        }

        @Override
        public void onCancel(long callId, String reason) {
            base.onCancel(callId, reason);
        }

        @Override
        public boolean onResult(long callId, Map<String, Object> details, List args, Map<String, Object> argsKw) {
            return base.onResult(callId, details, args, argsKw);
        }

        @Override
        public void onError(long callId, String error, Map<String, Object> details, List args, Map<String, Object> argsKw) {
            base.onError(callId, error, details, args, argsKw);
        }

        @Override
        public long getCallId() {
            return base.getCallId();
        }

        @Override
        public void onCall(long callId) {
            base.onCall(callId);
        }

        @Override
        public CALL_STATE getState() {
            return base.getState();
        }

        @Override
        public void setState(CALL_STATE state) {
            base.setState(state);
        }
    }

    public static interface ResultCallback {

        boolean onCallback(long callId, Map<String, Object> details, List args, Map<String, Object> argsKw);
    }

    public static interface ErrorCallback {

        void onCallback(long callId, String error, Map<String, Object> details, List args, Map<String, Object> argsKw);
    }

    public static interface CancelCallback {

        void onCallback(long callId, String reason);
    }

    public static ResultCallback resultCallbackDebug = (long callId, Map<String, Object> details, List args, Map<String, Object> argsKw) -> {
        System.out.println("[" + System.currentTimeMillis() + "][" + Thread.currentThread().getName() + "] " + "result[" + callId + "]: " + details + "; " + args + "; " + argsKw);
        return !(details.containsKey(RPC_PROGRESSIVE_CALL_PROGRESS_KEY) && (Boolean) details.get(RPC_PROGRESSIVE_CALL_PROGRESS_KEY));
    };

    public static ErrorCallback errorCallbackDebug = (long callId, String error, Map<String, Object> details, List args, Map<String, Object> argsKw) -> {
        System.out.println("[" + System.currentTimeMillis() + "][" + Thread.currentThread().getName() + "] " + "error [" + callId + "] " + error + "; " + details + "; " + args + "; " + argsKw);
    };

    public static CancelCallback cancelCallbackDebug = (long callId, String reason) -> {
        System.out.println("[" + System.currentTimeMillis() + "][" + Thread.currentThread().getName() + "] " + "cancel[" + callId + "] " + reason);
    };
}
