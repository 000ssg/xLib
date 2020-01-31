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
import ssg.lib.common.buffers.BufferTools;

/**
 *
 * @author 000ssg
 */
public class CommonToolsTest {

    public CommonToolsTest() {
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
     * Test of scanUniqueSubsequences method, of class CommonTools.
     */
    @Test
    public void testScanUniqueSubsequences_byteArrArrArr_byteArr() throws Exception {
        System.out.println("scanUniqueSubsequences");
        byte[][][] startend = new byte[][][]{
            {"${".getBytes(), "}".getBytes()},
            {"#{".getBytes(), "}".getBytes()},
            {"&{".getBytes(), "}".getBytes()}
        };

        for (Object[] oo : new Object[][]{
            {
                "Hello, ${Hello}, #{Hll}, &{Hi}".getBytes(),
                new byte[][][]{
                    {"${Hello}".getBytes()},
                    {"#{Hll}".getBytes()},
                    {"&{Hi}".getBytes()}
                }
            },
            {
                "Hello, ${Hello}, #{Hll}".getBytes(),
                new byte[][][]{
                    {"${Hello}".getBytes()},
                    {"#{Hll}".getBytes()},
                    null
                }
            },
            {
                "Hello, ${Hello #{Hll}}".getBytes(),
                new byte[][][]{
                    {"${Hello #{Hll}".getBytes()},
                    null,
                    null
                }
            },
            null
        }) {
            if (oo == null) {
                continue;
            }
            byte[] data = (byte[]) oo[0];
            byte[][][] expResult = (byte[][][]) oo[1];
            byte[][][] result = CommonTools.scanUniqueSubsequences(startend, data);
            String re = BufferTools.dump(expResult);
            String r = BufferTools.dump(result);
            System.out.println("---------\nSample: " + new String(data)
                    + "\nExp : " + re.replace("\n", "\n      ")
                    + "\nAct : " + r.replace("\n", "\n      ")
                    + "\n-----------------"
            );
            assertArrayEquals(expResult, result);
        }
    }

    @Test
    public void testWait() throws Exception {
        System.out.println("wait");

        
        
        CommonTools.wait(100, () -> System.currentTimeMillis() % 2 == 0);
    }

}
