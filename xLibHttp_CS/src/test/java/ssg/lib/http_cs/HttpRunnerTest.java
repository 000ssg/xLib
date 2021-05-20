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
package ssg.lib.http_cs;

import java.io.IOException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import ssg.lib.http.HttpApplication;

/**
 *
 * @author 000ssg
 */
public class HttpRunnerTest {

    public HttpRunnerTest() {
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
     * Test of stop method, of class HttpRunner.
     */
    @Test
    public void testStop() throws Exception {
        System.out.println("stop");
        HttpRunner instance = new HttpRunner();
        instance.configureHttp(0);
        instance.start();
        assertTrue(instance.isRunning());
        instance.stop();
        assertFalse(instance.isRunning());
    }

    /**
     * Test of start method, of class HttpRunner.
     */
    @Test
    public void testStart() throws Exception {
        System.out.println("start");
        HttpRunner instance = new HttpRunner();
        assertFalse(instance.isRunning());
        instance.configureHttp(0);
        instance.start();
        assertTrue(instance.isRunning());
        instance.stop();
        assertFalse(instance.isRunning());
    }

    /**
     * Test of initHttp method, of class HttpRunner.
     */
    @Test
    public void testInitHttp() {
        System.out.println("initHttp");
        HttpRunner instance = new HttpRunner();
        assertNull(instance.getService());
        instance.initHttp();
        assertNotNull(instance.getService());
    }

    /**
     * Test of getHttpPort method, of class HttpRunner.
     */
    @Test
    public void testGetHttpPort() {
        System.out.println("getHttpPort");
        HttpRunner instance = new HttpRunner();
        assertNull(instance.getHttpPort());
        instance.configureHttp(20000);
        assertEquals((Integer) 20000, instance.getHttpPort());
        instance.configureHttp(21000);
        assertEquals((Integer) 21000, instance.getHttpPort());
        instance.configureHttp(null);
        assertNull(instance.getHttpPort());
    }

    /**
     * Test of configureHttp method, of class HttpRunner.
     */
    @Test
    public void testConfigureHttp() throws Exception {
        System.out.println("configureHttp");
        HttpRunner instance = new HttpRunner();
        assertNull(instance.getHttpPort());
        instance.configureHttp(20000);
        assertEquals((Integer) 20000, instance.getHttpPort());
        instance.configureHttp(21000);
        assertEquals((Integer) 21000, instance.getHttpPort());
        instance.configureHttp(null);
        assertNull(instance.getHttpPort());
    }

    /**
     * Test of configureREST method, of class HttpRunner.
     */
    @Test
    public void testConfigureREST() throws Exception {
        System.out.println("configureREST");
        String path = "/r";
        HttpRunner instance = new HttpRunner();
        assertNull(instance.getREST());
        instance.configureREST(path);
        assertNotNull(instance.getREST());
    }

    /**
     * Test of onStarted method, of class HttpRunner.
     */
    @Test
    public void testOnStarted() throws Exception {
        System.out.println("onStarted");
        final boolean[] probe = {false, false, false, false};
        HttpRunner instance = new HttpRunner() {
            @Override
            public void onStarted() throws IOException {
                probe[0] = true;
                super.onStarted();
                probe[1] = true;
            }

            @Override
            public void onStopping() throws IOException {
                probe[2] = true;
                super.onStopping();
                probe[3] = true;
            }
        };
        instance.configureHttp(0);
        instance.start();
        assertTrue(probe[0]);
        assertTrue(probe[1]);
        assertFalse(probe[2]);
        assertFalse(probe[3]);
        instance.stop();
        assertTrue(probe[2]);
        assertTrue(probe[3]);
    }

    /**
     * Test of onStopping method, of class HttpRunner.
     */
    @Test
    public void testOnStopping() throws Exception {
        System.out.println("onStopping");
        final boolean[] probe = {false, false, false, false};
        HttpRunner instance = new HttpRunner() {
            @Override
            public void onStarted() throws IOException {
                probe[0] = true;
                super.onStarted();
                probe[1] = true;
            }

            @Override
            public void onStopping() throws IOException {
                probe[2] = true;
                super.onStopping();
                probe[3] = true;
            }
        };
        instance.configureHttp(0);
        instance.start();
        assertTrue(probe[0]);
        assertTrue(probe[1]);
        assertFalse(probe[2]);
        assertFalse(probe[3]);
        instance.stop();
        assertTrue(probe[2]);
        assertTrue(probe[3]);
    }

    /**
     * Test of getApp method, of class HttpRunner.
     */
    @Test
    public void testGetApp() {
        System.out.println("getApp");
        HttpRunner instance = new HttpRunner();
        assertNull(instance.getApp());
        HttpApplication app = new HttpApplication();
        instance = new HttpRunner(app);
        assertEquals(app, instance.getApp());
    }

    /**
     * Test of getREST method, of class HttpRunner.
     */
    @Test
    public void testGetREST() throws Exception {
        System.out.println("getREST");
        String path = "/r";
        HttpRunner instance = new HttpRunner();
        assertNull(instance.getREST());
        instance.configureREST(path);
        assertNotNull(instance.getREST());
    }

    /**
     * Test of getService method, of class HttpRunner.
     */
    @Test
    public void testGetService() throws Exception {
        System.out.println("getService");
        HttpRunner instance = new HttpRunner();
        assertNull(instance.getService());
        instance.configureREST("/r");
        assertNotNull(instance.getService());
    }

}
