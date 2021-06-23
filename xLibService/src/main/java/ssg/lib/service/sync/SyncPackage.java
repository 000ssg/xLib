/*
 * The MIT License
 *
 * Copyright 2021 sesidoro.
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
package ssg.lib.service.sync;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Stack;
import ssg.lib.common.ByteArray;
import ssg.lib.common.JSON;

/**
 * Package is
 */
public class SyncPackage {

    public static final char OP_CREATE = 'C';
    public static final char OP_UPDATE = 'U';
    public static final char OP_REMOVE = 'R';
    public static final char OP_END = 'E';

    static JSON.Encoder je = new JSON.Encoder();
    static JSON.Decoder jd = new JSON.Decoder();

    // [0] type (D(omain)|G(roup)|I(tem)),
    // [1] operation (C(reate)|U(update)|R(emove)|E(nd of level)),
    // [2-9] - timestamp
    // [10-11] - packet extra length (id+value)
    byte[] header = new byte[1 + 1 + 8 + 2];
    byte[] id;
    byte[] value;

    public SyncPackage(SyncItem item, char operation) throws IOException {
        if (item == null || item.getType() == null || item.getTimestamp() == null) {
            throw new IOException("Invalid item for SyncPackage operation '" + operation + "': " + item);
        }
        ByteArray ba = new ByteArray(header);
        switch (item.getType()) {
            case domain:
                ba.setByte(0, (byte) 'D');
                break;
            case group:
                ba.setByte(0, (byte) 'G');
                break;
            case item:
                ba.setByte(0, (byte) 'I');
                break;
        }
        ba.setByte(1, (byte) (0xFF & operation));
        ba.setLong(2, item.getTimestamp());
        id = item.getId().getBytes("UTF-8");
        switch (operation) {
            case OP_CREATE:
            case OP_UPDATE:
                value = value2bytes(item);
                ba.setUShort(10, id.length + (value != null ? value.length : 0));
                break;
            case OP_REMOVE:
            case OP_END:
                ba.setUShort(10, id.length);
                break;
            default:
                throw new IOException("Invalid operation '" + operation + "' for item " + item);
        }
    }

    public byte[] value2bytes(SyncItem item) throws IOException {
        String s = je.writeObject(item.getValue());
        return s != null ? s.getBytes("UTF-8") : null;
    }

    public static Iterable<SyncPackage> getIterable(final SyncItem item, final char operation, final Long timestamp) throws IOException {
        return new Iterable<SyncPackage>() {
            @Override
            public Iterator<SyncPackage> iterator() {
                return new Iterator<SyncPackage>() {
                    SyncPackage next;
                    Stack<Iterator<SyncItem>> wip = new Stack<>();
                    Stack<SyncItem> stack = new Stack<SyncItem>() {
                        {
                            if (timestamp == null || item.getTimestamp() > timestamp) {
                                add(item);
                                wip.add((item.children() != null && !item.children().isEmpty()) ? item.children().iterator() : null);
                                try {
                                    char localOp=OP_UPDATE==operation ? item.getCreated() > timestamp ? OP_CREATE : operation : operation;
                                    next = new SyncPackage(item, operation);
                                } catch (IOException ioex) {
                                    ioex.printStackTrace();
                                }
                            }
                        }
                    };

                    @Override
                    public boolean hasNext() {
                        return next != null || !stack.isEmpty();
                    }

                    @Override
                    public SyncPackage next() {
                        if (next != null) {
                            try {
                                return next;
                            } finally {
                                next = null;
                            }
                        }
                        // evaluate next package
                        SyncItem si = stack.peek();
                        Iterator<SyncItem> it = wip.peek();

                        try {
                            if (it != null && it.hasNext()) {
                                SyncItem ci = it.next();
                                //System.out.println(" child: "+ci);
                                while (!(timestamp == null || ci.getTimestamp() > timestamp)) {
                                    if (it.hasNext()) {
                                        ci = it.next();
                                    } else {
                                        ci = null;
                                        break;
                                    }
                                }
                                if (timestamp == null || ci != null && ci.getTimestamp() > timestamp) {
                                    char localOp = OP_UPDATE == operation ? ci.getCreated() > timestamp ? OP_CREATE : operation : operation;
                                    SyncPackage p = new SyncPackage(ci, localOp);
                                    if (ci.children() != null && !ci.children().isEmpty()) {
                                        stack.push(ci);
                                        wip.push(ci.children().iterator());
                                    }
                                    return p;
                                } else {
                                    stack.pop();
                                    wip.pop();
                                    if (ci != null) {
                                        SyncPackage p = new SyncPackage(ci, OP_END);
                                        return p;
                                    } else if (hasNext()) {
                                        return next();
                                    } else {
                                        int a = 0;
                                        return new SyncPackage(item, OP_END);
                                    }
                                }
                            } else {
                                stack.pop();
                                wip.pop();
                                SyncPackage p = new SyncPackage(si, OP_END);
                                return p;
                            }
                        } catch (IOException ioex) {
                            throw new NoSuchElementException(ioex.toString());
                        }
                    }
                };
            }
        };
    }
}
