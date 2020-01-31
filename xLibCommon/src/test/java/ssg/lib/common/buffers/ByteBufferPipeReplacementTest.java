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
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import ssg.lib.common.Replacement;

/**
 *
 * @author 000ssg
 */
public class ByteBufferPipeReplacementTest {

    public ByteBufferPipeReplacementTest() {
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
     * Test of initReplacement method, of class ByteBufferPipeReplacement.
     */
    @Test
    public void testInitReplacement() throws Exception {
        System.out.println("initReplacement");
        Replacement[] replace = new Replacement[]{
            new Replacement("aaa", "bbb"),
            new Replacement("ccc", "ddd")
        };

        ByteBuffer bb = ByteBuffer.allocate(100);

        // complete
        for (String[] ss : new String[][]{
            {"abcde", "abcde"},
            {"aaabcdeaaa", "bbbbcdebbb"},
            {"aaabcccdeaaa", "bbbbddddebbb"}
        }) {
            ByteBufferPipeReplacement instance = new ByteBufferPipeReplacement();
            instance.initReplacement(replace);
            instance.write(ByteBuffer.wrap(ss[0].getBytes()));
            //instance.close();
            int c = instance.read(bb);
            ((Buffer) bb).flip();
            String rs = BufferTools.toText("UTF-8", bb);
            assertEquals(ss[1], rs);
            for (Replacement r : replace) {
                r.reset();
            }
            ((Buffer) bb).clear();
        }

        // accumulative
        {
            ByteBufferPipeReplacement instance = new ByteBufferPipeReplacement();
            instance.initReplacement(replace);
            for (String[] ss : new String[][]{
                {"aaabcccdeaa", "bbbbdddde"},
                {"abcc", "bbbb"},
                {"cssa", "dddss"}
            }) {
                instance.write(ByteBuffer.wrap(ss[0].getBytes()));
                //instance.close();
                int c = instance.read(bb);
                ((Buffer) bb).flip();
                String rs = BufferTools.toText("UTF-8", bb);
                assertEquals(ss[1], rs);
                ((Buffer) bb).clear();
            }
            instance.close();
            int c = instance.read(bb);
            ((Buffer) bb).flip();
            String rs = BufferTools.toText("UTF-8", bb);
            assertEquals("a", rs);
            ((Buffer) bb).clear();
        }

        // big
        {
            ByteBufferPipeReplacement instance = new ByteBufferPipeReplacement();
            instance.initReplacement(replace);
            String ts = "abcdefghaaabbbcccdddeeefffggghhh";
            instance.write(ByteBuffer.wrap(ts.getBytes()));
            instance.read(bb);
            ((Buffer) bb).flip();

            String tr = BufferTools.toText("UTF-8", bb);
            for (int i = 0; i < 100; i++) {
                ts += ts;
                tr += tr;
                if (ts.length() > 8000) {
                    break;
                }
            }

            bb = ByteBuffer.allocate(13);

            StringBuilder sb = new StringBuilder();
            instance.write(ByteBuffer.wrap(ts.getBytes()));
            int c = 0;
            while ((c = instance.read(bb)) > 0) {
                ((Buffer) bb).flip();
                String rs = BufferTools.toText("UTF-8", bb);
                sb.append(rs);
                ((Buffer) bb).clear();
            }

            instance.close();
            c = instance.read(bb);
            if (c > 0) {
                ((Buffer) bb).flip();
                String rs = BufferTools.toText("UTF-8", bb);
                sb.append(rs);
                ((Buffer) bb).clear();
            }
            assertEquals(tr, sb.toString());
        }
    }

    /**
     * Test of isClosed method, of class ByteBufferPipeReplacement.
     */
    @Test
    public void testIsClosed() {
        System.out.println("isClosed");
    }

    /**
     * Test of fetch method, of class ByteBufferPipeReplacement.
     */
    @Test
    public void testFetch() throws Exception {
        System.out.println("fetch");
    }

    /**
     * Test of getFetchedSize method, of class ByteBufferPipeReplacement.
     */
    @Test
    public void testGetFetchedSize() {
        System.out.println("getFetchedSize");
    }

    /**
     * Test of getProcessedSize method, of class ByteBufferPipeReplacement.
     */
    @Test
    public void testGetProcessedSize() {
        System.out.println("getProcessedSize");
    }

    /**
     * Test of read method, of class ByteBufferPipeReplacement.
     */
    @Test
    public void testRead() throws Exception {
        System.out.println("read");
    }

}
