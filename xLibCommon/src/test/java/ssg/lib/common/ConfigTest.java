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
package ssg.lib.common;

import java.util.Arrays;
import java.util.List;
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
public class ConfigTest {

    public ConfigTest() {
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
     * Test of getBase method, of class Config.
     */
    @Test
    public void testGetBase() {
        System.out.println("getBase");
        // default implicit
        Config instance = new Config(null);
        String expResult = instance.getClass().getSimpleName();
        String result = instance.getBase();
        assertEquals(expResult, result);

        // default implicit anonymous!
        instance = new Config(null) {
        };
        expResult = instance.getClass().getSuperclass().getName();
        result = instance.getBase();
        assertEquals(expResult, result);

        // explicit
        instance = new Config("io");
        expResult = "io";
        result = instance.getBase();
        assertEquals(expResult, result);
    }

    /**
     * Test of load method, of class Config.
     */
    @Test
    public void testLoad() {
        System.out.println("load");
        //System.out.println(System.getProperties().keySet().toString().replace(",", "\n"));
        Config config = new Config("user") {
            public String name;
            public String script;
            public String language;
            public String country;
            public String language_format;
            public String country_format;
            public String variant;
            //public String home;
            //public String dir;

        };
        String[] args = null;
        Config.load(config, args);
        System.out.println("User config[" + config.getBase() + ", " + config.toMap(true).size() + "]:\n  " + config.toMap(true).toString().replace(",", "\n  ")
                + "\n  --- other[" + config.other().size() + "]\n  " + config.other().toString().replace(",", "\n  "));
        config = new Config("java");
        Config.load(config, args);
        System.out.println("Java config[" + config.getBase() + ", " + config.toMap(true).size() + "]:\n  " + config.toMap(true).toString().replace(",", "\n  ")
                + "\n  --- other[" + config.other().size() + "]\n  " + config.other().toString().replace(",", "\n  "));
        config = new Config("os");
        Config.load(config, args);
        System.out.println("OS config[" + config.getBase() + ", " + config.toMap(true).size() + "]:\n  " + config.toMap(true).toString().replace(",", "\n  ")
                + "\n  --- other[" + config.other().size() + "]\n  " + config.other().toString().replace(",", "\n  "));

        config = new Config("a") {
            public String aa;
            public String[] bb;
            public List<String> cc;
        };
        Config.load(config, new String[]{
            "a.aa=A",
            "a.aa=B",
            "a.bb=A",
            "a.bb=B",
            "a.bb=C",
            "a.bb=B",
            "a.bb=B",
            "a.cc=A",
            "a.cc=B",
            "a.cc=C"
        });
        System.out.println("AA config[" + config.getBase() + ", " + config.toMap(true).size() + "]:\n  " + config.toMap(true).toString().replace(",", "\n  ")
                + "\n  --- other[" + config.other().size() + "]\n  " + config.other().toString().replace(",", "\n  "));
        String[] ss= config.get("bb");
        System.out.println("  bb -> "+Arrays.asList(ss).toString());
    }

}
