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
 *
 * @author 000ssg
 */
public interface CSListener {

    void onStarted(CS cs);

    void onStopped(CS cs);

    void onAdded(CS cs, Handler handler);

    void onRemoved(CS cs, Handler handler);

    void onHandled(CS cs, SelectionKey sk, Handler h);

    void onRead(CS cs, SelectionKey sk, ByteBuffer buf);

    void onWrite(CS cs, SelectionKey sk, List<ByteBuffer> buf);

    void onInvalid(CS cs, SelectionKey key);

    void onError(CS cs, Throwable th);

    public static class DebuggingCSListener implements CSListener {

        public static final long DO_STARTED = 0x0001;
        public static final long DO_STOPPED = 0x0002;
        public static final long DO_ADDED = 0x0004;
        public static final long DO_REMOVED = 0x0008;
        public static final long DO_HANDLED = 0x0010;
        public static final long DO_READ = 0x0020;
        public static final long DO_WRITTEN = 0x0040;
        public static final long DO_INVALID = 0x0080;
        public static final long DO_ERROR = 0x0100;
        public static final long DO_ALL
                = DO_STARTED
                | DO_STOPPED
                | DO_ADDED
                | DO_REMOVED
                | DO_HANDLED
                | DO_READ
                | DO_WRITTEN
                | DO_INVALID
                | DO_ERROR;
        public static final long DO_STRUCTURAL
                = DO_STARTED
                | DO_STOPPED
                | DO_ADDED
                | DO_REMOVED
                | DO_ERROR;

        PrintStream out = System.out;
        long filter = DO_ALL;

        public DebuggingCSListener() {
        }

        public DebuggingCSListener(long filter) {
            this.filter = filter;
        }

        public DebuggingCSListener(PrintStream out) {
            this.out = out;
        }

        public DebuggingCSListener(PrintStream out, long filter) {
            this.out = out;
            this.filter = filter;
        }

        public boolean isAllowedOption(long option) {
            return (filter & option) == option;
        }

        @Override
        public void onStarted(CS cs) {
            if (isAllowedOption(DO_STARTED) && out != null) {
                out.println("CS:STARTED    " + cs);
            }
        }

        @Override
        public void onStopped(CS cs) {
            if (isAllowedOption(DO_STOPPED) && out != null) {
                out.println("CS:STOPPED   " + cs);
            }
        }

        @Override
        public void onAdded(CS cs, Handler handler) {
            if (isAllowedOption(DO_ADDED) && out != null) {
                out.println("CS:ADDED     " + cs + "\n  handler: " + handler);
            }
        }

        @Override
        public void onRemoved(CS cs, Handler handler) {
            if (isAllowedOption(DO_REMOVED) && out != null) {
                out.println("CS:REMOVED   " + cs + "\n  handler: " + handler);
            }
        }

        @Override
        public void onHandled(CS cs, SelectionKey sk, Handler h) {
            if (isAllowedOption(DO_HANDLED) && out != null) {
                out.println("CS:HANDLED   " + cs + "\n  key: " + sk + "\n  handler: " + h);
            }
        }

        @Override
        public void onRead(CS cs, SelectionKey sk, ByteBuffer buf) {
            if (isAllowedOption(DO_READ) && out != null) {
                out.println("CS:READ      " + cs + "  len=" + buf.remaining() + "  key=" + sk);
            }
        }

        @Override
        public void onWrite(CS cs, SelectionKey sk, List<ByteBuffer> buf) {
            if (isAllowedOption(DO_WRITTEN) && out != null) {
                long c = 0;
                for (ByteBuffer bb : buf) {
                    if (bb != null && bb.hasRemaining()) {
                        c += bb.remaining();
                    }
                }
                out.println("CS:WRITTEN   " + cs + "  len=" + c + "  key=" + sk);
            }
        }

        @Override
        public void onInvalid(CS cs, SelectionKey key) {
            if (isAllowedOption(DO_INVALID) && out != null) {
                out.println("CS:INVALID   " + cs + "\n  key: " + key);
            }
        }

        @Override
        public void onError(CS cs, Throwable th) {
            if (isAllowedOption(DO_ERROR) && out != null) {
                out.println("CS:ERROR     " + cs + "\n  err: " + th);
            }
        }

    }
}
