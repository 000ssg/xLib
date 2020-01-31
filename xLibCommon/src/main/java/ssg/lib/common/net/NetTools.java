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
package ssg.lib.common.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

/**
 *
 * @author 000ssg
 */
public class NetTools {

    public static boolean delay(long duration) {
        try {
            Thread.sleep(duration);
            return true;
        } catch (Throwable th) {
            return false;
        }
    }

    public static final long NI_OPT_UP = 0x0001;
    public static final long NI_OPT_LOOPBACK = 0x0002;
    public static final long NI_OPT_MULTICAST = 0x0004;
    public static final long NI_OPT_VIRTUAL = 0x0008;
    public static final long NI_OPT_P2P = 0x0010;

    /**
     * Returns default addresses that may be hosted by server. Used in port-only
     * constructors.
     *
     * As addition utility function to find free port for hosted address.
     *
     * @return
     */
    public InetAddress[] getDefaultAddresses() {
        return getAllLocalAddresses();
    }

    /**
     * Returns all addresses associated with UP interfaces that are not virtual
     * and not PointToPoint If local addresses restrictions are applied then
     * only allowed (restricted to) are returned.
     *
     * @return
     */
    public static InetAddress[] getAllLocalAddresses() {
        List<InetAddress> addrs = null;
        List<NetworkInterface> nis = getSupportedNetworkInterfaces(true, null, null, false, false);
        addrs = new ArrayList<InetAddress>();
        for (NetworkInterface ni : nis) {
            Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                addrs.add(inetAddresses.nextElement());
            }
        }
        return addrs.toArray(new InetAddress[addrs.size()]);
    }

    /**
     * Returns all addresses associated with DNS host name.
     *
     * @param host
     * @return
     */
    public static InetAddress[] getAllAddresses(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException uhex) {
        }
        return new InetAddress[0];
    }

    /**
     * Returns list of network interfaces with appropriate flags. If flag is
     * null its value is ignored.
     *
     * @param up
     * @param loopback
     * @param multicast
     * @param virtual
     * @param pointToPoint
     * @return
     */
    public static List<NetworkInterface> getSupportedNetworkInterfaces(Boolean up, Boolean loopback, Boolean multicast, Boolean virtual, Boolean pointToPoint) {
        List<NetworkInterface> result = new ArrayList<NetworkInterface>();
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (up != null && ni.isUp() != up) {
                    continue;
                }
                if (loopback != null && ni.isLoopback() != loopback) {
                    continue;
                }
                if (multicast != null && ni.supportsMulticast() != multicast) {
                    continue;
                }
                if (virtual != null && ni.isVirtual() != virtual) {
                    continue;
                }
                if (pointToPoint != null && ni.isPointToPoint() != pointToPoint) {
                    continue;
                }
                result.add(ni);
            }
        } catch (SocketException sex) {
        }
        return result;
    }

    /**
     * Returns list of interfaces matching any NI_OPT_* inf ni_opts_include and
     * not matching any NI_OPT_* in ni_opts_exclude. Both option sets may be
     * null meaning no restrictions.
     *
     * @param ni_opts_include
     * @param ni_opts_exclude
     * @return
     */
    public static List<NetworkInterface> getSupportedNetworkInterfaces(Long ni_opts_include, Long ni_opts_exclude) {
        List<NetworkInterface> result = new ArrayList<NetworkInterface>();
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                long opts = 0;
                if (ni.isUp()) {
                    opts |= NI_OPT_UP;
                }
                if (ni.isLoopback()) {
                    opts |= NI_OPT_LOOPBACK;
                }
                if (ni.supportsMulticast()) {
                    opts |= NI_OPT_MULTICAST;
                }
                if (ni.isVirtual()) {
                    opts |= NI_OPT_VIRTUAL;
                }
                if (ni.isPointToPoint()) {
                    opts |= NI_OPT_P2P;
                }

                if (ni_opts_include == null || (ni_opts_include & opts) != 0) {
                    if (ni_opts_exclude == null || (ni_opts_exclude & opts) == 0) {
                        result.add(ni);
                    }
                }
            }
        } catch (SocketException sex) {
        }
        return result;
    }

    public static NetworkInterface getDefaultNetworkInterface() {
        for (NetworkInterface ni0 : getSupportedNetworkInterfaces(true, false, null, false, false)) {
            final NetworkInterface ni = ni0;

            List<InterfaceAddress> ias = ni.getInterfaceAddresses();
            for (InterfaceAddress ia : ias) {
                //System.out.println(" " + ni.getDisplayName() + "  " + ia.getAddress() + ", " + ia.getNetworkPrefixLength() + ", " + ia.getBroadcast() + ", " + ia.toString());
                if (ia.getNetworkPrefixLength() == -1) {
                    //System.out.println("  DEFAULT ni/addr = " + ni.getDisplayName() + "/" + ia.getAddress());
                    return ni;
                }
            }
        }
        return null;
    }

    /**
     * Send UDP message (data) to target address/port.
     *
     * Returns -2 if address or data are invalid. Returns -1 if error while
     * preparing/sending.
     *
     * @param target
     * @param port
     * @param data
     * @return
     */
    public static int sendUDP(InetAddress source, int sourcePort, InetAddress target, int port, byte[] data) {
        if (target == null || port == 0 || port < 0 || data == null) {
            return -2;
        }
        try {
            DatagramPacket packet = new DatagramPacket(data, data.length,
                    target, port);
            if (sourcePort == 0) {
                sourcePort = findFreePort(source);
            }
            DatagramSocket ds = new DatagramSocket(new InetSocketAddress(source, sourcePort));
            ds.send(packet);
            ds.close();
            return data.length;
        } catch (Throwable th) {
            return -1;
        }
    }

    /**
     * Send UDP message (data) to target address/port.
     *
     * Returns -2 if address or data are invalid. Returns -1 if error while
     * preparing/sending.
     *
     * @param target
     * @param port
     * @param data
     * @return
     */
    public static int sendUDP(InetAddress target, int port, byte[] data) {
        if (target == null || port == 0 || port < 0 || data == null) {
            return -2;
        }
        try {
            DatagramPacket packet = new DatagramPacket(data, data.length,
                    target, port);
            DatagramSocket ds = new DatagramSocket();
            ds.send(packet);
            ds.close();
            return data.length;
        } catch (Throwable th) {
            return -1;
        }
    }

    /**
     * Send UDP message (data) to target address/port.
     *
     * Returns data responded within timeout.
     *
     * @param target
     * @param port
     * @param data
     * @return
     */
    public static byte[] sendUDP(InetAddress target, int port, byte[] data, int timeout) {
        byte[] result = null;
        if (target == null || port == 0 || port < 0 || data == null) {
            return result;
        }
        try {
            DatagramPacket packet = new DatagramPacket(data, data.length,
                    target, port);
            DatagramSocket ds = new DatagramSocket();
            if (timeout > 0) {
                ds.setSoTimeout(timeout);
            }
            ds.send(packet);
            if (timeout > 0) {
                byte[] buf = new byte[2000];
                DatagramPacket dpR = new DatagramPacket(buf, buf.length, target, port);
                ds.receive(dpR);
                if (dpR.getLength() > 0) {
                    result = Arrays.copyOf(buf, dpR.getLength());
                }
            }
            ds.close();
        } catch (Throwable th) {
        }
        return result;
    }

    /**
     * Send UDP message (data) to target address/port.
     *
     * Returns data responded within timeout.
     *
     * @param target
     * @param port
     * @param data
     * @return
     */
    public static void sendUDP(InetAddress target, int port, byte[] data, DatagramSocket ds) {
        byte[] result = null;
        if (target == null || port == 0 || port < 0 || data == null) {
            return;
        }
        try {
            DatagramPacket packet = new DatagramPacket(data, data.length,
                    target, port);
            ds.send(packet);
        } catch (Throwable th) {
        }
    }

    /**
     * Send UDP message (data) to target address/port.
     *
     * Returns data responded within timeout.
     *
     * @param target
     * @param port
     * @param data
     * @param ds
     * @return
     */
    public static void sendUDP(InetAddress target, int port, byte[] data, DatagramChannel ds) {
        byte[] result = null;
        if (target == null || port == 0 || port < 0 || data == null) {
            return;
        }
        try {
            ds.send(ByteBuffer.wrap(data), new InetSocketAddress(target, port));
        } catch (Throwable th) {
        }
    }

    /**
     * Utility to find free port.
     *
     * @return
     * @throws IOException
     */
    public static int findFreePort(InetAddress address) {
        try {
            ServerSocket server = new ServerSocket(0, 0, address);
            server.setReuseAddress(true);
            int port = server.getLocalPort();
            server.close();
            return port;
        } catch (IOException ioex) {
            return -1;
        }
    }

    /**
     * Test if requested port is available. // based on
     * http://stackoverflow.com/questions/434718/sockets-discover-port-availability-using-java
     *
     * @param port
     * @param bindAddr
     * @return
     */
    public static boolean isPortAvailable(int port, InetAddress bindAddr, boolean udp) {
        ServerSocket ss = null;
        DatagramSocket ds = null;
        try {
            if (!udp) {
                ss = (bindAddr == null) ? new ServerSocket(port) : new ServerSocket(port, 0, bindAddr);
                ss.setReuseAddress(true);
            } else {
                ds = new DatagramSocket(null);
                ds.setReuseAddress(true);
                ds.bind(new InetSocketAddress(bindAddr, port));
            }
            return true;
        } catch (IOException e) {
            int a = 0;
        } finally {
            if (ds != null) {
                ds.close();
            }

            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException e) {
                }
            }
        }
        return false;
    }

    /**
     * Returns port for given SocketAddress by parsing int number after ":" in
     * its string representation.
     *
     * @param addr
     * @return
     */
    public static int getPort(SocketAddress addr) {
        if (addr == null) {
            return -1;
        }
        String a = addr.toString();
        return Integer.parseInt(a.substring(a.lastIndexOf(":") + 1));
    }

    /**
     * Returns address name byt removin leading "/" and trailing ":" with port.
     *
     * @param addr
     * @return
     */
    public static String getAddress(SocketAddress addr) {
        if (addr == null) {
            return null;
        }
        String a = addr.toString();
        a = a.substring(0, a.indexOf(":"));
        int idx = a.indexOf("/");
        if (idx > 0) {
            a = a.substring(0, idx);
        } else {
            a = a.substring(idx + 1);
        }
        return a;
    }

}
