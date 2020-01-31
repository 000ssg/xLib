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
public class WildcardMatcherTest {

    public WildcardMatcherTest() {
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
     * Test of match method, of class WildcardMatcher.
     */
    @Test
    public void testMatch() {
        System.out.println("match");

        // [wildcard][match = 1][match = 0]
        for (String[][] data : new String[][][]{
            {{null}, {}, {}},
            {{"*"}, {"a", "aa", ""}, {}},
            {{"/a/*/b"}, {"/a/a/b", "/a/b/b"}, {"a/a/b", "/b/a/a/b"}},
            {{"*/a/*/b"}, {"/a/a/b", "/a/b/b", "a/a//b", "/b/a/a/b"}, {"/b/a/a/bb"}},
            {{"*/a/*/b*"}, {"/a/a/b", "/a/b/b", "a/a//b", "/b/a/a/b", "/b/a/a/bb"}, {}},
            {{"/a/?/b"}, {"/a/a/b", "/a/b/b"}, {"a/a/b", "/b/a/a/b"}},}) {
            WildcardMatcher instance = new WildcardMatcher(data[0][0]);
            //instance.DEBUG=true;
            System.out.println("  " + instance);
            for (String m1 : data[1]) {
                float f = instance.match(m1);
                System.out.println("   + " + f + "\t" + m1);
                assertEquals(1f, f, 0);
            }
            for (String m0 : data[2]) {
                float f = instance.match(m0);
                System.out.println("   - " + f + "\t" + m0);
                assertEquals(0f, f, 0);
            }
        }
    }

    /**
     * Test of weight method, of class WildcardMatcher.
     */
    @Test
    public void testWeight() {
        System.out.println("weight");
        WildcardMatcher instance = new WildcardMatcher(null);
        float expResult = 1.0F;
        float result = instance.weight();
        assertEquals(expResult, result, 0.0);
    }

}
