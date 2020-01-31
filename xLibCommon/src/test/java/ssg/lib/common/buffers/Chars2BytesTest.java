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
package ssg.lib.common.buffers;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author 000ssg
 */
public class Chars2BytesTest {

    public Chars2BytesTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of clone method, of class Chars2Bytes.
     */
    @Test
    public void testClone() throws Exception {
        System.out.println("clone");
        Chars2Bytes instance = new Chars2Bytes();
        instance.write(CharBuffer.wrap("aaa"));
        Chars2Bytes result = (Chars2Bytes) instance.clone();
        assertEquals(instance.encoding, result.encoding);
        assertEquals(instance.eof, result.eof);
        assertEquals(instance.lengthIn, result.lengthIn);
        assertEquals(instance.lengthOut, result.lengthOut);
        assertEquals(instance.size, result.size);
        assertEquals(instance.buffer, result.buffer);
        assertEquals(instance.data, result.data);
        assertEquals(instance.encoder, result.encoder);
    }

    /**
     * Test of reset method, of class Chars2Bytes.
     */
    @Test
    public void testReset() throws Exception {
        System.out.println("reset");
        Chars2Bytes instance = new Chars2Bytes();
        instance.write(CharBuffer.wrap("aaa"));
        instance.flush();
        assertEquals(3, instance.lengthIn);
        assertEquals(3, instance.lengthOut);
        assertEquals(3, instance.size);
        instance.reset();
        assertEquals(0, instance.lengthIn);
        assertEquals(0, instance.lengthOut);
        assertEquals(0, instance.size);
        assertEquals(0, instance.buffer.flip().remaining());
        assertEquals(0, instance.data.size());
    }

    /**
     * Test of getSourceLength method, of class Chars2Bytes.
     */
    @Test
    public void testGetSourceLength() throws Exception {
        System.out.println("getSourceLength");
        Chars2Bytes instance = new Chars2Bytes();
        assertEquals(0, instance.getSourceLength());
        instance.write(CharBuffer.wrap("aaa"));
        assertEquals(3, instance.getSourceLength());
        instance.write(CharBuffer.wrap("bb"));
        assertEquals(5, instance.getSourceLength());
        instance.reset();
        assertEquals(0, instance.getSourceLength());
    }

    /**
     * Test of getTargetLength method, of class Chars2Bytes.
     */
    @Test
    public void testGetTargetLength() throws Exception {
        System.out.println("getTargetLength");
        Chars2Bytes instance = new Chars2Bytes();
        assertEquals(0, instance.getSourceLength());
        instance.write(CharBuffer.wrap("aaa"));
        assertEquals(0, instance.getTargetLength());
        instance.flush();
        assertEquals(3, instance.getTargetLength());
        instance.write(CharBuffer.wrap("bb"));
        assertEquals(3, instance.getTargetLength());
        instance.flush();
        assertEquals(5, instance.getTargetLength());
        instance.reset();
        assertEquals(0, instance.getTargetLength());
    }

    /**
     * Test of getAvailable method, of class Chars2Bytes.
     */
    @Test
    public void testGetAvailable() throws Exception {
        System.out.println("getAvailable");
        Chars2Bytes instance = new Chars2Bytes();
        assertEquals(0, instance.getAvailable());
        instance.write(CharBuffer.wrap("aaa"));
        assertEquals(0, instance.getAvailable());
        instance.flush();
        assertEquals(3, instance.getAvailable());
        instance.read(1);
        assertEquals(2, instance.getAvailable());
        instance.write(CharBuffer.wrap("1"));
        instance.flush();
        instance.write(CharBuffer.wrap("bb"));
        assertEquals(3, instance.getAvailable());
        instance.flush();
        assertEquals(5, instance.getAvailable());
        instance.reset();
        assertEquals(0, instance.getAvailable());
    }

    /**
     * Test of isOpen method, of class Chars2Bytes.
     */
    @Test
    public void testIsOpen() throws Exception {
        System.out.println("isOpen");
        Chars2Bytes instance = new Chars2Bytes();
        assertEquals(true, instance.isOpen());
        instance.close();
        assertEquals(false, instance.isOpen());
        instance.reset();
        assertEquals(true, instance.isOpen());
    }

    /**
     * Test of write method, of class Chars2Bytes.
     */
    @Test
    public void testWrite() throws Exception {
        System.out.println("write");
        Chars2Bytes instance = new Chars2Bytes();
        instance.write(CharBuffer.wrap("Hel"), CharBuffer.wrap("lo"));
        assertEquals(5, instance.getSourceLength());
        assertEquals(0, instance.getTargetLength());
        instance.write(null);
        assertEquals(5, instance.getSourceLength());
        assertEquals(0, instance.getTargetLength());
        instance.write(CharBuffer.allocate(0));
        assertEquals(5, instance.getSourceLength());
        assertEquals(0, instance.getTargetLength());
        instance.flush();
        assertEquals(5, instance.getSourceLength());
        assertEquals(5, instance.getTargetLength());
    }

    /**
     * Test of flush method, of class Chars2Bytes.
     */
    @Test
    public void testFlush() throws Exception {
        System.out.println("flush");
        Chars2Bytes instance = new Chars2Bytes();
        instance.write(CharBuffer.wrap("Hel"), CharBuffer.wrap("lo"));
        assertEquals(5, instance.getSourceLength());
        assertEquals(0, instance.getTargetLength());
        instance.write(null);
        assertEquals(5, instance.getSourceLength());
        assertEquals(0, instance.getTargetLength());
        instance.write(CharBuffer.allocate(0));
        assertEquals(5, instance.getSourceLength());
        assertEquals(0, instance.getTargetLength());
        instance.flush();
        assertEquals(5, instance.getSourceLength());
        assertEquals(5, instance.getTargetLength());
    }

