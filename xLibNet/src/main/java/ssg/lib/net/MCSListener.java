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
package ssg.lib.net;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.List;

/**
 * MCS/handlers lifecycle
 *
 * @author 000ssg
 */
public interface MCSListener {

    default void onStarted(MCS cs) {
    }

    default void onStopped(MCS cs) {
    }

    default void onError(MCS cs, Throwable th) {
    }

    /**
     * Extended listener for connections and I/O.
     */
    public static interface MCSListenerX extends MCSListener {

        default void onRead(MCS cs, SelectionKey sk, ByteBuffer buf) {
        }

        default void onWrite(MCS cs, SelectionKey sk, List<ByteBuffer> buf) {
        }

        default void onInvalid(MCS cs, SelectionKey key) {
        }
    }

    public static class DebuggingMCSListener implements MCSListenerX {

        public static final long DO_STARTED = 0x0001;
        public static final long DO_STOPPED = 0x0002;
        public static final long DO_READ = 0x0010;
        public static final long DO_WRITTEN = 0x0020;
        public static final long DO_INVALID = 0x0040;
        public static final long DO_ERROR = 0x0080;
        public static final long DO_ALL
                = DO_STARTED
                | DO_STOPPED
                | DO_READ
                | DO_WRITTEN
                | DO_INVALID
                | DO_ERROR;
        public static final long DO_STRUCTURAL
                = DO_STARTED
                | DO_STOPPED
                | DO_ERROR;

        PrintStream out = System.out;
        long filter = DO_ALL;

        public DebuggingMCSListener() {
        }

        public DebuggingMCSListener(long filter) {
            this.filter = filter;
        }

        public DebuggingMCSListener(PrintStream out) {
            this.out = out;
        }

        public DebuggingMCSListener(PrintStream out, long filter) {
            this.out = out;
            this.filter = filter;
        }

        public boolean isAllowedOption(long option) {
            return (filter & option) == option;
        }

        public String mcsInfo(MCS cs) {
            return cs != null ? (cs.getClass().isAnonymousClass() ? cs.getClass().getName() : cs.getClass().getSimpleName()) + "::" + cs.name : "<none>";
        }

        @Override
        public void onStarted(MCS cs) {
            if (isAllowedOption(DO_STARTED) && out != null) {
                out.println("MCS:STARTED    " + mcsInfo(cs));
            }
        }

        @Override
        public void onStopped(MCS cs) {
            if (isAllowedOption(DO_STOPPED) && out != null) {
                out.println("MCS:STOPPED   " + mcsInfo(cs));
            }
        }

        @Override
        public void onRead(MCS cs, SelectionKey sk, ByteBuffer buf) {
            if (isAllowedOption(DO_READ) && out != null) {
                out.println("MCS:READ      " + mcsInfo(cs) + "  len=" + buf.remaining() + "  key=" + sk);
            }
        }

        @Override
        public void onWrite(MCS cs, SelectionKey sk, List<ByteBuffer> buf) {
            if (isAllowedOption(DO_WRITTEN) && out != null) {
                long c = 0;
                for (ByteBuffer bb : buf) {
                    if (bb != null && bb.hasRemaining()) {
                        c += bb.remaining();
                    }
                }
                out.println("MCS:WRITTEN   " + mcsInfo(cs) + "  len=" + c + "  key=" + sk);
            }
        }

        @Override
        public void onInvalid(MCS cs, SelectionKey key) {
            if (isAllowedOption(DO_INVALID) && out != null) {
                out.println("MCS:INVALID   " + mcsInfo(cs) + "\n  key: " + key);
            }
        }

        @Override
        public void onError(MCS cs, Throwable th) {
            if (isAllowedOption(DO_ERROR) && out != null) {
                out.println("MCS:ERROR     " + mcsInfo(cs) + "\n  err: " + th);
            }
        }

    }
}
