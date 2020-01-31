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

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
public class BufferingDITest {

    public BufferingDITest() {
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
     * Test of providers method, of class BufferingDI.
     */
    @Test
    public void testProviders() throws Exception {
        System.out.println("providers");
        BufferingDI di = new BufferingDI();

        assertEquals(0, di.providers().size());

        // write to different providers
        di.write("1", null);
        assertEquals(1, di.providers().size());

        di.write("2", null);
        assertEquals(2, di.providers().size());

        // delete provider
        di.delete("2");
        assertEquals(1, di.providers().size());

        // write to different providers and remove all
        di.write("2", null);
        assertEquals(2, di.providers().size());
        di.write("3", null);
        assertEquals(3, di.providers().size());
        for (Object p : di.providers()) {
            di.delete(p);
        }
        assertEquals(0, di.providers().size());
    }

    /**
     * Test of delete method, of class BufferingDI.
     */
    @Test
    public void testDelete() throws Exception {
        System.out.println("delete");
        BufferingDI di = new BufferingDI();

        assertEquals(0, di.providers().size());

        // write to different providers
        di.write("1", null);
        assertEquals(1, di.providers().size());

        di.write("2", null);
        assertEquals(2, di.providers().size());

        // delete provider
        di.delete("2");
        assertEquals(1, di.providers().size());

        // write to different providers and remove all
        di.write("2", null);
        assertEquals(2, di.providers().size());
        di.write("3", null);
        assertEquals(3, di.providers().size());
        for (Object p : di.providers()) {
            di.delete(p);
        }
        assertEquals(0, di.providers().size());
    }

    /**
     * Test of size method, of class BufferingDI.
     */
    @Test
    public void testSize() {
        System.out.println("size");
        Buffer data = null;
        Buffer data1 = ByteBuffer.wrap("AA".getBytes());
        Buffer[] data2 = new Buffer[]{
            ByteBuffer.wrap("AA".getBytes()),
            ByteBuffer.wrap("BB".getBytes())
        };
        BufferingDI<Buffer, Object> di = new BufferingDI<>();
        assertEquals(0, di.size(Collections.singletonList(data)));
        assertEquals(2, di.size(Collections.singletonList(data1)));
        assertEquals(4, di.size(Arrays.asList(data2)));
    }

    /**
     * Test of consume method, of class BufferingDI.
     */
    @Test
    public void testConsume() throws Exception {
        System.out.println("consume");
        Object provider = null;
        Collection[] data = null;
        BufferingDI di = new BufferingDI();
        di.consume(provider, data);
        assertEquals(null, di.fetch(provider));

        data = new Collection[]{new ArrayList() {
            {
                add(ByteBuffer.wrap("1".getBytes()));
            }
        }};
        di.consume(provider, data);
        assertEquals(1, di.fetch(provider).size());

        data = new Collection[]{
            new ArrayList() {
                {
                    add(ByteBuffer.wrap("1".getBytes()));
                    add(ByteBuffer.wrap("22".getBytes()));
                    add(ByteBuffer.wrap("333".getBytes()));
                }
            },
            new ArrayList() {
                {
                    add(ByteBuffer.wrap("22".getBytes()));
                    add(ByteBuffer.wrap("333".getBytes()));
                }
            }
        };
        di.consume(provider, data);
        assertEquals(5, di.fetch(provider).size());
    }

    /**
     * Test of produce method, of class BufferingDI.
     */
    @Test
    public void testProduce() throws Exception {
        System.out.println("produce");
        Object provider = null;
        Collection[] data = null;
        BufferingDI di = new BufferingDI();
        di.push(provider, data);
        assertEquals(null, di.produce(provider));

        data = new Collection[]{new ArrayList() {
            {
                add(ByteBuffer.wrap("1".getBytes()));
            }
        }};
        di.push(provider, data);
        assertEquals(1, di.produce(provider).size());

        data = new Collection[]{
            new ArrayList() {
                {
                    add(ByteBuffer.wrap("1".getBytes()));
                    add(ByteBuffer.wrap("22".getBytes()));
                    add(ByteBuffer.wrap("333".getBytes()));
                }
            },
            new ArrayList() {
                {
                    add(ByteBuffer.wrap("22".getBytes()));
                    add(ByteBuffer.wrap("333".getBytes()));
                }
            }
        };
        di.push(provider, data);
        assertEquals(5, di.produce(provider).size());
    }

    /**
     * Test of getBuffer method, of class BufferingDI.
     */
    @Test
    public void testGetBuffer() {
        System.out.println("getBuffer");
        Object provider = null;
        BufferingDI di = new BufferingDI();
        assertEquals(0, di.providers().size());
        assertNotNull(di.getBuffer(provider, true));
        assertEquals(1, di.providers().size());
        assertNotNull(di.getBuffer(provider, false));
        assertEquals(1, di.providers().size());
    }

    /**
     * Test of push method, of class BufferingDI.
     */
    @Test
    public void testPush() throws Exception {
        System.out.println("push");
        Object provider = null;
        Collection[] data = null;
        BufferingDI di = new BufferingDI();
        di.push(provider, data);
        assertEquals(null, di.produce(provider));

        data = new Collection[]{new ArrayList() {
            {
                add(ByteBuffer.wrap("1".getBytes()));
            }
        }};
        di.push(provider, data);
        assertEquals(1, di.produce(provider).size());

        data = new Collection[]{
            new ArrayList() {
                {
                    add(ByteBuffer.wrap("1".getBytes()));
                    add(ByteBuffer.wrap("22".getBytes()));
                    add(ByteBuffer.wrap("333".getBytes()));
                }
            },
            new ArrayList() {
                {
                    add(ByteBuffer.wrap("22".getBytes()));
                    add(ByteBuffer.wrap("333".getBytes()));
                }
            }
        };
        di.push(provider, data);
        assertEquals(5, di.produce(provider).size());
    }

    /**
     * Test of fetch method, of class BufferingDI.
     */
    @Test
    public void testFetch() throws Exception {
        System.out.println("fetch");
        Object provider = null;
        Collection[] data = null;
        BufferingDI di = new BufferingDI();
        di.consume(provider, data);
        assertEquals(null, di.fetch(provider));

        data = new Collection[]{new ArrayList() {
            {
                add(ByteBuffer.wrap("1".getBytes()));
            }
        }};
        di.consume(provider, data);
        assertEquals(1, di.fetch(provider).size());

        data = new Collection[]{
            new ArrayList() {
                {
                    add(ByteBuffer.wrap("1".getBytes()));
                    add(ByteBuffer.wrap("22".getBytes()));
                    add(ByteBuffer.wrap("333".getBytes()));
                }
            },
            new ArrayList() {
                {
                    add(ByteBuffer.wrap("22".getBytes()));
                    add(ByteBuffer.wrap("333".getBytes()));
                }
            }
        };
        di.consume(provider, data);
        assertEquals(5, di.fetch(provider).size());
    }

}
