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

import java.nio.Buffer;
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
 * @author sesidoro
 */
public class Bytes2CharsTest {

    public Bytes2CharsTest() {
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
     * Test of clone method, of class Bytes2Chars.
     */
    @Test
    public void testClone() throws Exception {
        System.out.println("clone");
        Bytes2Chars instance = new Bytes2Chars();
        instance.write(ByteBuffer.wrap("aaa".getBytes()));
        Bytes2Chars result = (Bytes2Chars) instance.clone();
        assertEquals(instance.encoding, result.encoding);
        assertEquals(instance.eof, result.eof);
        assertEquals(instance.lengthIn, result.lengthIn);
        assertEquals(instance.lengthOut, result.lengthOut);
        assertEquals(instance.size, result.size);
        assertEquals(instance.buffer, result.buffer);
        assertEquals(instance.data, result.data);
        assertEquals(instance.decoder, result.decoder);
        assertEquals(instance.reminder, result.reminder);
    }

    /**
     * Test of reset method, of class Bytes2Chars.
     */
    @Test
    public void testReset() throws Exception {
        System.out.println("reset");
        Bytes2Chars instance = new Bytes2Chars();
        instance.write(ByteBuffer.wrap("aaa".getBytes()));
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
     * Test of getSourceLength method, of class Bytes2Chars.
     */
    @Test
    public void testGetSourceLength() throws Exception {
        System.out.println("getSourceLength");
        Bytes2Chars instance = new Bytes2Chars();
        assertEquals(0, instance.getSourceLength());
        instance.write(ByteBuffer.wrap("aaa".getBytes()));
        assertEquals(3, instance.getSourceLength());
        instance.write(ByteBuffer.wrap("bb".getBytes()));
        assertEquals(5, instance.getSourceLength());
        instance.reset();
        assertEquals(0, instance.getSourceLength());
    }

    /**
     * Test of getTargetLength method, of class Bytes2Chars.
     */
    @Test
    public void testGetTargetLength() throws Exception {
        System.out.println("getTargetLength");
        Bytes2Chars instance = new Bytes2Chars();
        assertEquals(0, instance.getSourceLength());
        instance.write(ByteBuffer.wrap("aaa".getBytes()));
        assertEquals(0, instance.getTargetLength());
        instance.flush();
        assertEquals(3, instance.getTargetLength());
        instance.write(ByteBuffer.wrap("bb".getBytes()));
        assertEquals(3, instance.getTargetLength());
        instance.flush();
        assertEquals(5, instance.getTargetLength());
        instance.reset();
        assertEquals(0, instance.getTargetLength());
    }

    /**
     * Test of getAvailable method, of class Bytes2Chars.
     */
    @Test
    public void testGetAvailable() throws Exception {
        System.out.println("getAvailable");
        Bytes2Chars instance = new Bytes2Chars();
        assertEquals(0, instance.getAvailable());
        instance.write(ByteBuffer.wrap("aaa".getBytes()));
        assertEquals(0, instance.getAvailable());
        instance.flush();
        assertEquals(3, instance.getAvailable());
        instance.read(1);
        assertEquals(2, instance.getAvailable());
        instance.write(ByteBuffer.wrap("1".getBytes()));
        instance.flush();
        instance.write(ByteBuffer.wrap("bb".getBytes()));
        assertEquals(3, instance.getAvailable());
        instance.flush();
        assertEquals(5, instance.getAvailable());
        instance.reset();
        assertEquals(0, instance.getAvailable());
    }

    /**
     * Test of isOpen method, of class Bytes2Chars.
     */
    @Test
    public void testIsOpen() throws Exception {
        System.out.println("isOpen");
        Bytes2Chars instance = new Bytes2Chars();
        assertEquals(true, instance.isOpen());
        instance.close();
        assertEquals(false, instance.isOpen());
        instance.reset();
        assertEquals(true, instance.isOpen());
    }

    /**
     * Test of write method, of class Bytes2Chars.
     */
    @Test
    public void testWrite() throws Exception {
        System.out.println("write");
        Bytes2Chars instance = new Bytes2Chars();
        instance.write(ByteBuffer.wrap("Hel".getBytes()), ByteBuffer.wrap("lo".getBytes()));
        assertEquals(5, instance.getSourceLength());
        assertEquals(0, instance.getTargetLength());
        instance.write(null);
        assertEquals(5, instance.getSourceLength());
        assertEquals(0, instance.getTargetLength());
        instance.write(ByteBuffer.allocate(0));
        assertEquals(5, instance.getSourceLength());
        assertEquals(0, instance.getTargetLength());
        instance.flush();
        assertEquals(5, instance.getSourceLength());
        assertEquals(5, instance.getTargetLength());
    }

    /**
     * Test of flush method, of class Bytes2Chars.
     */
    @Test
    public void testFlush() throws Exception {
        System.out.println("flush");
        Bytes2Chars instance = new Bytes2Chars();
        instance.write(ByteBuffer.wrap("Hel".getBytes()), ByteBuffer.wrap("lo".getBytes()));
        assertEquals(5, instance.getSourceLength());
        assertEquals(0, instance.getTargetLength());
        instance.write(null);
        assertEquals(5, instance.getSourceLength());
        assertEquals(0, instance.getTargetLength());
        instance.write(ByteBuffer.allocate(0));
        assertEquals(5, instance.getSourceLength());
        assertEquals(0, instance.getTargetLength());
        instance.flush();
        assertEquals(5, instance.getSourceLength());
        assertEquals(5, instance.getTargetLength());
    }

    /**
     * Test of close method, of class Bytes2Chars.
     */
    @Test
    public void testClose() throws Exception {
        System.out.println("close");
        Bytes2Chars instance = new Bytes2Chars();
        instance.write(ByteBuffer.wrap("Hel".getBytes()), ByteBuffer.wrap("lo".getBytes()));
        assertEquals(5, instance.getSourceLength());
        assertEquals(0, instance.getTargetLength());
        instance.write(null);
        assertEquals(5, instance.getSourceLength());
        assertEquals(0, instance.getTargetLength());
        instance.write(ByteBuffer.allocate(0));
        assertEquals(5, instance.getSourceLength());
        assertEquals(0, instance.getTargetLength());
        instance.flush();
        assertEquals(5, instance.getSourceLength());
        assertEquals(5, instance.getTargetLength());
        instance.write(ByteBuffer.wrap(" W".getBytes()), ByteBuffer.wrap("orld".getBytes()));
        assertEquals(11, instance.getSourceLength());
        assertEquals(5, instance.getTargetLength());
        instance.close();
        assertEquals(11, instance.getSourceLength());
        assertEquals(11, instance.getTargetLength());
    }

    /**
     * Test of read method, of class Bytes2Chars.
     */
    @Test
    public void testRead_CharBuffer() throws Exception {
        System.out.println("read");
        Bytes2Chars instance = new Bytes2Chars();

        {
            CharBuffer bb = CharBuffer.allocate(10);
            assertEquals(0, instance.read(bb));
            ((Buffer) bb).clear();
            instance.write(ByteBuffer.wrap("0123456789".getBytes()));
            assertEquals(10, instance.read(bb));
            ((Buffer) bb).clear();
            instance.flush();
            assertEquals(0, instance.read(bb));
            ((Buffer) bb).clear();
            instance.write(ByteBuffer.wrap("10123456789".getBytes()));
            assertEquals(10, instance.read(bb));
            ((Buffer) bb).clear();
            assertEquals(1, instance.read(bb));
            ((Buffer) bb).clear();
        }

        {
            instance.reset();
            instance.flushOnDemand = false;
            CharBuffer bb = CharBuffer.allocate(10);
            assertEquals(0, instance.read(bb));
            ((Buffer) bb).clear();
            instance.write(ByteBuffer.wrap("0123456789".getBytes()));
            assertEquals(0, instance.read(bb));
            ((Buffer) bb).clear();
            instance.flush();
            assertEquals(10, instance.read(bb));
            ((Buffer) bb).clear();
            instance.write(ByteBuffer.wrap("10123456789".getBytes()));
            instance.flush();
            assertEquals(10, instance.read(bb));
            ((Buffer) bb).clear();
            assertEquals(1, instance.read(bb));
            ((Buffer) bb).clear();
        }
    }

    /**
     * Test of read method, of class Bytes2Chars.
     */
    @Test
    public void testRead_int() throws Exception {
        System.out.println("read");
        Bytes2Chars instance = new Bytes2Chars();
        {
            CharBuffer bb = CharBuffer.allocate(10);
            assertEquals(0, instance.read(bb));
            ((Buffer) bb).clear();
            instance.write(ByteBuffer.wrap("0123456789".getBytes()));
            assertEquals(10, instance.read(bb));
            ((Buffer) bb).clear();
            instance.flush();
            assertEquals(0, instance.read(bb));
            ((Buffer) bb).clear();
            instance.write(ByteBuffer.wrap("10123456789".getBytes()));
            assertEquals(10, instance.read(bb));
            ((Buffer) bb).clear();
            assertEquals(1, instance.read(bb));
            ((Buffer) bb).clear();
        }

        {
            instance.reset();
            instance.flushOnDemand = false;
            assertEquals(0, instance.read(10).remaining());
            instance.write(ByteBuffer.wrap("0123456789".getBytes()));
            assertEquals(0, instance.read(10).remaining());
            instance.flush();
            assertEquals(10, instance.read(10).remaining());
            instance.write(ByteBuffer.wrap("10123456789".getBytes()));
            instance.flush();
            assertEquals(10, instance.read(10).remaining());
            assertEquals(1, instance.read(10).remaining());
        }
    }

    /**
     * Test of toString method, of class Bytes2Chars.
     */
    @Test
    public void testToString() throws Exception {
        System.out.println("toString");
        Bytes2Chars instance = new Bytes2Chars();
        instance.write(ByteBuffer.wrap("Hello".getBytes()));
        instance.flush();
        instance.write(ByteBuffer.wrap(", world!".getBytes()));
        System.out.println("  " + instance.toString().replace("\n", "\n  "));
    }

    /**
     * Test of main method, of class Bytes2Chars.
     */
    @Test
    public void testFunctional() throws Exception {
        System.out.println("functional");
        String text = "Привет";

        Bytes2Chars b2c = new Bytes2Chars();
        byte[] buf = text.getBytes(b2c.encoding);
        b2c.write(ByteBuffer.wrap(buf, 0, 3));
        b2c.write(ByteBuffer.wrap(buf, 3, buf.length - 3));
        b2c.close();

        CharBuffer cs = b2c.read(100);
        System.out.println("SRC[" + text.length() + "]: " + text + "\nDST[" + cs.remaining() + "]: " + cs);
    }

}
