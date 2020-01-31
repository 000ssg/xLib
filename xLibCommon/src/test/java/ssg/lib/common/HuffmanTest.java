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

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
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
public class HuffmanTest {
    
    public HuffmanTest() {
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
     * Test of getDictionary method, of class Huffman.
     */
    @Test
    public void testGetDictionary() {
        System.out.println("getDictionary");
        Huffman instance = new HuffmanImpl();
        Map result = instance.getDictionary();
        assertEquals(11, result.size());
    }

    /**
     * Test of getRoot method, of class Huffman.
     */
    @Test
    public void testGetRoot() {
        System.out.println("getRoot");
        Huffman instance = new HuffmanImpl();
        Huffman.HTree result = instance.getRoot();
        assertNotNull(result);
        assertNull(result.value);
        assertNotNull(result.zero);
        assertNotNull(result.one);
        assertEquals(0, result.level);
        assertEquals(1, result.zero.level);
        assertEquals(1, result.one.level);
    }

    /**
     * Test of getEncoder method, of class Huffman.
     */
    @Test
    public void testGetEncoder() {
        System.out.println("getEncoder");
        Huffman instance = new HuffmanImpl();
        Huffman.Encoder result = instance.getEncoder();
        assertNotNull(result);
    }

    /**
     * Test of getDecoder method, of class Huffman.
     */
    @Test
    public void testGetDecoder() {
        System.out.println("getDecoder");
        Huffman instance = new HuffmanImpl();
        Huffman.Decoder result = instance.getDecoder();
        assertNotNull(result);
    }

    /**
     * Test of buildTree method, of class Huffman.
     */
    @Test
    public void testBuildTree() throws Exception {
        System.out.println("buildTree");
        SmallHuffman huff = new SmallHuffman();
        Collection<Integer> consumed = new HashSet<Integer>();
        Huffman.HTree result = Huffman.buildTree(null, null, huff.getDictionary(), consumed);
        assertEquals(huff.root.toString(), result.toString());

        Huffman.Encoder enc = huff.getEncoder();
        Huffman.Decoder dec = huff.getDecoder();

        for (String testText : new String[]{
            "0000000000"
        }) {
            enc.reset();
            dec.reset();

            System.out.println("\nSOURCE[" + testText.getBytes("ISO-8859-1").length + "]: " + testText);
            if (huff.DEBUG) {
                for (byte b : testText.getBytes("ISO-8859-1")) {
                    System.out.print(Integer.toBinaryString(0xFF & b) + "");
                }
                System.out.println();
            }

            enc.add(testText.getBytes());
            enc.close();

            byte[] bb = enc.getValue();
            System.out.println("ENCODED[" + bb.length + "]");
            if (huff.DEBUG) {
                for (int i = 0; i < bb.length; i++) {
                    System.out.print(Integer.toBinaryString(0xFF & bb[i]) + "");
                }
                System.out.println();
                for (int i = 0; i < bb.length; i++) {
                    System.out.print(Integer.toHexString(0xFF & bb[i]) + "");
                    if (i % 2 == 0 && i > 0) {
                        System.out.print(" ");
                    }
                }
                System.out.println();
            }

            dec.add(bb);
            dec.close();

            byte[] bb2 = dec.getValue();
            System.out.println("DECODED[" + bb2.length + "]");

            System.out.println("RESULT: " + new String(bb2, "ISO-8859-1"));
            System.out.println("Matches: " + testText.equals(new String(bb2, "ISO-8859-1")));

        }
    }

    public static class HuffmanImpl extends SmallHuffman {
    }
}
