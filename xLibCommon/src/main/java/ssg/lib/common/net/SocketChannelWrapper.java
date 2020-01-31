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
import java.io.PrintStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Set;

/**
 *
 * @author 000ssg
 */
public class SocketChannelWrapper extends SocketChannel {

    PrintStream logWriter;
    SocketChannel base;
    //Method implCloseSelectableChannel;
    //Method implConfigureBlocking;

    public SocketChannelWrapper(SocketChannel base) {
        super(null);
        this.base = base;

//        try {
//            implCloseSelectableChannel = base.getClass().getDeclaredMethod("implCloseSelectableChannel", null);
//            implCloseSelectableChannel.setAccessible(true);
//        } catch (Throwable th) {
//        }
//        try {
//            implConfigureBlocking = base.getClass().getDeclaredMethod("implConfigureBlocking", boolean.class);
//            implConfigureBlocking.setAccessible(true);
//        } catch (Throwable th) {
//        }
    }

    public SocketChannel getBase() {
        return base;
    }

    @Override
    public SocketChannel bind(SocketAddress local) throws IOException {
        base.bind(local);
        return this;
    }

    @Override
    public <T> SocketChannel setOption(SocketOption<T> name, T value) throws IOException {
        base.setOption(name, value);
        return this;
    }

    @Override
    public SocketChannel shutdownInput() throws IOException {
        base.shutdownInput();
        return this;
    }

    @Override
    public SocketChannel shutdownOutput() throws IOException {
        base.shutdownOutput();
        return this;
    }

    @Override
    public Socket socket() {
        return base.socket();
    }

    @Override
    public boolean isConnected() {
        return base.isConnected();
    }

    @Override
    public boolean isConnectionPending() {
        return base.isConnectionPending();
    }

    @Override
    public boolean connect(SocketAddress remote) throws IOException {
        return base.connect(remote);
    }

    @Override
    public boolean finishConnect() throws IOException {
        return base.finishConnect();
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        return base.getRemoteAddress();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        //return base.read(dst);
        long started = System.nanoTime();
        int i = base.read(dst);
        log("read(i)", i);
        return i;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        //return base.read(dsts, offset, length);
        long started = System.nanoTime();
        long l = base.read(dsts, offset, length);
        log("read(l)", l);
        return l;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        //return base.write(src);
        long started = System.nanoTime();
        int i = base.write(src);
        log("write(i)", i);
        return i;
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        //return base.write(srcs, offset, length);
        long started = System.nanoTime();
        long l = base.write(srcs, offset, length);
        log("write(l)", l);
        return l;
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return base.getLocalAddress();
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
        try {
            //implCloseSelectableChannel.invoke(base);
            base.close();
        } catch (Throwable th) {
            if (th instanceof IOException) {
                throw (IOException) th;
            } else {
                throw new IOException(th);
            }
        }
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
        try {
            //implConfigureBlocking.invoke(base, block);
            base.configureBlocking(block);
        } catch (Throwable th) {
            if (th instanceof IOException) {
                throw (IOException) th;
            } else {
                throw new IOException(th);
            }
        }
    }

    @Override
    public <T> T getOption(SocketOption<T> name) throws IOException {
        return base.getOption(name);
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return base.supportedOptions();
    }

    public void log(String title, Number value) {
        if (logWriter != null) {
            logWriter.println("    [" + Thread.currentThread().getName() + ", " + System.currentTimeMillis() + "] " + value + "\t " + title);
        }
    }
}
