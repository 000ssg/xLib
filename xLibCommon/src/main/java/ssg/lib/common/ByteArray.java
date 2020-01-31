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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import ssg.lib.common.buffers.BufferTools;

/**
 * Class to enable byte and bit-level access to binary data with utility methods
 * to read/write java numeric and text types.
 *
 * @author ssg
 */
public class ByteArray {

    public static final ByteArray EMPTY = new ByteArray(new byte[0]) {
    };
    public static ByteArrayNamedType DEFAULT_NT = new ByteArrayNamedType();
    ByteArray _parent;
    byte[] data;
    int offset;
    int maxOffset;
    Boolean le;
    ByteArrayNamedType nt;

    public ByteArray(ByteBuffer data) {
        if (data != null && data.hasRemaining()) {
            this.data = new byte[data.remaining()];
            data.get(this.data);
            offset = 0;
            maxOffset = this.data.length - 1;
        }
    }

    public ByteArray(byte[] data) {
        if (data == null) {
            throw new InvalidParameterException("Cannot instantiate ByteArray instance: no data (null) is provided.");
        }
        this.data = data;
        offset = 0;
        maxOffset = data.length - 1;
    }

    public ByteArray(byte[] data, int offset) {
        if (data == null) {
            throw new InvalidParameterException("Cannot instantiate ByteArray instance: no data (null) is provided.");
        }
        if (offset >= data.length) {
            throw new ArrayIndexOutOfBoundsException("Cannot instantiate ByteArray: initial offset beyond data range (data length=" + data.length + ", requested offset=" + offset);
        }
        this.data = data;
        this.offset = offset;
        maxOffset = data.length - 1;
    }

    public ByteArray getByteArray(int offset, int len) {
        ByteArray result = new ByteArray(data, this.offset + offset, len);
        result._parent = this;
        return result;
    }

    /**
     * Weak constructor: if length cannot be set then actual data size is used
     * to set it.
     *
     * @param data
     * @param offset
     * @param len
     */
    public ByteArray(byte[] data, int offset, int len) {
        if (data == null) {
            throw new InvalidParameterException("Cannot instantiate ByteArray instance: no data (null) is provided.");
        }
        if (offset >= data.length) {
            throw new ArrayIndexOutOfBoundsException("Cannot instantiate ByteArray: initial offset beyond data range (data length=" + data.length + ", requested offset=" + offset);
        }
        this.data = data;
        this.offset = offset;
        maxOffset = (len >= 0) ? Math.min(data.length - 1, offset + len - 1) : data.length - 1;
    }

    public ByteArray(byte[] data, int offset, int len, boolean isLE) {
        if (data == null) {
            throw new InvalidParameterException("Cannot instantiate ByteArray instance: no data (null) is provided.");
        }
        if (offset >= data.length) {
            throw new ArrayIndexOutOfBoundsException("Cannot instantiate ByteArray: initial offset beyond data range (data length=" + data.length + ", requested offset=" + offset);
        }
        this.data = data;
        this.offset = offset;
        maxOffset = (len >= 0) ? Math.min(data.length - 1, offset + len - 1) : data.length - 1;
        setLE(isLE);
    }
    
    public boolean isValidOffset(int offset) {
        //return offset >= this.offset && offset <= maxOffset;
        return offset >= 0 && (this.offset + offset) <= maxOffset;
    }

    /**
     * slide structure over underlying byte array
     *
     * @param offset
     */
    public void toOffset(int offset) {
        this.offset = offset;
        maxOffset = data.length - 1;
    }

    /**
     * set base to data at offset
     *
     * @param data
     * @param offset
     */
    public void setData(byte[] data, int offset) {
        this.data = data;
        this.offset = offset;
        maxOffset = data.length - 1;
    }

    @Override
    public String toString() {
        return "ByteArray: data len=" + data.length + ", offset=" + offset + ", size=" + getSize() + " bytes=" + BufferTools.dump(getBytes(0, Math.min(getSize(), 16)));// CommonTools.bytesToHex(getBytes(0, Math.min(getSize(), 16)), 8, 17, " ", " | ", "\n") + ((getSize() > 16) ? "..." : "");
    }

    /**
     * Clones selected byte array data. To keep reference to original bytes
     * array (e.g. for updating it) use getByteArray.
     *
     * NOTE: if special len(-1) is used then returns data from logical offset to
     * end of physical array! To get data within current ByteArray limits use
     * getBytes(offset) or getBytes().
     *
     * @param offset
     * @param len
     * @return
     */
    public byte[] getBytes(int offset, int len) {
        return Arrays.copyOfRange(data, this.offset + offset, (len == -1) ? data.length : this.offset + offset + len);
    }

