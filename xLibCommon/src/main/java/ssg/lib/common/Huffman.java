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
package ssg.lib.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * Huffman algorithm enocidng/decoding for bytes.
 *
 * @author 000ssg
 */
public abstract class Huffman {

    private boolean TRACE = false;

    // value -> int[value,length,bits]
    public abstract Map<Integer, int[]> getDictionary();

    public abstract HTree getRoot();

    public Encoder getEncoder() {
        return new Encoder();
    }

    public Decoder getDecoder() {
        return new Decoder();
    }

    /**
     * Implements dictionary-based encoding.
     */
    public class Encoder {

        byte[] buf = new byte[100];
        int bufPos;
        int bbit = 1 << 7;

        public void add(byte... bytes) throws IOException {
            if (bytes == null || bytes.length == 0) {
                return;
            }

            StringBuilder sb = (isTRACE()) ? new StringBuilder() : null;

            for (byte b : bytes) {
                int[] ii = getDictionary().get(0xFF & b);
                add(ii, sb);
            }
            if (isTRACE()) {
                System.out.println("\n" + sb);
            }
        }

        public void add(int[] ii, StringBuilder sb) {
            int val = ii[2];

            if (sb != null) {
                String s0 = Integer.toBinaryString(val);
                while (s0.length() < ii[1]) {
                    s0 = "0" + s0;
                }
                sb.append("" + s0 + " ");
            }

            int bit = 1 << ii[1] - 1;

            for (int i = 0; i < ii[1]; i++) {
                if ((val & bit) != 0) {
                    buf[bufPos] |= bbit;
                    if (isTRACE()) {
                        System.out.print("1");
                    }
                } else {
                    if (isTRACE()) {
                        System.out.print("0");
                    }
                }
                bbit >>= 1;
                if (bbit == 0) {
                    bufPos++;
                    if (bufPos == buf.length) {
                        buf = Arrays.copyOf(buf, buf.length + 100);
                    }
                    bbit = 1 << 7;
                }
                bit >>= 1;
            }
            if (isTRACE()) {
                System.out.print(" ");
            }
        }

        public void add(ByteBuffer... bbs) throws IOException {
            if (bbs != null) {
                StringBuilder sb = (isTRACE()) ? new StringBuilder() : null;

                for (ByteBuffer bb : bbs) {

                    if (bb == null || !bb.hasRemaining()) {
                        continue;
                    }
                    while (bb.hasRemaining()) {
                        int[] ii = getDictionary().get(0xFF & bb.get());
                        add(ii, sb);
                    }
                }

                if (isTRACE()) {
                    System.out.println("\n" + sb);
                }
            }
        }

        public byte[] getValue() {
            return Arrays.copyOf(buf, bufPos);
        }

        /**
         * Returns completed bytes keeping state for further processing...
         *
         * @return
         */
        public byte[] flushAndGet() {
            if (bufPos > 0) {
                synchronized (this) {
                    byte[] r = Arrays.copyOf(buf, bufPos);
                    byte last = buf[bufPos];
                    buf = new byte[100];
                    buf[0] = last;
                    bufPos = 0;
                    return r;
                }
            }
            return new byte[0];
        }

        public int currentSize() {
            return bufPos;
        }

        public void close() throws IOException {
            if (bbit == -1) {
                return;
            }
            if (bbit != 0x80) {
                while (bbit != 0) {
                    buf[bufPos] |= bbit;
                    bbit >>= 1;
                }
                bufPos++;
            }
            bbit = -1;
        }

        public void reset() {
            Arrays.fill(buf, (byte) 0);
            bufPos = 0;
            bbit = 1 << 7;
        }
    }

    /**
     * Implements dictionary-based decoding.
     */
    public class Decoder {

        byte[] buf = new byte[100];
        int bufPos = 0;
        HTree node = getRoot();

        public void add(byte... bytes) throws IOException {
            if (bytes == null || bytes.length == 0) {
                return;
            }

            for (byte b : bytes) {
                add(b);
            }
            if (isTRACE()) {
                System.out.println();
            }
        }

