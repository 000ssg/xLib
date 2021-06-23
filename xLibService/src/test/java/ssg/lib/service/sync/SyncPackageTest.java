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
package ssg.lib.service.sync;

import java.math.BigInteger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import ssg.lib.common.ByteArray;
import ssg.lib.service.sync.SyncItem.SyncType;
import ssg.lib.service.sync.SyncItem.SyncUpdate;

/**
 *
 * @author sesidoro
 */
public class SyncPackageTest {

    public SyncPackageTest() {
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
     * Test of value2bytes method, of class SyncPackage.
     */
    @Test
    public void testValue2bytes() throws Exception {
        System.out.println("value2bytes");
        SyncItem item = new SyncItem(SyncType.domain, "d-a", System.currentTimeMillis(), BigInteger.valueOf(19));
        SyncPackage instance = new SyncPackage(item, SyncPackage.OP_CREATE);
        byte[] expResult = "19".getBytes();
        byte[] result = instance.value2bytes(item);
        assertArrayEquals(expResult, result);
    }

    /**
     * Test of getIterable method, of class SyncPackage.
     */
    @Test
    public void testGetIterable() throws Exception {
        System.out.println("getIterable");
        SyncItem item = testDomain();
        char operation = SyncPackage.OP_CREATE;
        Long timestamp = null;
        Iterable<SyncPackage> result = SyncPackage.getIterable(item, operation, timestamp);
        System.out.println("Test domain:\n  " + item.dump().replace("\n", "\n  "));
        for (SyncPackage p : result) {
            System.out.println("P: " + new String(p.header, 0, 2) + " " + new String(p.id)+"\t\t"+new ByteArray(p.header).getLong(2));
        }
        long last = item.getTimestamp();
        Thread.sleep(2);
        SyncItem sa = item.find("Item-A-B");
        sa.setValue("333");
        assertTrue(last < item.getTimestamp());
        assertTrue(sa.getTimestamp() == item.getTimestamp());
        result = SyncPackage.getIterable(item, SyncPackage.OP_UPDATE, last);
        System.out.println("--- after modifying Item-A-B: "+last);
        for (SyncPackage p : result) {
            System.out.println("P: " + new String(p.header, 0, 2) + " " + new String(p.id)+"\t\t"+new ByteArray(p.header).getLong(2));
        }
        //
        Thread.sleep(2);
        long last2=sa.getTimestamp();
        SyncItem sc = item.find("Group-C");
        sc.setValue(Math.PI*Math.E);
        result = SyncPackage.getIterable(item, SyncPackage.OP_UPDATE, last);
        System.out.println("--- after modifying Item-A-B and Group-C: "+last);
        for (SyncPackage p : result) {
            System.out.println("P: " + new String(p.header, 0, 2) + " " + new String(p.id)+"\t\t"+new ByteArray(p.header).getLong(2));
        }
        
        result = SyncPackage.getIterable(item, SyncPackage.OP_UPDATE, last2);
        System.out.println("--- after modifying Group-C: "+last2);
        for (SyncPackage p : result) {
            System.out.println("P: " + new String(p.header, 0, 2) + " " + new String(p.id)+"\t\t"+new ByteArray(p.header).getLong(2));
        }
        
        sc.add(new SyncItem(SyncType.item,"Item-C-A",System.currentTimeMillis(),"asdfg"));
        result = SyncPackage.getIterable(item, SyncPackage.OP_UPDATE, last2);
        System.out.println("--- after modifying Group-C and adding Item-C-A: "+last2);
        for (SyncPackage p : result) {
            System.out.println("P: " + new String(p.header, 0, 2) + " " + new String(p.id)+"\t\t"+new ByteArray(p.header).getLong(2));
        }

        System.out.println("\nTest domain (finally):\n  " + item.dump().replace("\n", "\n  "));
    }

    public static SyncItem testDomain() {
        long timestamp = System.currentTimeMillis();
        SyncItem root = new SyncItem(SyncType.domain, "Domain-A", timestamp);
        SyncItem g1 = new SyncItem(SyncType.group, "Group-A", timestamp, "Descr group A.");
        SyncItem g2 = new SyncItem(SyncType.group, "Group-B", timestamp, "Descr group B.");
        SyncItem g3 = new SyncItem(SyncType.group, "Group-C", timestamp, Math.PI);
        SyncUpdate gu1 = root.add(g1);
        SyncUpdate gu2 = root.add(g2);
        SyncUpdate gu3 = root.add(g3);
        SyncItem i11 = new SyncItem(SyncType.item, "Item-A-A", timestamp, Math.E);
        SyncItem i12 = new SyncItem(SyncType.item, "Item-A-B", timestamp, Math.PI);
        SyncItem i21 = new SyncItem(SyncType.item, "Item-B-A", timestamp, true);
        SyncUpdate iu1 = g1.add(i11);
        SyncUpdate iu2 = g1.add(i12);
        SyncUpdate iu3 = g2.add(i21);

        return root;
    }

}
