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
import java.nio.channels.SelectionKey;

/**
 *
 * @author 000ssg
 */
public interface CSListener extends MCSListener {

    default void onAdded(CS cs, Handler handler) {
    }

    default void onRemoved(CS cs, Handler handler) {
    }

    default void onGroupAdded(CS cs, CSGroup group) {
    }

    default void onGroupRemoved(CS cs, CSGroup group) {
    }

    public static interface CSListenerX extends CSListener {

        default void onHandled(CS cs, SelectionKey sk, Handler h) {
        }
    }

    public static class DebuggingCSListener extends DebuggingMCSListener implements CSListenerX {

        public static final long DO_ADDED = 0x0004;
        public static final long DO_REMOVED = 0x0008;
        public static final long DO_GROUP_ADDED = 0x0100;
        public static final long DO_GROUP_REMOVED = 0x0200;
        public static final long DO_HANDLED = 0x0400;
        public static final long DO_ALL_CS
                = DO_ADDED
                | DO_REMOVED
                | DO_GROUP_ADDED
                | DO_GROUP_REMOVED
                | DO_HANDLED;
        public static final long DO_STRUCTURAL_CS
                = DO_ADDED
                | DO_REMOVED
                | DO_GROUP_ADDED
                | DO_GROUP_REMOVED;
        public static final long DO_ALL
                = MCSListener.DebuggingMCSListener.DO_ALL
                | DO_ALL_CS;
        public static final long DO_STRUCTURAL
                = MCSListener.DebuggingMCSListener.DO_STRUCTURAL
                | DO_STRUCTURAL_CS;

        public DebuggingCSListener() {
        }

        public DebuggingCSListener(long filter) {
            super(filter);
        }

        public DebuggingCSListener(PrintStream out) {
            super(out);
        }

        public DebuggingCSListener(PrintStream out, long filter) {
            super(out, filter);
        }

        @Override
        public void onAdded(CS cs, Handler handler) {
            if (isAllowedOption(DO_ADDED) && out != null) {
                out.println("CS:ADDED     " + mcsInfo(cs) + "\n  handler: " + handler);
            }
        }

        @Override
        public void onRemoved(CS cs, Handler handler) {
            if (isAllowedOption(DO_REMOVED) && out != null) {
                out.println("CS:REMOVED   " + mcsInfo(cs) + "\n  handler: " + handler);
            }
        }

        @Override
        public void onGroupAdded(CS cs, CSGroup group) {
            if (isAllowedOption(DO_GROUP_ADDED) && out != null) {
                out.println("CS:GROUP_ADDED     " + mcsInfo(cs) + "\n  group: " + group);
            }
        }

        @Override
        public void onGroupRemoved(CS cs, CSGroup group) {
            if (isAllowedOption(DO_GROUP_REMOVED) && out != null) {
                out.println("CS:GROUP_REMOVED   " + mcsInfo(cs) + "\n  group: " + group);
            }
        }

        @Override
        public void onHandled(CS cs, SelectionKey sk, Handler h) {
            if (isAllowedOption(DO_HANDLED) && out != null) {
                out.println("CS:HANDLED   " + mcsInfo(cs) + "\n  key: " + sk + "\n  handler: " + h);
            }
        }

    }
}