    /**
     * Clones selected byte array data starting at offset to end.
     *
     * @param offset
     * @param len
     * @return
     */
    public byte[] getBytes(int offset) {
        return getBytes(offset, getSize() - offset);
    }

    /**
     * Clones byte array data as new byte[] array.
     *
     * @return
     */
    public byte[] getBytes() {
        return getBytes(0, getSize());
    }

    public int setBytes(int offset, byte[] data) {
        setBytes(offset, data, 0, data.length);
        return data.length;
    }

    public int setBytes(int offset, ByteArray data) {
        for (int i = 0; i < data.getSize(); i++) {
            setByte(offset + i, data.getByte(i));
        }
        return data.getSize();
    }

    public int getBaseOffset() {
        return offset;
    }

    public int getMaxOffset() {
        return maxOffset;
    }

    public int getMaxMaxOffset() {
        return data.length - 1;
    }

    /**
     * Weak size assigner: if size cannot be assigned (due to array bounds
     * problem) it is silently ignored.
     *
     * @param size
     */
    public void setSize(int size) {
        if (offset + size <= data.length) {
            maxOffset = offset + size - 1;
        }
    }

    public int getSize() {
        return maxOffset - offset + 1;
    }

    ////////////////////////////////////// byte-order control
    /**
     * Default is Big-Endian format. It may be changed to LE for sequence if
     * needed. Applied to groupped bytes of short, int, long, float, double
     * types.
     *
     * @return
     */
    public boolean isLE() {
        if (_parent != null) {
            return _parent.isLE();
        }
        return (le == null) ? true : le;
    }

    public void setLE(Boolean le) {
        this.le = le;
    }
    
    public byte[] data() {
        return data;
    }

    ////////////////////////////////////// Primitive data type accessors
    public byte getByte(int offset) {
        if (this.offset + offset > maxOffset) {
            throw new ArrayIndexOutOfBoundsException("Tried to get byte at " + (this.offset + offset) + ", while max is " + maxOffset);
        }
        return data[this.offset + offset];
    }

    public short getUByte(int offset) {
        return (short) (getByte(offset) & 0xFF);
    }

    public void setByte(int offset, byte b) {
        data[this.offset + offset] = b;
    }

    public boolean getBit(int offset, byte bit) {
        if (bit < 0 || bit > 7) {
            return false;
        }
        byte mask = (byte) (1 << bit);
        return (getByte(offset) & mask) != 0;
    }

    public void setBit(int offset, byte bit, boolean setIt) {
        if (bit < 0 || bit > 7) {
            return;
        }
        byte mask = (byte) (1 << bit);
        if (setIt) {
            setByte(offset, (byte) (getByte(offset) | mask));
        } else {
            setByte(offset, (byte) (getByte(offset) & ~mask));
        }
    }

    public byte getByteHalf(int offset, boolean low) {
        return (low)
                ? (byte) (getByte(offset) & 0x0f)
                : (byte) ((getByte(offset) >> 4) & 0x0f);
    }

    public void setByteHalf(int offset, boolean low, byte value) {
        if (low) {
            setByte(offset, (byte) ((getByte(offset) & 0xf0) | (value & 0x0f)));
        } else {
            setByte(offset, (byte) ((getByte(offset) & 0x0f) | ((value & 0x0f) << 4)));
        }
    }

    public byte[] getBytes2(int offset, int len) {
        byte[] result = new byte[len];
        if (isLE()) {
            for (int i = offset; i < offset + len; i++) {
                result[i - offset] = getByte(i);
            }
        } else {
            for (int i = offset; i < offset + len; i++) {
                result[len - (i - offset) - 1] = getByte(i);
            }
        }
        return result;
    }

    /**
     * Returns integer value at specified offset + bit offset of specified bits
     * length. TODO: setter for this value.
     *
     * @param offset
     * @param bitOffset
     * @param bits
     * @return
     */
    public long getUIntBits(int offset, byte bitOffset, byte bits) {
        while (bitOffset > 8) {
            offset++;
            bitOffset -= 8;
        }
        long l = 0;
        long mask = 0;
        for (int i = 0; i < bits; i++) {
            mask |= 1 << i;
        }
        if (bits + bitOffset > 32) {
            l = getLong(offset);
        } else if (bits + bitOffset > 24) {
            l = getUInt(offset);
        } else if (bits + bitOffset > 16) {
            l = getInt3(offset);
        } else if (bits + bitOffset > 8) {
            l = getUShort(offset);
        } else {
            l = getUByte(offset);
        }
        if (bitOffset > 0) {
            return (l >> bitOffset) & mask;
            //return l >> bitOffset;
        } else {
            return l & mask;
        }
    }

