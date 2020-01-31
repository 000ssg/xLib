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
import ssg.lib.wamp.rpc.impl.caller.CallerCall.CallListener;
import ssg.lib.wamp.util.WAMPTools;

/**
 *
 * @author sesidoro
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
}
