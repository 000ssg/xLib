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
 * @author 000ssg
 */
public class ByteBufferPipeHuffmanPackTest {

    public ByteBufferPipeHuffmanPackTest() {
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
     * Test of write method, of class ByteBufferPipeHuffmanPack.
     */
    @Test
    public void testWrite() throws Exception {
        System.out.println("write");
        byte[] buf = ("01010101" + "22222222").getBytes();
        ByteBuffer[] bbs = new ByteBuffer[]{
            ByteBuffer.wrap(buf, 0, buf.length / 2),
            ByteBuffer.wrap(buf, buf.length / 2, buf.length - buf.length / 2)
        };
        ByteBufferPipeHuffmanPack instance = new ByteBufferPipeHuffmanPack(new SmallHuffman().getEncoder());
        instance.write(bbs);
        ByteBuffer bb = instance.read(1024);
        assertEquals(0, bb.remaining());
        instance.close();
        bb = instance.read(1024);
        assertEquals(6, bb.remaining());
        Huffman.Decoder dec = new SmallHuffman().getDecoder();
        dec.add(bb);
        byte[] buf2 = dec.getValue();
        assertEquals(buf.length, buf2.length);
        assertArrayEquals(buf, buf2);
    }

    /**
     * Test of close method, of class ByteBufferPipeHuffmanPack.
     */
    @Test
    public void testClose() throws Exception {
        System.out.println("close");
        byte[] buf = ("01010101" + "22222222").getBytes();
        ByteBuffer[] bbs = new ByteBuffer[]{
            ByteBuffer.wrap(buf, 0, buf.length / 2),
            ByteBuffer.wrap(buf, buf.length / 2, buf.length - buf.length / 2)
        };
        ByteBufferPipeHuffmanPack instance = new ByteBufferPipeHuffmanPack(new SmallHuffman().getEncoder());
        instance.write(bbs);
        instance.close();
    }

    /**
     * Test of getEncoder method, of class ByteBufferPipeHuffmanPack.
     */
    @Test
    public void testGetEncoder() {
        System.out.println("getEncoder");
        Huffman.Encoder enc = new SmallHuffman().getEncoder();
        ByteBufferPipeHuffmanPack instance = new ByteBufferPipeHuffmanPack();
        assertEquals(null, instance.getEncoder());
        instance.setEncoder(enc);
        assertEquals(enc, instance.getEncoder());
    }

    /**
     * Test of setEncoder method, of class ByteBufferPipeHuffmanPack.
     */
    @Test
    public void testSetEncoder() {
        System.out.println("setEncoder");
        Huffman.Encoder enc = new SmallHuffman().getEncoder();
        ByteBufferPipeHuffmanPack instance = new ByteBufferPipeHuffmanPack();
        assertEquals(null, instance.getEncoder());
        instance.setEncoder(enc);
        assertEquals(enc, instance.getEncoder());
    }

}