    public long getUIntBits(int offset, int bitOffset, int bits) {
        return getUIntBits(offset, (byte) bitOffset, (byte) bits);
    }

    public long getLong(int offset) {
        return (isLE())
                ? (long) ((getByte(offset + 7) & 0xFF) << 56
                | (long) (getByte(offset + 6) & 0xFF) << 48
                | (long) (getByte(offset + 5) & 0xFF) << 40
                | (long) (getByte(offset + 4) & 0xFF) << 32
                | (getByte(offset + 3) & 0xFF) << 24
                | (getByte(offset + 2) & 0xFF) << 16
                | (getByte(offset + 1) & 0xFF) << 8
                | (getByte(offset) & 0xFF))
                : (long) ((getByte(offset) & 0xFF) << 56
                | (long) (getByte(offset + 1) & 0xFF) << 48
                | (long) (getByte(offset + 2) & 0xFF) << 40
                | (long) (getByte(offset + 3) & 0xFF) << 32
                | (getByte(offset + 4) & 0xFF) << 24
                | (getByte(offset + 5) & 0xFF) << 16
                | (getByte(offset + 6) & 0xFF) << 8
                | (getByte(offset + 7) & 0xFF));
    }

    public void setLong(int offset, long l) {
        if (isLE()) {
            setByte(offset + 7, (byte) ((l >> 56) & 0xFF));
            setByte(offset + 6, (byte) ((l >> 48) & 0xFF));
            setByte(offset + 5, (byte) ((l >> 40) & 0xFF));
            setByte(offset + 4, (byte) ((l >> 32) & 0xFF));
            setByte(offset + 3, (byte) ((l >> 24) & 0xFF));
            setByte(offset + 2, (byte) ((l >> 16) & 0xFF));
            setByte(offset + 1, (byte) ((l >> 8) & 0xFF));
            setByte(offset + 0, (byte) (l & 0xFF));
        } else {
            setByte(offset + 0, (byte) ((l >> 56) & 0xFF));
            setByte(offset + 1, (byte) ((l >> 48) & 0xFF));
            setByte(offset + 2, (byte) ((l >> 40) & 0xFF));
            setByte(offset + 3, (byte) ((l >> 32) & 0xFF));
            setByte(offset + 4, (byte) ((l >> 24) & 0xFF));
            setByte(offset + 5, (byte) ((l >> 16) & 0xFF));
            setByte(offset + 6, (byte) ((l >> 8) & 0xFF));
            setByte(offset + 7, (byte) (l & 0xFF));
        }
    }

    public int getInt(int offset) {
        return (isLE())
                ? ((getByte(offset + 3) & 0xFF) << 24
                | (getByte(offset + 2) & 0xFF) << 16
                | (getByte(offset + 1) & 0xFF) << 8
                | (getByte(offset) & 0xFF))
                : ((getByte(offset) & 0xFF) << 24
                | (getByte(offset + 1) & 0xFF) << 16
                | (getByte(offset + 2) & 0xFF) << 8
                | (getByte(offset + 3) & 0xFF));
    }

    public long getUInt(int offset) {
        return (isLE())
                ? (long) (0L + (getByte(offset + 3) & 0xFF) << 24
                | (getByte(offset + 2) & 0xFF) << 16
                | (getByte(offset + 1) & 0xFF) << 8
                | (getByte(offset) & 0xFF))
                : (long) (0L + (getByte(offset) & 0xFF) << 24
                | (getByte(offset + 1) & 0xFF) << 16
                | (getByte(offset + 2) & 0xFF) << 8
                | (getByte(offset + 3) & 0xFF));
    }

    public void setInt(int offset, int i) {
        if (isLE()) {
            setByte(offset + 3, (byte) ((i >> 24) & 0xFF));
            setByte(offset + 2, (byte) ((i >> 16) & 0xFF));
            setByte(offset + 1, (byte) ((i >> 8) & 0xFF));
            setByte(offset + 0, (byte) (i & 0xFF));
        } else {
            setByte(offset + 0, (byte) ((i >> 24) & 0xFF));
            setByte(offset + 1, (byte) ((i >> 16) & 0xFF));
            setByte(offset + 2, (byte) ((i >> 8) & 0xFF));
            setByte(offset + 3, (byte) (i & 0xFF));
        }
    }

