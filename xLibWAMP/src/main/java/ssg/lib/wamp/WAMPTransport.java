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
package ssg.lib.wamp;

import ssg.lib.wamp.auth.WAMPAuth;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.stat.WAMPMessageStatistics;

/**
 * WAMP transport defines send/receive methods to hide transport implementation.
 *
 * @author 000ssg
 */
public interface WAMPTransport {

    /**
     * Enable transport-level authentication info (to enable router-side session
     * authentiction if no explicit request)
     *
     * @return
     */
    WAMPAuth getTransportAuth();

    void send(WAMPMessage message) throws WAMPException;

    WAMPMessage receive() throws WAMPException;

    void unreceive(WAMPMessage last) throws WAMPException;

    boolean isOpen();

    void close();

    void setStatistics(WAMPMessageStatistics statistics);

    void addWAMPTransportMessageListener(WAMPTransportMessageListener... ls);

    void removeWAMPTransportMessageListener(WAMPTransportMessageListener... ls);

    WAMPMessageStatistics getStatistics();

    public static interface WAMPTransportMessageListener {

        void onMessageReceived(WAMPTransport wt, WAMPMessage msg);

        void onMessageUnreceived(WAMPTransport wt, WAMPMessage msg);

        void onMessageSent(WAMPTransport wt, WAMPMessage msg);
    }

    public static class WAMPTransportWrapper implements WAMPTransport {

        private WAMPTransport base;

        public WAMPTransportWrapper(WAMPTransport base) {
            this.base = base;
        }

        public void setBase(WAMPTransport base) {
            this.base = base;
        }

        @Override
        public WAMPAuth getTransportAuth() {
            return base.getTransportAuth();
        }

        @Override
        public void send(WAMPMessage message) throws WAMPException {
            getBase().send(message);
        }

        @Override
        public WAMPMessage receive() throws WAMPException {
            return (getBase() != null) ? getBase().receive() : null;
        }

        @Override
        public void unreceive(WAMPMessage last) throws WAMPException {
            getBase().unreceive(last);
        }

        @Override
        public void close() {
            if (getBase() != null) {
                getBase().close();
            }
        }

        @Override
        public boolean isOpen() {
            return getBase() != null && getBase().isOpen();
        }

        @Override
        public void setStatistics(WAMPMessageStatistics statistics) {
            if (getBase() != null) {
                getBase().setStatistics(statistics);
            }
        }

        @Override
        public WAMPMessageStatistics getStatistics() {
            if (getBase() != null) {
                return getBase().getStatistics();
            } else {
                return null;
            }
        }

        /**
         * @return the base
         */
        public WAMPTransport getBase() {
            return base;
        }

        @Override
        public void addWAMPTransportMessageListener(WAMPTransportMessageListener... ls) {
            base.addWAMPTransportMessageListener(ls);
        }

        @Override
        public void removeWAMPTransportMessageListener(WAMPTransportMessageListener... ls) {
            base.removeWAMPTransportMessageListener(ls);
        }

    }
}
