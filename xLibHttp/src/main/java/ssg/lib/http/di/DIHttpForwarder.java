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
package ssg.lib.http.di;

import ssg.lib.http.base.HttpForwarder;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.common.net.Forwardable;
import ssg.lib.di.base.BaseDI;

/**
 *
 * @author 000ssg
 */
public class DIHttpForwarder<P extends Channel> extends BaseDI<ByteBuffer, P> implements Forwardable<P> {

    int packetSize = 4096;
    Map<P, Link> links = new HashMap<>();

    boolean TRACE = false;

    @Override
    public long size(Collection<ByteBuffer>... data) {
        return BufferTools.getRemaining(data);
    }

    @Override
    public List<ByteBuffer> produce(P provider) throws IOException {
        Link l = links.get(provider);
        if (l.local == provider) {
            ByteBuffer[] bbs = l.fwd.readExternal();
            if (BufferTools.hasRemaining(bbs)) {
                return BufferTools.aggregate(packetSize, TRACE, bbs);
            }
        } else if (l.remote == provider) {
            ByteBuffer buf = ByteBuffer.allocateDirect(packetSize);
            if (l.fwd.readInternal(buf) > 0) {
                ((Buffer) buf).flip();
                return Collections.singletonList(buf);
            }
        } else {
            throw new IOException("Unknown forwarder channel in 'produce': " + provider);
        }
        return null;
    }

    @Override
    public void consume(P provider, Collection<ByteBuffer>... data) throws IOException {
        Link l = links.get(provider);
        if (l.local == provider) {
            l.fwd.writeInternal(data);
        } else if (l.remote == provider) {
            l.fwd.writeExternal(data);
        } else {
            throw new IOException("Unknown forwarder channel in 'consume': " + provider);
        }
    }

    public long toExternal(P provider, List<ByteBuffer> to, List<ByteBuffer>... from) throws IOException {
        Link l = links.get(provider);
        if (BufferTools.getRemaining(from) > 0) {
            if (TRACE) {
                System.out.println("||||||||||||| toExternal: " + BufferTools.getRemaining(from) + "\n|||||  " + BufferTools.toText(null, from).replace("\n", "\n|||||  "));
            }
        }
        if (l != null && l.fwd != null) {
            long c = BufferTools.getRemaining(from);

            l.fwd.writeExternal(from);
            ByteBuffer bb = ByteBuffer.allocate(l.fwd.getPreferredBufferSize(true));
            int lc = l.fwd.readInternal(bb);
            while (lc > 0) {
                ((Buffer) bb).flip();
                to.add(bb);
                bb = ByteBuffer.allocate(l.fwd.getPreferredBufferSize(true));
                lc = l.fwd.readInternal(bb);
            }

            return c - BufferTools.getRemaining(from);
        } else {
            return 0;
        }
    }

    public long toInternal(P provider, List<ByteBuffer> to, List<ByteBuffer>... from) throws IOException {
        Link l = links.get(provider);
        if (BufferTools.getRemaining(from) > 0) {
            if (TRACE) {
                System.out.println("||||||||||||| toInternal: " + BufferTools.getRemaining(from) + "\n|||||  " + BufferTools.toText(null, from).replace("\n", "\n|||||  "));
            }
        }
        if (l != null && l.fwd != null) {
            long c = BufferTools.getRemaining(from);
            l.fwd.writeInternal(from);
            ByteBuffer[] bbs = l.fwd.readExternal();
            if (bbs != null) {
                for (ByteBuffer bb : bbs) {
                    if (bb != null && bb.hasRemaining()) {
                        to.add(bb);
                    }
                }
            }
            return c - BufferTools.getRemaining(from);
        }
        return 0;
    }

    @Override
    public void onBindForwarding(P local, boolean localIsSecure, P remote, boolean remoteIsSecure) {
        Link l = new Link(local, localIsSecure, remote, remoteIsSecure);
        links.put(local, l);
        links.put(remote, l);
        //l.fwd = null;
    }

    @Override
    public void onUnbindForwarding(P provider) {
        Link l = links.get(provider);
        if (l != null) {
            links.remove(l.local);
            links.remove(l.remote);
        }
    }

    public Link link(P provider) {
        return links.get(provider);
    }

    public class Link {

        P local;
        P remote;
        boolean localIsSecure = false;
        boolean remoteIsSecure = false;

        HttpForwarder fwd;

        public Link(P local, boolean localIsSecure, P remote, boolean remoteIsSecure) {
            this.local = local;
            this.remote = remote;
            this.localIsSecure = localIsSecure;
            this.remoteIsSecure = remoteIsSecure;
            init();
        }

        void init() {
            fwd = new HttpForwarder((Channel) local, localIsSecure, (Channel) remote, remoteIsSecure);
        }
    }
}