    public int getInt3(int offset) {
        return (isLE())
                ? (getByte(offset + 2) & 0xFF) << 16
                | (getByte(offset + 1) & 0xFF) << 8
                | (getByte(offset) & 0xFF)
                : (getByte(offset) & 0xFF) << 16
                | (getByte(offset + 1) & 0xFF) << 8
                | (getByte(offset + 2) & 0xFF);
    }

    public void setInt3(int offset, int i) {
        if (isLE()) {
            setByte(offset + 2, (byte) ((i >> 16) & 0xFF));
            setByte(offset + 1, (byte) ((i >> 8) & 0xFF));
            setByte(offset + 0, (byte) (i & 0xFF));
        } else {
            setByte(offset + 0, (byte) ((i >> 16) & 0xFF));
            setByte(offset + 1, (byte) ((i >> 8) & 0xFF));
            setByte(offset + 2, (byte) (i & 0xFF));
        }
    }

    public short getShort(int offset) {
        return (isLE())
                ? (short) ((getByte(offset + 1) & 0xFF) << 8
                | (getByte(offset) & 0xFF))
                : (short) ((getByte(offset) & 0xFF) << 8
                | (getByte(offset + 1) & 0xFF));
    }

    public int getUShort(int offset) {
        return (isLE())
                ? ((0 & 0xFF) << 24
                | (0 & 0xFF) << 16
                | (getByte(offset + 1) & 0xFF) << 8
                | (getByte(offset) & 0xFF))
                : ((0 & 0xFF) << 24
                | (0 & 0xFF) << 16
                | (getByte(offset) & 0xFF) << 8
                | (getByte(offset + 1) & 0xFF));
    }

    public void setShort(int offset, short sh) {
        if (isLE()) {
            setByte(offset + 1, (byte) ((sh >> 8) & 0xFF));
            setByte(offset + 0, (byte) (sh & 0xFF));
        } else {
            setByte(offset + 0, (byte) ((sh >> 8) & 0xFF));
            setByte(offset + 1, (byte) (sh & 0xFF));
        }
    }

    public void setUShort(int offset, int sh) {
        if (isLE()) {
            setByte(offset + 1, (byte) ((sh >> 8) & 0xFF));
            setByte(offset + 0, (byte) (sh & 0xFF));
        } else {
            setByte(offset + 0, (byte) ((sh >> 8) & 0xFF));
            setByte(offset + 1, (byte) (sh & 0xFF));
        }
    }

    public Date getDate(int offset) {
        return new Date(getLong(offset));
    }

    public Date getDate4(int offset) {
        return new Date(getInt(offset));

    }

    public float getFloat(int offset) {
        return Float.intBitsToFloat(getInt(offset));
    }

    public void setFloat(int offset, float v) {
        setInt(offset, Float.floatToIntBits(v));
    }

    public double getDouble(int offset) {
        return Double.longBitsToDouble(getLong(offset));
    }

    public void setDouble(int offset, double v) {
        setLong(offset, Double.doubleToLongBits(v));
    }

    public String getString(int offset, int length, Charset charset) {
        try {
            if (charset == null) {
                charset = Charset.forName("ISO-8859-1");
            }
            return new String(data, this.offset + offset, length, charset);
        } catch (Throwable th) {
            System.err.println("Failed to read string: " + th);
            return null;
        }
    }

    public void setString(int offset, String value, int maxLength, Charset charset) {
        try {
            if (charset == null) {
                charset = Charset.forName("ISO-8859-1");
            }
            byte[] buf = value.getBytes(charset);
            setBytes(offset, buf, 0, Math.min(buf.length, maxLength));
        } catch (Throwable th) {
            System.err.println("Failed to write string: " + th);
        }
    }

    public void setBytes(int offset, byte[] buf, int off, int len) {
        int off0 = this.offset + offset;
        for (int i = off; i < (len + off); i++) {
            data[off0 + i] = buf[i];
        }
    }

    /**
     * Returns string encoded with TBDIC algorithm.
     *
     * @param offset
     * @param size
     * @return
     */
    public String getTBDICString(int offset, int size) {
        StringBuilder sb = new StringBuilder();
        int maxOffset = Math.min(offset + size - 1, this.maxOffset);
        for (int idx = offset; idx <= maxOffset; idx++) {
            sb.append(getByteHalf(idx, true));
            if (getByteHalf(idx, false) != 0xF) {
                sb.append(getByteHalf(idx, false));
            } else {
                break;
            }
        }
        return sb.toString();
    }