    /**
     * Test of close method, of class Chars2Bytes.
     */
    @Test
    public void testClose() throws Exception {
        System.out.println("close");
        Chars2Bytes instance = new Chars2Bytes();
        instance.write(CharBuffer.wrap("Hel"), CharBuffer.wrap("lo"));
        assertEquals(5, instance.getSourceLength());
        assertEquals(0, instance.getTargetLength());
        instance.write(null);
        assertEquals(5, instance.getSourceLength());
        assertEquals(0, instance.getTargetLength());
        instance.write(CharBuffer.allocate(0));
        assertEquals(5, instance.getSourceLength());
        assertEquals(0, instance.getTargetLength());
        instance.flush();
        assertEquals(5, instance.getSourceLength());
        assertEquals(5, instance.getTargetLength());
        instance.write(CharBuffer.wrap(" W"), CharBuffer.wrap("orld"));
        assertEquals(11, instance.getSourceLength());
        assertEquals(5, instance.getTargetLength());
        instance.close();
        assertEquals(11, instance.getSourceLength());
        assertEquals(11, instance.getTargetLength());
    }

    /**
     * Test of read method, of class Chars2Bytes.
     */
    @Test
    public void testRead_ByteBuffer() throws Exception {
        System.out.println("read");
        Chars2Bytes instance = new Chars2Bytes();
        {
            ByteBuffer bb = ByteBuffer.allocate(10);
            assertEquals(0, instance.read(bb));
            bb.clear();
            instance.write(CharBuffer.wrap("0123456789"));
            assertEquals(10, instance.read(bb));
            bb.clear();
            instance.flush();
            assertEquals(0, instance.read(bb));
            bb.clear();
            instance.write(CharBuffer.wrap("10123456789"));
            assertEquals(10, instance.read(bb));
            bb.clear();
            assertEquals(1, instance.read(bb));
            bb.clear();
        }

        {
            instance.reset();
            instance.flushOnDemand = false;
            ByteBuffer bb = ByteBuffer.allocate(10);
            assertEquals(0, instance.read(bb));
            bb.clear();
            instance.write(CharBuffer.wrap("0123456789"));
            assertEquals(0, instance.read(bb));
            bb.clear();
            instance.flush();
            assertEquals(10, instance.read(bb));
            bb.clear();
            instance.write(CharBuffer.wrap("10123456789"));
            instance.flush();
            assertEquals(10, instance.read(bb));
            bb.clear();
            assertEquals(1, instance.read(bb));
            bb.clear();
        }
    }

    /**
     * Test of read method, of class Chars2Bytes.
     */
    @Test
    public void testRead_int() throws Exception {
        System.out.println("read");
        Chars2Bytes instance = new Chars2Bytes();
        {
            ByteBuffer bb = ByteBuffer.allocate(10);
            assertEquals(0, instance.read(bb));
            bb.clear();
            instance.write(CharBuffer.wrap("0123456789"));
            assertEquals(10, instance.read(bb));
            bb.clear();
            instance.flush();
            assertEquals(0, instance.read(bb));
            bb.clear();
            instance.write(CharBuffer.wrap("10123456789"));
            assertEquals(10, instance.read(bb));
            bb.clear();
            assertEquals(1, instance.read(bb));
            bb.clear();
        }

        {
            instance.reset();
            instance.flushOnDemand = false;
            assertEquals(0, instance.read(10).remaining());
            instance.write(CharBuffer.wrap("0123456789"));
            assertEquals(0, instance.read(10).remaining());
            instance.flush();
            assertEquals(10, instance.read(10).remaining());
            instance.write(CharBuffer.wrap("10123456789"));
            instance.flush();
            assertEquals(10, instance.read(10).remaining());
            assertEquals(1, instance.read(10).remaining());
        }
    }

    /**
     * Test of toString method, of class Chars2Bytes.
     */
    @Test
    public void testToString() throws Exception {
        System.out.println("toString");
        Chars2Bytes instance = new Chars2Bytes();
        instance.write(CharBuffer.wrap("Hello"));
        instance.flush();
        instance.write(CharBuffer.wrap(", world!"));
        System.out.println("  " + instance.toString().replace("\n", "\n"));
    }

    /**
     * Test of main method, of class Chars2Bytes.
     */
    @Test
    public void testFunctional() throws Exception {
        System.out.println("functional");

        BufferConverter<CharBuffer, ByteBuffer> c2b = new Chars2Bytes();
        String text = "Привет";
        byte[] buf = text.getBytes(((Chars2Bytes) c2b).encoding);

        c2b.write(CharBuffer.wrap(text.substring(0, 2)));
        c2b.write(CharBuffer.wrap(text.substring(2)));
        c2b.close();

        ByteBuffer cs = c2b.read(100);
        System.out.println("SRC[" + text.length() + "]: " + text + "\nDST[" + cs.remaining() + "]: " + BufferTools.toText(((Chars2Bytes) c2b).encoding.name(), cs));

        Bytes2Chars b2c = new Bytes2Chars();
        b2c.write(cs);
        b2c.close();
        CharBuffer ccs = b2c.read(100);
        System.out.println("DST[" + ccs.remaining() + "]: " + ccs);
    }

}
