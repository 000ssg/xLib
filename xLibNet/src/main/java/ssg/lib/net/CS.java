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
package ssg.lib.net;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.common.Config;
import ssg.lib.common.net.NetTools;
import ssg.lib.net.CSListener.CSListenerX;
import ssg.lib.net.stat.MCSStatistics;
import ssg.lib.net.stat.RunnerStatistics;

/**
 * CS represents Handler/CSGroup adaptation of MCS.
 *
 * @author 000ssg
 */
public class CS extends MCS {

    List<CSGroup> groups = new ArrayList<>();
    Map<Handler, Boolean> registered = Collections.synchronizedMap(new HashMap<>());

    // listeners
    CSListener[] cslisteners = new CSListener[0];
    CSListenerX[] xcslisteners = new CSListenerX[0];

    public CS() {
    }

    public CS(int acceptors, int dataHandlers) {
        super(acceptors, dataHandlers);
    }

    @Override
    public CS configureHandlers(int acceptors, int dataHandlers) {
        return (CS) super.configureHandlers(acceptors, dataHandlers);
    }

    @Override
    public CS configureStatistics(MCSStatistics stat) {
        return (CS) super.configureStatistics(stat);
    }

    @Override
    public CS configureName(String name) {
        return (CS) super.configureName(name);
    }

    @Override
    public CS configuration(Config... configs) throws IOException {
        super.configuration(configs);
        return this;
    }

    @Override
    public void stop() throws IOException {
        for (CSGroup l : groups) {
            try {
                l.onStop(this);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        if (!registered.isEmpty()) {
            for (Entry<Handler, Boolean> h : registered.entrySet()) {
                if (h.getValue()) {
                    h.setValue(Boolean.FALSE);
                    //h.getKey().unregister(selector);
                }
            }
        }
        super.stop();
        for (CSListener l : cslisteners) {
            l.onStopped(this);
        }
        long timeout = System.currentTimeMillis() + 500;
        while (isRunning() && System.currentTimeMillis() < timeout) {
            NetTools.delay(1);
        }
    }

    @Override
    public void start() throws IOException {
        super.start(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean tryKeyHandling(Runner runner, SelectionKey key, RunnerStatistics stat) throws Exception {
        if (key.attachment() instanceof Handler && !(key.attachment() instanceof ConnectionIO)) {
            if (stat != null) {
                if (key.isAcceptable()) {
                    stat.onAccept();
                } else if (key.isConnectable()) {
                    stat.onConnectable();
                }
            }
            ((Handler) key.attachment()).onHandle(key);
            for (CSListenerX l : xcslisteners) {
                try {
                    l.onHandled(this, key, (Handler) key.attachment());
                } catch (Throwable th) {
                }
            }
            return true;
        } else {
            return super.tryKeyHandling(runner, key, stat);
        }
    }

    //////////////////////////////////////////////////////// CSListener
    @Override
    public void addListener(MCSListener l) {
        super.addListener(l);
        if (l != null) {
            synchronized (cslisteners) {
                if (l instanceof CSListener) {
                    synchronized (cslisteners) {
                        boolean ok = true;
                        for (CSListener li : cslisteners) {
                            if (l.equals(li)) {
                                ok = false;
                                break;
                            }
                        }
                        if (ok) {
                            cslisteners = Arrays.copyOf(cslisteners, cslisteners.length + 1);
                            cslisteners[cslisteners.length - 1] = (CSListener) l;
                        }
                    }
                }
                if (l instanceof CSListenerX) {
                    synchronized (xcslisteners) {
                        boolean ok = true;
                        for (CSListenerX li : xcslisteners) {
                            if (l.equals(li)) {
                                ok = false;
                                break;
                            }
                        }
                        if (ok) {
                            xcslisteners = Arrays.copyOf(xcslisteners, xcslisteners.length + 1);
                            xcslisteners[xcslisteners.length - 1] = (CSListenerX) l;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void removeListener(MCSListener l) {
        super.removeListener(l);
        if (l != null) {
            if (l instanceof CSListener) {
                synchronized (cslisteners) {
                    int idx = -1;
                    for (int i = 0; i < cslisteners.length; i++) {
                        if (l.equals(cslisteners[i])) {
                            idx = i;
                            break;
                        }
                    }
                    if (idx != -1) {
                        for (int i = idx; i < cslisteners.length - 1; i++) {
                            cslisteners[i] = cslisteners[i + 1];
                        }
                        cslisteners = Arrays.copyOf(cslisteners, cslisteners.length - 1);
                    }
                }
            }
            if (l instanceof CSListenerX) {
                synchronized (xcslisteners) {
                    int idx = -1;
                    for (int i = 0; i < xcslisteners.length; i++) {
                        if (l.equals(xcslisteners[i])) {
                            idx = i;
                            break;
                        }
                    }
                    if (idx != -1) {
                        for (int i = idx; i < xcslisteners.length - 1; i++) {
                            xcslisteners[i] = xcslisteners[i + 1];
                        }
                        xcslisteners = Arrays.copyOf(xcslisteners, xcslisteners.length - 1);
                    }
                }
            }
        }
    }
    ///////////////////////////////////////////////////////// Handlers

    public void add(Handler handler) throws IOException {
        if (handler != null) {
            if (isRunning()) {
                handler.register(this);
                registered.put(handler, true);
            } else {
                registered.put(handler, false);
            }
        }
        for (CSListener l : cslisteners) {
            l.onAdded(this, handler);
        }
    }

    public void remove(Handler handler) throws IOException {
        if (handler != null) {
            if (registered.containsKey(handler)) {
                registered.remove(handler);
            }
            handler.unregister(this);
        }
        for (CSListener l : cslisteners) {
            l.onRemoved(this, handler);
        }
    }

    public boolean hasTCPHandler(int port) {
        if (port <= 0 || port > 0xFFFF) {
            return false;
        }
        for (Handler h : registered.keySet()) {
            if (h instanceof TCPHandler) {
                TCPHandler tcp = (TCPHandler) h;
                SocketAddress[] saddrs = tcp.getAddresses();
                if (saddrs != null) {
                    for (SocketAddress saddr : saddrs) {
                        if (NetTools.getPort(saddr) == port) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean hasUDPHandler(int port) {
        if (port <= 0 || port > 0xFFFF) {
            return false;
        }
        for (Handler h : registered.keySet()) {
            if (h instanceof UDPHandler) {
                UDPHandler udp = (UDPHandler) h;
                SocketAddress saddr = udp.getAddress();
                if (NetTools.getPort(saddr) == port) {
                    return true;
                }
            }
        }
        return false;
    }

    ///////////////////////////////////////////////////////// CSGroups
    public <Z extends MCS> Z addCSGroup(CSGroup... gs) {
        if (gs != null && gs.length > 0) {
            for (CSGroup g : gs) {
                if (g == null || groups.contains(g)) {
                    continue;
                }
                groups.add(g);
                for (CSListener l : cslisteners) {
                    l.onGroupAdded(this, g);
                }
                if (isRunning()) {
                    g.onStarted(this);
                }
            }
        }
        return (Z) this;
    }

    public void removeCSGroup(CSGroup... gs) {
        if (gs != null && gs.length > 0) {
            for (CSGroup g : gs) {
                if (g != null && groups.contains(g)) {
                    groups.remove(g);
                    for (CSListener l : cslisteners) {
                        l.onGroupRemoved(this, g);
                    }
                    g.onStop(this);
                }
            }
        }
    }
}