    /**
     * DSS format: string is sequence of [len][chars>] pairs where len is length
     * of chars. Result is sequence of strings built from chars separated with
     * dots.
     *
     * @param offset
     * @param size
     * @return
     */
    public String getDotSeparatedString(int offset, int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            int len = getUByte(offset + i);
            if (i > 0) {
                sb.append('.');
            }
            sb.append(getString(offset + i + 1, len, null));
            i += len;
        }
        return sb.toString();
    }

    ///////////////////////////////////////////////////////// array types but byte[]
    public byte[][] getBlocks(int offset, int length, int blockSize) {
        byte[][] result = new byte[length / blockSize][];
        for (int i = 0; i < length / blockSize; i++) {
            result[i] = getBytes(offset + i * blockSize, blockSize);
        }
        return result;
    }

    public short[] getUBytes(int offset, int length) {
        short[] result = new short[length / 2];
        for (int i = 0; i < length / 2; i++) {
            result[i] = getUByte(offset + i * 2);
        }
        return result;
    }

    public short[] getShorts(int offset, int length) {
        short[] result = new short[length / 2];
        for (int i = 0; i < length / 2; i++) {
            result[i] = getShort(offset + i * 2);
        }
        return result;
    }

    public int[] getUShorts(int offset, int length) {
        int[] result = new int[length / 2];
        for (int i = 0; i < length / 2; i++) {
            result[i] = getUShort(offset + i * 2);
        }
        return result;
    }

    public int[] getInts(int offset, int length) {
        int[] result = new int[length / 4];
        for (int i = 0; i < length / 4; i++) {
            result[i] = getInt(offset + i * 4);
        }
        return result;
    }

    public int[] getInt3s(int offset, int length) {
        int[] result = new int[length / 3];
        for (int i = 0; i < length / 3; i++) {
            result[i] = getInt3(offset + i * 3);
        }
        return result;
    }

    public long[] getUInts(int offset, int length) {
        long[] result = new long[length / 4];
        for (int i = 0; i < length / 4; i++) {
            result[i] = getUInt(offset + i * 4);
        }
        return result;
    }

    public long[] getLongs(int offset, int length) {
        long[] result = new long[length / 8];
        for (int i = 0; i < length / 8; i++) {
            result[i] = getInt(offset + i * 8);
        }
        return result;
    }

    /////////////////////////////////////////////////////////////// named types
    /**
     * Returns type names handler. If local type name handler is undefined then
     * parent's is used or, if none - default one (ByteArray.DEFAULT_NT).
     *
     * @return
     */
    public ByteArrayNamedType getNametTypeHandler() {
        if (nt != null) {
            return nt;
        } else if (_parent != null) {
            return _parent.getNametTypeHandler();
        } else {
            return DEFAULT_NT;
        }
    }

    /**
     * Assigns or clears assignment (nt=null) of specific type handler.
     *
     * @param nt
     */
    public void setNametTypeHandler(ByteArrayNamedType nt) {
        this.nt = nt;
    }

    /**
     * Returns value as specified type. Types support is implemented in
     * ByteArrayNamedType class or its extension.
     *
     * @param <T>
     * @param offset
     * @param length
     * @param type
     * @return
     */
    public <T> T getValueAt(int offset, int length, String type) {
        if (nt != null) {
            return nt.getValueAt(this, offset, length, type);
        } else {
            return DEFAULT_NT.getValueAt(this, offset, length, type);
        }
    }

    /**
     * Returns size of data for scalar or for array if contains "[<number>]"
     * pattern.
     *
     * Returns -1 for dynamic-length data (e.g. array defined with "[]",
     * "bytes", "string". Returns 0 for unknown/undefined or zero-length array
     * definitions.
     *
     * @param type
     * @return
     */
    public int getValueSizeForType(String type) {
        if (nt != null) {
            return nt.getValueSizeForType(type);
        } else {
            return DEFAULT_NT.getValueSizeForType(type);
        }
    }

    /**
     * @return the _parent
     */
    public ByteArray getParent() {
        return _parent;
    }

    /**
     * @param parent the _parent to set
     */
    public void setParent(ByteArray parent) {
        this._parent = parent;
    }

    /**
     * Try to copy specified amount of bytes from given offset to target
     * ByteArray. Returns # of actually copies bytes that may be less than
     * requested.
     *
     * @param offset
     * @param len
     * @param target
     * @return
     */
    public int copyBytes(int offset, int len, ByteArray target) {
        int idx0 = this.offset + offset;
        int idx1 = idx0 + len;
        if (idx1 > maxOffset) {
            idx1 = maxOffset;
        }
        int odx0 = target.offset;
        int odx1 = target.offset + len;
        if (odx1 > target.maxOffset) {
            odx1 = target.maxOffset;
        }
        if ((odx1 - odx0) < (idx1 - idx0)) {
            idx1 = idx0 + (odx1 - odx0);
        }
        len = idx1 - idx0;
        byte[] src = data();
        byte[] dst = target.data();
        System.arraycopy(src, idx0, dst, odx0, len);
        return len;
    }

    /**
     * Provides composite multi-data byte array functionality by composing
     * continuous bytes space from fragments.
     */
    public static class XByteArray extends ByteArray {

        static byte[] noData = new byte[0];
        ByteArray[] set;
        int[][] offs;
        int[] sizes;
        int _maxOffset; // absolute max

        public XByteArray(byte[]... set) {
            super(noData);
            if (set != null) {
                this.set = new ByteArray[set.length];
                for (int i = 0; i < set.length; i++) {
                    this.set[i] = new ByteArray(set[i]);
                }
            }
            offset = 0;
            init();
        }

        public XByteArray(ByteArray... set) {
            super(noData);
            this.set = set;
            offset = 0;
            init();
        }

        public XByteArray(Object... set) {
            super(noData);
            this.set = verify(set);
            offset = 0;
            init();
        }

        /**
         * Retrieves from set all byte[] or ByteArray instances, including those
         * within arrays and/or collections
         *
         * @param set
         * @return
         */
        public static ByteArray[] verify(Object... set) {
            if (set == null || set.length == 0) {
                return null;
            }
            List<ByteArray> l = new ArrayList<ByteArray>();
            for (Object o : set) {
                if (o instanceof Collection) {
                    for (Object oo : (Collection) o) {
                        ByteArray[] ooo = verify(oo);
                        if (ooo != null) {
                            for (ByteArray o1 : ooo) {
                                l.add(o1);
                            }
                        }
                    }
                } else if (o instanceof byte[]) {
                    l.add(new ByteArray((byte[]) o));
                } else if (o instanceof ByteArray) {
                    l.add((ByteArray) o);
                } else if (o != null && o instanceof Object[]) {
                    for (Object oo : (Object[]) o) {
                        ByteArray[] ooo = verify(oo);
                        if (ooo != null) {
                            for (ByteArray o1 : ooo) {
                                l.add(o1);
                            }
                        }
                    }
                }
            }
            if (l.isEmpty()) {
                return null;
            } else {
                return l.toArray(new ByteArray[l.size()]);
            }
        }

        public void add(Object... set) {
            ByteArray[] bas = verify(set);
            if (bas != null) {
                if (bas.length > 0) {
                    int off = this.set.length;
                    this.set = Arrays.copyOf(this.set, off + bas.length);
                    for (int i = 0; i < bas.length; i++) {
                        this.set[off + i] = bas[i];
                    }

                    off = offset;
                    offset = 0;
                    init();
                    offset = off;
                }
            }
        }

        void init() {
            if (set != null) {
                offs = new int[set.length][2];
                sizes = new int[set.length];
                int lastOff = 0;
                int lastSize = 0;
                for (int i = 0; i < offs.length; i++) {
                    ByteArray ba = set[i];
                    sizes[i] = ba.getSize();
                    offs[i][0] = lastOff + lastSize;
                    offs[i][1] = offs[i][0] + sizes[i] - 1;
                    lastOff = offs[i][0];
                    lastSize = sizes[i];
                }
                maxOffset = lastOff + lastSize - 1;
                _maxOffset = maxOffset;
            }
        }

        @Override
        public int copyBytes(int offset, int len, ByteArray target) {
//            int idx0 = this.offset + offset;
//            int idx1 = idx0 + len;
//            if (idx1 > maxOffset) {
//                idx1 = maxOffset;
//            }
//            int odx0 = target.offset;
//            int odx1 = target.offset + len;
//            if (odx1 > target.maxOffset) {
//                odx1 = target.maxOffset;
//            }
//            if ((odx1 - odx0) < (idx1 - idx0)) {
//                idx1 = idx0 + (odx1 - odx0);
//            }
//            len = idx1 - idx0;
//            byte[] src = data();
//            byte[] dst = target.data();
//            System.arraycopy(src, idx0, dst, odx0, len);
//            return len;
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] getBytes(int offset, int len) {
            offset += this.offset;
            byte[] bb = new byte[len];
            int off = 0;
            for (int i = 0; i < offs.length; i++) {
                if (offset > offs[i][1]) {
                    continue;
                }
                if (offset >= offs[i][0]) {
                    int o = offset - offs[i][0];
                    int c = Math.min(len, offs[i][1] - offset);
                    if (c > 0) {
                        byte[] bx = set[i].getBytes(o, c);
                        System.arraycopy(bx, 0, bb, off, c);
                        offset += c;
                        off += c;
                        len -= c;
                    } else {
                        break;
                    }
                }
            }
            return bb;
        }

        @Override
        public void setBytes(int offset, byte[] buf, int off, int len) {
            offset += this.offset;
            for (int i = 0; i < offs.length; i++) {
                if (offset > offs[i][1]) {
                    continue;
                }
                if (offset >= offs[i][0]) {
                    int o = offset - offs[i][0];
                    int c = Math.min(len, offs[i][1] - offset);
                    if (c > 0) {
                        set[i].setBytes(o, buf, off, c);
                        offset += c;
                        off += c;
                        len -= c;
                    } else {
                        break;
                    }
                }
            }
        }

        @Override
        public String getString(int offset, int length, Charset charset) {
            offset += this.offset;
            try {
                if (charset == null) {
                    charset = Charset.forName("ISO-8859-1");
                }
                return new String(getBytes(offset, length), charset);
            } catch (Throwable th) {
                System.err.println("Failed to read string: " + th);
                return null;
            }
        }

        @Override
        public void setByte(int offset, byte b) {
            offset += this.offset;
            for (int i = 0; i < offs.length; i++) {
                if (offset > offs[i][1]) {
                    continue;
                }
                if (offset >= offs[i][0] && offset <= offs[i][1]) {
                    set[i].setByte(offset - offs[i][0], b);
                    break;
                }
            }
            throw new ArrayIndexOutOfBoundsException("Tried to set byte " + b + " at " + (this.offset + offset) + ", while max is " + maxOffset);
        }

        @Override
        public byte getByte(int offset) {
            offset += this.offset;
            for (int i = 0; i < offs.length; i++) {
                if (offset > offs[i][1]) {
                    continue;
                }
                if (offset >= offs[i][0] && offset <= offs[i][1]) {
                    return set[i].getByte(offset - offs[i][0]);
                }
            }
            throw new ArrayIndexOutOfBoundsException("Tried to get byte at " + (this.offset + offset) + ", while max is " + maxOffset);
        }

        @Override
        public void setSize(int size) {
            if (offset + size < _maxOffset) {
                maxOffset = offset + size - 1;
            }
        }

        @Override
        public int getMaxMaxOffset() {
            return _maxOffset;
        }

        @Override
        public ByteArray getByteArray(int offset, int len) {
            offset += this.offset;
            List<ByteArray> lst = new ArrayList<ByteArray>();
            boolean direct = false;
            for (int i = 0; i < offs.length; i++) {
                if (offset > offs[i][1]) {
                    continue;
                }
                if (offset >= offs[i][0] && offset <= offs[i][1]) {
                    int o = offset - offs[i][0];
                    int c = Math.min(len, offs[i][1] - offset);
                    if (c == set[i].getSize()) {
                        lst.add(set[i]);
                        direct = true;
                    } else {
                        lst.add(set[i].getByteArray(o, c));
                    }
                    offset += c;
                    len -= c;
                }
                if (len <= 0) {
                    break;
                }
            }
            if (lst.size() == 1) {
                ByteArray ba = lst.get(0);
                if (direct) {
                    return ba.getByteArray(0, ba.getSize());
                } else {
                    return ba;
                }
            } else {
                return new XByteArray(lst.toArray(new ByteArray[lst.size()]));
            }
        }
    }

    /**
     * Provides mapping of name types to data structures and data size.
     * Implements default set of types that may be extended by overriding it.
     */
    public static class ByteArrayNamedType {

        public <T> T getValueAt(ByteArray ba, int offset, int length, String type) {
            int len = getValueSizeForType(type);
            int typeLen = len;
            boolean isArray = false;
            if (type.contains("[")) {
                isArray = true;
                typeLen = getValueSizeForType(type.substring(0, type.indexOf("[")));
            } else if ("bytes".equals(type)) {
                isArray = true;
                typeLen = 1;
            } else if (type.startsWith("string") || type.startsWith("cstring")) {
                isArray = true;
                len = length;
            } else if (len != length) {
                System.err.println("Possibly invalid data type: requested data length is " + length + ", data type length is " + len);
            }
            type = type.toLowerCase();
            if (len == 0) {
                return null;
            }
            if (type.startsWith("byte")) {
                if (isArray) {
                    return (T) ba.getBytes(offset, length);
                } else {
                    return (T) (Object) ba.getByte(offset);
                }
            } else if (type.startsWith("ubyte")) {
                if (isArray) {
                    return (T) ba.getUBytes(offset, length);
                } else {
                    return (T) (Object) ba.getUByte(offset);
                }
            } else if (type.startsWith("short")) {
                if (isArray) {
                    return (T) ba.getShorts(offset, length);
                } else {
                    return (T) (Object) ba.getShort(offset);
                }
            } else if (type.startsWith("ushort")) {
                if (isArray) {
                    return (T) ba.getUShorts(offset, length);
                } else {
                    return (T) (Object) ba.getUShort(offset);
                }
            } else if (type.startsWith("int")) {
                if (isArray) {
                    return (T) ba.getInts(offset, length);
                } else {
                    return (T) (Object) ba.getInt(offset);
                }
            } else if (type.startsWith("uint")) {
                if (isArray) {
                    return (T) ba.getUInts(offset, length);
                } else {
                    return (T) (Object) ba.getUInt(offset);
                }
            } else if (type.startsWith("int3")) {
                if (isArray) {
                    return (T) ba.getInt3s(offset, length);
                } else {
                    return (T) (Object) ba.getInt3(offset);
                }
            } else if (type.startsWith("long")) {
                if (isArray) {
                    return (T) ba.getLongs(offset, length);
                } else {
                    return (T) (Object) ba.getLong(offset);
                }
//        } else if ("bytes".equals(type) || type.startsWith("byte[")) {
//            return (T) getByteArray(offset, len);
            } else if (type.startsWith("string") && type.contains("/")) {
                String subtype = type.substring(type.indexOf("/") + 1);
                if (subtype.contains("[")) {
                    subtype = subtype.substring(0, subtype.indexOf("["));
                }
                if ("BCD".equalsIgnoreCase(subtype)) {
                    return (T) ba.getTBDICString(offset, len);
                } else if ("DSS".equalsIgnoreCase(subtype)) {
                    return (T) ba.getDotSeparatedString(offset, len);
                } else {
                    return (T) ba.getString(offset, len, Charset.forName(subtype));
                }
            } else if (type.startsWith("string")) {
                return (T) ba.getString(offset, len, null);
            } else if (type.startsWith("cstring")) {
                int max = 0;
                for (int i = 0; i < len; i++) {
                    if (ba.getByte(offset + i) != 0) {
                        max = i;
                    } else {
                        max++;
                        break;
                    }
                }
                return (T) ba.getString(offset, max, null);
            } else if (type.startsWith("block")) {
                if (isArray) {
                    return (T) ba.getBlocks(offset, length, typeLen);
                } else {
                    return (T) ba.getBytes(offset, typeLen);
                }
            } else {
                System.err.println("Warning: unrecognized named type [" + type + "]. Return as bytes.");
                return (T) ba.getByteArray(offset, len);
            }
        }

        /**
         * Returns size of data for scalar or for array if contains "[<number>]"
         * pattern.
         *
         * Returns -1 for dynamic-length data (e.g. array defined with "[]",
         * "bytes", "string". Returns 0 for unknown/undefined or zero-length
         * array definitions.
         *
         * @param type
         * @return
         */
        public int getValueSizeForType(String type) {
            if (type == null) {
                return 0;
            }
            type = type.toLowerCase();
            if (type.contains("[")) {
                type = type.substring(type.indexOf("[") + 1, type.length() - 1);
                try {
                    if (type.isEmpty()) {
                        return -1;
                    }
                    return Integer.parseInt(type);
                } catch (Throwable th) {
                    return 0;
                }
            } else if (type.equals("bytes")) {
                return -1; // dynamic size bytes array
            } else if (type.startsWith("byte") || type.startsWith("ubyte")) {
                return 1;
            } else if (type.startsWith("short") || type.startsWith("ushort")) {
                return 2;
            } else if (type.startsWith("int3")) {
                return 3;
            } else if (type.startsWith("int") || type.startsWith("uint")) {
                return 4;
            } else if (type.startsWith("long")) {
                return 4;
            } else if (type.startsWith("string")) {
                return -1;
            } else if (type.startsWith("cstring")) {
                return -1;
            } else if (type.startsWith("block")) {
                try {
                    return Integer.parseInt(type.substring(5));
                } catch (Throwable th) {
                    return 0;
                }
            }
            return 0;
        }
    }
}
