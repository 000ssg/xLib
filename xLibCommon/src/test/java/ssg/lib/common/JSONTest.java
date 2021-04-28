/*
 * The MIT License
 *
 * Copyright 2021 000ssg.
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
import java.util.ArrayList;
import java.util.HashMap;
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
public class JSONTest {

    public JSONTest() {
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
     * Test of isObject method, of class JSON.
     */
    @Test
    public void testIsObject() {
        System.out.println("isObject");
        Object o = "";
        JSON instance = new JSON();
        boolean expResult = false;
        boolean result = instance.isObject(o);
        assertEquals(expResult, result);
    }

    /**
     * Test of isList method, of class JSON.
     */
    @Test
    public void testIsList() {
        System.out.println("isList");
        Object o = new HashMap();
        Object o1 = new ArrayList();
        JSON instance = new JSON();
        assertEquals(false, instance.isList(o));
        assertEquals(true, instance.isList(o1));
    }

    /**
     * Test of getEncoding method, of class JSON.
     */
    @Test
    public void testGetEncoding() {
        System.out.println("getEncoding");
        JSON instance = new JSON();
        String expResult = "UTF-8";
        String result = instance.getEncoding();
        assertEquals(expResult, result);
    }

    /**
     * Test of setEncoding method, of class JSON.
     */
    @Test
    public void testSetEncoding() {
        System.out.println("setEncoding");
        String encoding = "ISO-8859-1";
        JSON instance = new JSON();
        instance.setEncoding(encoding);
    }

    @Test
    public void testF1() throws IOException {
        System.out.println("functional 1");
        for (String text : new String[]{
            "{a:'a', b:'b'}",
            "{'a':'a', 'b':'b', 'C':'c'}"
        }) {
            JSON.Decoder jd = new JSON.Decoder();
            Map map = jd.readObject(text, Map.class);
            System.out.println(text + " -> " + map);
        }
    }
}