        public void add(byte b) {
            int bit = 1 << 7;
            for (int i = 0; i < 8; i++) {
                if ((b & bit) == 0) {
                    if (isTRACE()) {
                        System.out.print("0");
                    }
                    node = node.zero;
                } else {
                    if (isTRACE()) {
                        System.out.print("1");
                    }
                    node = node.one;
                }
                if (node.value != null) {
                    if (bufPos == buf.length) {
                        buf = Arrays.copyOf(buf, buf.length + 100);
                    }
                    buf[bufPos++] = node.value.byteValue();
                    node = getRoot();
                    if (isTRACE()) {
                        System.out.print(" ");
                    }
                }
                bit >>= 1;
            }
        }

        public void add(ByteBuffer... bbs) throws IOException {
            if (bbs == null || bbs.length == 0) {
                return;
            }

            for (ByteBuffer bb : bbs) {
                if (bb == null || !bb.hasRemaining()) {
                    continue;
                }
                while (bb.hasRemaining()) {
                    add(bb.get());
                }
            }
            if (isTRACE()) {
                System.out.println();
            }
        }

        public byte[] getValue() {
            return Arrays.copyOf(buf, bufPos);
        }

        /**
         * Returns completed bytes keeping state for further processing...
         *
         * @return
         */
        public byte[] flushAndGet() {
            if (bufPos > 0) {
                synchronized (this) {
                    byte[] r = Arrays.copyOf(buf, bufPos);
                    buf = new byte[100];
                    bufPos = 0;
                    return r;
                }
            }
            return new byte[0];
        }

        public int currentSize() {
            return bufPos;
        }

        public void close() throws IOException {

        }

        public void reset() {
            bufPos = 0;
            node = getRoot();
            Arrays.fill(buf, (byte) 0);
        }
    }

    public static HTree buildTree(HTree parent, Boolean forZero, Map<Integer, int[]> dict, Collection<Integer> consumed) {
        HTree node = new HTree();
        if (parent == null) {
            parent = node;
        } else {
            node.parent = parent;
            node.level = parent.level + 1;
        }

        //System.out.println("create at '" + parent.bits() + "' " + forZero);
        boolean found0 = false;
        boolean found1 = false;
        for (int[] ii : dict.values()) {
            if (consumed.contains(ii[0])) {
                continue;
            }
            if (ii[1] < node.level) {
                continue;
            } else if (ii[1] == node.level && (forZero != null && (forZero && (ii[2] & 0x1) == 0) || (!forZero && (ii[2] & 0x1) == 1))) {
                node.value = ii[0];
                node.bits = ii[2];
                consumed.add(node.value);
                found0 = false;
                found1 = false;
                break;
            } else {
                int bit = 1 << (ii[1] - node.level - 1);
                //String s=Integer.toBinaryString(ii[2]);
                //while(s.length()<ii[1])s="0"+s;
                //System.out.println("  len="+ii[1]+", lev="+node.level+"\n   bits='"+Integer.toBinaryString(bit)+"'\n    val='"+s+"', 0="+((ii[2] & bit) == 0));
                if ((ii[2] & bit) == 0) {
                    found0 = true;
                } else {
                    found1 = true;
                }
            }
            if (found0 && found1) {
                //break;
            }
        }

        //System.out.println("adding node at " + node.level + ", for 0=" + forZero + ": val=" + node.value + ", 0=" + found0 + ", 1=" + found1);
        if (found0) {
            node.zero = buildTree(node, true, dict, consumed);
        }
        if (found1) {
            node.one = buildTree(node, false, dict, consumed);
        }

        return node;
    }

    /**
     * Decoder support.
     */
    public static class HTree {

        HTree parent;
        int level;
        HTree zero;
        HTree one;
        Integer value;
        Integer bits;

        public HTree() {
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (zero != null) {
                sb.append("[0] " + zero.toString().replace("\n", "\n    ") + "\n");
            }
            if (one != null) {
                sb.append("[1] " + one.toString().replace("\n", "\n    ") + "\n");
            }
            if (value != null) {
                sb.append("<" + level + "> " + value + "  " + bits());
            }
            return sb.toString();
        }

        public String bits() {
            StringBuilder sb = new StringBuilder();
            if (parent != null) {
                sb.append(parent.bits() + ((parent.zero == this) ? "0" : "1"));
            }
            if (value != null) {
                sb.append("" + (bits % 2));
            }
            return sb.toString();
        }
    }

    /**
     * @return the TRACE
     */
    public boolean isTRACE() {
        return TRACE;
    }

    /**
     * @param TRACE the TRACE to set
     */
    public void setTRACE(boolean TRACE) {
        this.TRACE = TRACE;
    }
}
