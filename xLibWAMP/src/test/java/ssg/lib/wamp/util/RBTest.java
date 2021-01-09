/*
 * The MIT License
 *
 * Copyright 2021 sesidoro.
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
package ssg.lib.wamp.util;

import ssg.lib.wamp.util.RB;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static ssg.lib.wamp.util.RB.NAME;
import static ssg.lib.wamp.util.RB.TYPE;
import ssg.lib.wamp.util.WAMPTools;

/**
 *
 * @author sesidoro
 */
public class RBTest {

    public RBTest() {
    }

    @org.junit.jupiter.api.BeforeAll
    public static void setUpClass() throws Exception {
    }

    @org.junit.jupiter.api.AfterAll
    public static void tearDownClass() throws Exception {
    }

    @org.junit.jupiter.api.BeforeEach
    public void setUp() throws Exception {
    }

    @org.junit.jupiter.api.AfterEach
    public void tearDown() throws Exception {
    }

//    @BeforeAll
//    public static void setUpClass() {
//    }
//    
//    @AfterAll
//    public static void tearDownClass() {
//    }
//    
//    @BeforeEach
//    public void setUp() {
//    }
//    
//    @AfterEach
//    public void tearDown() {
//    }
    /**
     * Test of data method, of class RB.
     */
    @org.junit.jupiter.api.Test
    public void testData() {
        System.out.println("data");
        RB instance = RB.root("a", "a-a");
        Map<String, Object> expResult = WAMPTools.createDict(TYPE, "a", NAME, "a-a");
        Map<String, Object> result = instance.data();
        assertEquals(expResult, result);
    }

    /**
     * Test of toString method, of class RB.
     */
    @org.junit.jupiter.api.Test
    public void testToString_0args() {
        System.out.println("toString");
        RB instance = RB.root("a", "a-a");
        String expResult = "{\n  type: a\n  name: a-a\n}";
        String result = instance.toString();
        assertEquals(expResult, result);
    }

    /**
     * Test of toString method, of class RB.
     */
    @org.junit.jupiter.api.Test
    public void testToString_Object() {
        System.out.println("toString");
        RB instance = RB.root("a", "a-a");
        String expResult = "{\n  type: a\n  name: a-a\n}";
        String result = instance.toString(instance.data());
        assertEquals(expResult, result);
    }

    /**
     * Test of procedure method, of class RB.
     */
    @org.junit.jupiter.api.Test
    public void testProcedure_RB() {
        System.out.println("procedure");
        RB rb = RB.procedure("aaa").parameter(0, "pa", "String", false);
        RB instance = RB.root("a", "a-a");
        String expResult = "{\n"
                + "  type: a\n"
                + "  name: a-a\n"
                + "  proc: {\n"
                + "    aaa: [\n"
                + "      {\n"
                + "        type: procedure\n"
                + "        parameters: [\n"
                + "          {\n"
                + "            type: String\n"
                + "            optional: false\n"
                + "            name: pa\n"
                + "            order: 0\n"
                + "          }\n"
                + "        ]\n"
                + "      }\n"
                + "    ]\n"
                + "  }\n"
                + "}";
        RB result = instance.procedure(rb);
        assertEquals(expResult, result.toString());
    }

    /**
     * Test of element method, of class RB.
     */
    @org.junit.jupiter.api.Test
    public void testElement() {
        System.out.println("element");
        RB rb = RB.root("x", "x-x");
        RB instance = RB.root("a", "a-a");
        String expResult = "{\n"
                + "  type: a\n"
                + "  name: a-a\n"
                + "  x: {\n"
                + "    x-x: {\n"
                + "      type: x\n"
                + "    }\n"
                + "  }\n"
                + "}";
        RB result = instance.element(rb);
        assertEquals(expResult, result.toString());
    }

    /**
     * Test of parameter method, of class RB.
     */
    @org.junit.jupiter.api.Test
    public void testParameter() {
        System.out.println("parameter");
        String expResult = "{\n"
                + "  type: procedure\n"
                + "  name: aaa\n"
                + "  parameters: [\n"
                + "    {\n"
                + "      type: string\n"
                + "      optional: false\n"
                + "      order: 0\n"
                + "    }\n"
                + "  ]\n"
                + "}";
        RB instance = RB.procedure("aaa");
        RB result = instance.parameter(0, null, "string", false);
        assertEquals(expResult, result.toString());
    }

    /**
     * Test of returns method, of class RB.
     */
    @org.junit.jupiter.api.Test
    public void testReturns() {
        System.out.println("returns");
        String expResult = "{\n"
                + "  type: function\n"
                + "  name: aaa\n"
                + "  parameters: [\n"
                + "    {\n"
                + "      type: string\n"
                + "      optional: false\n"
                + "      order: 0\n"
                + "    }\n"
                + "  ]\n"
                + "  returns: float\n"
                + "}";
        RB instance = RB.function("aaa");
        instance.parameter(0, null, "string", false);
        RB result = instance.returns("float");
        assertEquals(expResult, result.toString());
    }

    /**
     * Test of root method, of class RB.
     */
    @org.junit.jupiter.api.Test
    public void testRoot() {
        System.out.println("root");
        String type = "a";
        String name = "a-a";
        Map expResult = WAMPTools.createDict(TYPE, type, NAME, name);
        RB result = RB.root(type, name);
        assertEquals(expResult, result.data());
    }

    /**
     * Test of procedure method, of class RB.
     */
    @org.junit.jupiter.api.Test
    public void testProcedure_String() {
        System.out.println("procedure");
        String name = "";
        Map expResult = WAMPTools.createDict(TYPE, "procedure", NAME, name);
        RB result = RB.procedure(name);
        assertEquals(expResult, result.data());
    }

    /**
     * Test of function method, of class RB.
     */
    @org.junit.jupiter.api.Test
    public void testFunction() {
        System.out.println("function");
        String name = "";
        Map expResult = WAMPTools.createDict(TYPE, "function", NAME, name);
        RB result = RB.function(name);
        assertEquals(expResult, result.data());
    }

    /**
     * Test of type method, of class RB.
     */
    @org.junit.jupiter.api.Test
    public void testType() {
        System.out.println(TYPE);
        String name = "";
        Map expResult = WAMPTools.createDict(TYPE, TYPE, NAME, name);
        RB result = RB.type(name);
        assertEquals(expResult, result.data());
    }

    /**
     * Test of error method, of class RB.
     */
    @org.junit.jupiter.api.Test
    public void testError() {
        System.out.println("error");
        String name = "";
        Map expResult = WAMPTools.createDict(TYPE, "error", NAME, name);
        RB result = RB.error(name);
        assertEquals(expResult, result.data());
    }

    /**
     * Test of pub method, of class RB.
     */
    @org.junit.jupiter.api.Test
    public void testPub() {
        System.out.println("pub");
        String name = "";
        Map expResult = WAMPTools.createDict(TYPE, "pub", NAME, name);
        RB result = RB.pub(name);
        assertEquals(expResult, result.data());
    }

}
