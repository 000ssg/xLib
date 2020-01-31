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
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import ssg.lib.common.Huffman;
import ssg.lib.common.SmallHuffman;

/**
 *
 * @author sesidoro
 */
public class ByteBufferPipeHuffmanUnpackTest {

    public ByteBufferPipeHuffmanUnpackTest() {
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
     * Test of write method, of class ByteBufferPipeHuffmanUnpack.
     */
    @Test
    public void testWrite() throws Exception {
        System.out.println("write");
        Huffman huff = new SmallHuffman();
        byte[] buf = ("01010101" + "22222222").getBytes();
        ByteBuffer[] bbs = new ByteBuffer[]{
            ByteBuffer.wrap(buf, 0, buf.length / 2),
            ByteBuffer.wrap(buf, buf.length / 2, buf.length - buf.length / 2)
        };
        Huffman.Encoder enc = huff.getEncoder();
        Huffman.Decoder dec = huff.getDecoder();
        enc.add(buf);
        byte[] buf2 = enc.getValue();
        dec.add(buf2);
        byte[] buf3 = dec.getValue();

        ByteBufferPipeHuffmanUnpack instance = new ByteBufferPipeHuffmanUnpack(huff.getDecoder());
        instance.write(ByteBuffer.wrap(buf2, 0, buf2.length / 2));
        ByteBuffer bb = instance.read(1024);
        assertEquals(0, bb.remaining());
        instance.write(ByteBuffer.wrap(buf2, buf2.length / 2, buf2.length - buf2.length / 2));
        instance.close();

        bb = instance.read(1024);
        assertEquals(buf3.length, bb.remaining());

        byte[] buf4 = new byte[bb.remaining()];
        bb.get(buf4);
        assertArrayEquals(buf, buf4);
    }

    /**
     * Test of close method, of class ByteBufferPipeHuffmanUnpack.
     */
    @Test
    public void testClose() throws Exception {
        System.out.println("close");
        Huffman huff = new SmallHuffman();
        byte[] buf = ("01010101" + "22222222").getBytes();
        ByteBuffer[] bbs = new ByteBuffer[]{
            ByteBuffer.wrap(buf, 0, buf.length / 2),
            ByteBuffer.wrap(buf, buf.length / 2, buf.length - buf.length / 2)
        };
        Huffman.Encoder enc = huff.getEncoder();
        Huffman.Decoder dec = huff.getDecoder();
        enc.add(buf);
        byte[] buf2 = enc.getValue();
        dec.add(buf2);
        byte[] buf3 = dec.getValue();

        ByteBufferPipeHuffmanUnpack instance = new ByteBufferPipeHuffmanUnpack(huff.getDecoder());
        instance.write(ByteBuffer.wrap(buf2));
        instance.close();

        ByteBuffer bb = instance.read(1024);
        assertEquals(buf3.length, bb.remaining());
    }

    /**
     * Test of getDecoder method, of class ByteBufferPipeHuffmanUnpack.
     */
    @Test
    public void testGetDecoder() {
        System.out.println("getDecoder");
        Huffman.Decoder dec = new SmallHuffman().getDecoder();
        ByteBufferPipeHuffmanUnpack instance = new ByteBufferPipeHuffmanUnpack();
        assertEquals(null, instance.getDecoder());
        instance.setDecoder(dec);
        assertEquals(dec, instance.getDecoder());
    }

    /**
     * Test of setEncoder method, of class ByteBufferPipeHuffmanUnpack.
     */
    @Test
    public void testSetEncoder() {
        System.out.println("setEncoder");
        Huffman.Decoder dec = new SmallHuffman().getDecoder();
        ByteBufferPipeHuffmanUnpack instance = new ByteBufferPipeHuffmanUnpack();
        assertEquals(null, instance.getDecoder());
        instance.setDecoder(dec);
        assertEquals(dec, instance.getDecoder());
    }

}
