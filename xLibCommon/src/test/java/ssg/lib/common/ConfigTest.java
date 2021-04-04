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

import java.net.URI;
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
 * @author 000ssg
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
        config = Config.load(new Config("java"), args);
        System.out.println("Java config[" + config.getBase() + ", " + config.toMap(true).size() + "]:\n  " + config.toMap(true).toString().replace(",", "\n  ")
                + "\n  --- other[" + config.other().size() + "]\n  " + config.other().toString().replace(",", "\n  "));
        config = Config.load(new Config("os"), args);
        System.out.println("OS config[" + config.getBase() + ", " + config.toMap(true).size() + "]:\n  " + config.toMap(true).toString().replace(",", "\n  ")
                + "\n  --- other[" + config.other().size() + "]\n  " + config.other().toString().replace(",", "\n  "));

        config = Config.load(new Config("a") {
            public String aa;
            public String[] bb;
            public List<String> cc;
        }, new String[]{
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
        String[] ss = config.get("bb");
        System.out.println("  bb -> " + Arrays.asList(ss).toString());

        config = Config.load(new Config(""), args);
        System.out.println("Any config[" + config.getBase() + ", " + config.toMap(true).size() + "]:\n  " + config.toMap(true).toString().replace(",", "\n  ")
                + "\n  --- other[" + config.other().size() + "]\n  " + config.other().toString().replace(",", "\n  "));
    }

    @Test
    public void testLoad1() {
        System.out.println("load1");

        Config conf = new Config("") {
            public V1 v1;
            public List<V2> v2;

            @Override
            public String toString() {
                return "{" 
                        + "\n  v1=" + v1 
                        + "\n  v2=" + v2 
                        + '}';
            }

        };

        conf = Config.load(conf, new String[]{
            "v1={'a':'aaa', 'ii': [1,2,3], 'll':[4,7], 'lv2': [{'ss':['aa','bb']},{'ss':['cc','dd'], 'uri':['http://aaa','http://bbb']}]}"
        });
        System.out.println(conf);
    }

    public static class V1 {

        public String a;
        public List<Integer> ii;
        public Long[] ll;
        public List<V2> lv2;

        @Override
        public String toString() {
            return "V1{" + "a=" + a + ", ii=" + ii + ", ll=" + (ll != null ? Arrays.asList(ll) : "<none>") + ", lv2=" + lv2 + '}';
        }
    }

    public static class V2 {

        public String[] ss;
        public List<URI> uri;

        @Override
        public String toString() {
            return "V2{" + "ss=" + (ss != null ? Arrays.asList(ss) : "<none>") +", uri="+uri+ '}';
        }
    }
}
