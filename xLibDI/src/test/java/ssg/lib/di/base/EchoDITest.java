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
package ssg.lib.di.base;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
public class EchoDITest {

    public EchoDITest() {
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
     * Test of providers method, of class EchoDI.
     */
    @Test
    public void testProviders() throws Exception {
        System.out.println("providers");
        EchoDI echo = new EchoDIImpl();
        assertEquals(0, echo.providers().size());

        echo.write("1", null);
        assertEquals(1, echo.providers().size());
        echo.write("2", null);
        assertEquals(2, echo.providers().size());
        echo.write("2", null);
        assertEquals(2, echo.providers().size());
        echo.write("3", null);
        assertEquals(3, echo.providers().size());

        echo.delete("3");
        assertEquals(2, echo.providers().size());

        for (Object p : echo.providers()) {
            echo.delete(p);
        }
        assertEquals(0, echo.providers().size());
    }

    /**
     * Test of delete method, of class EchoDI.
     */
    @Test
    public void testDelete() throws Exception {
        System.out.println("delete");
        EchoDI echo = new EchoDIImpl();
        assertEquals(0, echo.providers().size());

        echo.write("1", null);
        assertEquals(1, echo.providers().size());
        echo.write("2", null);
        assertEquals(2, echo.providers().size());
        echo.write("2", null);
        assertEquals(2, echo.providers().size());
        echo.write("3", null);
        assertEquals(3, echo.providers().size());

        echo.delete("3");
        assertEquals(2, echo.providers().size());

        for (Object p : echo.providers()) {
            echo.delete(p);
        }
        assertEquals(0, echo.providers().size());
    }

    /**
     * Test of size method, of class EchoDI.
     */
    @Test
    public void testSize() {
        System.out.println("size");
        List<Object> data = null;
        List data1 = Collections.singletonList("AA");
        List data2 = new ArrayList() {
            {
                add("AA");
                add("BB");
            }
        };
        EchoDI di = new EchoDIImpl();
        assertEquals(0, di.size(data));
        assertEquals(1, di.size(data1));
        assertEquals(2, di.size(data2));
    }

    /**
     * Test of consume method, of class EchoDI.
     */
    @Test

    public void testConsume() throws Exception {
        System.out.println("consume");
        Collection[] data = null;
        Object provider = null;
        EchoDI di = new EchoDIImpl();
        di.consume(provider, data);
        assertEquals(null, di.read(provider));

        data = new Collection[]{new ArrayList() {
            {
                add("1");
            }
        }};
        di.consume(provider, data);
        assertEquals(1, di.read(provider).size());

        data = new Collection[]{
            new ArrayList() {
                {
                    add("1");
                    add("22");
                    add("333");
                }
            },
            new ArrayList() {
                {
                    add("22");
                    add("333");
                }
            }
        };
        di.consume(provider, data);
        assertEquals(5, di.read(provider).size());
    }

    /**
     * Test of produce method, of class EchoDI.
     */
    @Test
    public void testProduce() throws Exception {
        System.out.println("produce");
        testConsume();
    }

    /**
     * Test of echo method, of class EchoDI.
     */
    @Test
    public void testEcho() {
        System.out.println("echo");
        Collection[] data = null;
        EchoDI di = new EchoDIImpl();
        assertEquals(null, di.echo(data));

        data = new Collection[]{new ArrayList() {
            {
                add("1");
            }
        }};
        assertEquals(1, di.echo(null, data).size());

        data = new Collection[]{
            new ArrayList() {
                {
                    add("1");
                    add("22");
                    add("333");
                }
            },
            new ArrayList() {
                {
                    add("22");
                    add("333");
                }
            }
        };
        assertEquals(5, di.echo(null, data).size());
    }

    public class EchoDIImpl extends EchoDI<Object, Object> {

        public List<Object> echo(Object provider, Collection<Object>[] data) {
            if (data == null || data.length == 0 || data[0] == null) {
                return null;
            }
            List<Object> r = new ArrayList<>();
            for (Collection ds : data) {
                if (ds != null) {
                    r.addAll(ds);
                }
            }
            return r;
        }
    }

}
