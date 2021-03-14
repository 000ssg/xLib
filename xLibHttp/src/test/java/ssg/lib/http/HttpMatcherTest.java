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
package ssg.lib.http;

import java.util.LinkedHashMap;
import java.util.Map;
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
public class HttpMatcherTest {

    public HttpMatcherTest() {
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
     * Test of init method, of class HttpMatcher.
     */
    @Test
    public void testInit() {
        System.out.println("init");
        String path = "/aaa/{bbb}/{id}/all/aaa.txt?abcd=efgh&a1=11&b2=22";
        HttpMatcher instance = new HttpMatcher(path);
        instance.init();
        assertEquals(path, instance.path);
        assertEquals(true, instance.absolutePath);
        assertArrayEquals(new String[]{"aaa", "{bbb}", "{id}", "all", "aaa.txt"}, instance.paths);
        assertEquals(2, instance.pathParamCount);
        assertEquals(3, instance.qpm.length);
        assertEquals(true, instance.lastIsFile);
        assertEquals("txt", instance.ext);

        //
        {
            HttpMatcher m = new HttpMatcher("");
            System.out.println(m.toString());
            for (Object[] oo : new Object[][]{
                {"", 0f},
                {"/", 1f},
                {"a", 1f},
                {"/a", 1f},
                {"/b/c", 1f},
                {".a", 1f}
            }) {
                HttpMatcher rm = new HttpMatcher((String) oo[0]);
                float f = m.match(rm);
                System.out.println("  " + f + "\t" + rm.toString().replace("\n", "\n\t"));
                //assertEquals((Float) oo[1], f, 0);
            }
        }

    }

    /**
     * Test of toString method, of class HttpMatcher.
     */
    @Test
    public void testToString() {
        System.out.println("toString");

    }

    /**
     * Test of getParameters method, of class HttpMatcher.
     */
    @Test
    public void testGetParameters() throws Exception {
        System.out.println("getParameters");
        HttpMatcher rm = new HttpMatcher("/AAA/BBB?CCC");
        HttpMatcher instance = new HttpMatcher("/{aaa}/{bbb}");
        Map<String, Object> expResult = new LinkedHashMap<>();
        expResult.put("aaa", "AAA");
        expResult.put("bbb", "BBB");
        Map<String, Object> result = instance.getParameters(rm, true);
        assertEquals(expResult, result);
        expResult.put("CCC", "");
        result = instance.getParameters(rm, false);
        assertEquals(expResult, result);
    }

    /**
     * Test of match method, of class HttpMatcher.
     */
    @Test
    public void testMatch() {
        System.out.println("match");

        {
            // specific cases

            //// extension match
            HttpMatcher m = new HttpMatcher(".txt");
            assertEquals(1f, m.match(new HttpMatcher("/1.txt")), 0);

            m = new HttpMatcher("/.txt");
            assertEquals(1f, m.match(new HttpMatcher("/1.txt")), 0);

            m = new HttpMatcher("/a/.txt");
            assertEquals(0f, m.match(new HttpMatcher("/1.txt")), 0);

            m = new HttpMatcher("/a/bb.txt");
            assertEquals(0f, m.match(new HttpMatcher("/1.txt")), 0);
        }

        // path-based matches: all true
        for (String[] sss : new String[][]{
            {"/", "/", "/aaa?b", "/a/b/"}
        }) {
            HttpMatcher instance = new HttpMatcher(sss[0]);
            System.out.println("Match for: " + instance.path);
            for (int i = 0; i < sss.length; i++) {
                HttpMatcher rm = new HttpMatcher(sss[i]);
                float f = instance.match(rm);
                //f=instance.match(rm);
                System.out.println("  " + f + "\t" + rm.path + "");
                assertEquals(1f, instance.match(rm), 0);
            }
        }

        // path-based matches: all false
        for (String[] sss : new String[][]{
            {"/bbb", "/", "/aaa?b", "/a/b/", ""}
        }) {
            HttpMatcher instance = new HttpMatcher(sss[0]);
            System.out.println("No match for: " + instance.path);
            for (int i = 0; i < sss.length; i++) {
                HttpMatcher rm = new HttpMatcher(sss[i]);
                float f = instance.match(rm);
                f = instance.match(rm);
                System.out.println("  " + f + "\t" + rm.path + "");
                assertEquals((i == 0) ? 1f : 0f, f, 0);
            }
        }
    }

    /**
     * Test of hashCode method, of class HttpMatcher.
     */
    @Test
    public void testHashCode() {
        System.out.println("hashCode: TODO?");
    }

    /**
     * Test of equals method, of class HttpMatcher.
     */
    @Test
    public void testEquals() {
        System.out.println("equals");
        Object obj = "/aaa";
        HttpMatcher instance = new HttpMatcher("/aaa");
        assertEquals(false, instance.equals(obj));
        obj = new HttpMatcher("/aaa");
        assertEquals(true, instance.equals(obj));
        instance.setContentType("aaa");
        assertEquals(false, instance.equals(obj));
        ((HttpMatcher) obj).setContentType("aaa");
        assertEquals(true, instance.equals(obj));
    }

    /**
     * Test of getContentType method, of class HttpMatcher.
     */
    @Test
    public void testGetContentType() {
        System.out.println("getContentType");
        String contentType = "aaa";
        HttpMatcher instance = new HttpMatcher();
        assertNull(instance.getContentType());
        instance.setContentType(contentType);
        assertEquals(contentType, instance.getContentType());
        instance.setContentType(null);
        assertNull(instance.getContentType());
    }

    /**
     * Test of setContentType method, of class HttpMatcher.
     */
    @Test
    public void testSetContentType() {
        System.out.println("setContentType");
        String contentType = "aaa";
        HttpMatcher instance = new HttpMatcher();
        assertNull(instance.getContentType());
        instance.setContentType(contentType);
        assertEquals(contentType, instance.getContentType());
        instance.setContentType(null);
        assertNull(instance.getContentType());
    }

}
