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
package ssg.lib.wamp.cs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import ssg.lib.common.JSON;
import ssg.lib.common.JSON.STATE;
import ssg.lib.common.Refl;
import ssg.lib.common.Refl.ReflImpl;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.WAMPTransport;
import ssg.lib.wamp.stat.WAMPMessageStatistics;
import ssg.lib.wamp.util.LS;

/**
 *
 * @author 000ssg
 */
public class WAMPTransportJSON<P> implements WAMPTransport {

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static Refl refl = new ReflImpl();

    public final int id = NEXT_ID.getAndIncrement();
    JSON.Encoder encoder = new JSON.Encoder(refl);
    JSON.Decoder decoder = new JSON.Decoder(refl);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    P provider;
    TransportData transport;
    private WAMPMessageStatistics statistics;
    LS<WAMPTransportMessageListener> listeners = new LS<>(new WAMPTransportMessageListener[0]);
    WAMPMessage last;

    public WAMPTransportJSON() {
        transport = new TransportData();
    }

    public WAMPTransportJSON(Class outputType) {
        transport = new TransportData();
        transport.setOutputType(outputType);
    }

    public WAMPTransportJSON(P provider, Class outputType) {
        this.provider = provider;
        transport = new TransportData();
        transport.setOutputType(outputType);
    }

    public WAMPTransportJSON(TransportData transport) {
        this.transport = transport;
    }

    public WAMPTransportJSON(P provider, TransportData transport) {
        this.provider = provider;
        this.transport = transport;
    }

    public WAMPTransportJSON(List input, List output) {
        transport = new TransportData(input, output);
    }

    public WAMPTransportJSON(List input, List output, Class outputType) {
        transport = new TransportData(input, output);
        transport.setOutputType(outputType);
    }

    @Override
    public void send(WAMPMessage message) throws WAMPException {
        boolean sent = false;
        synchronized (encoder) {
            try {
                encoder.put(message.toList());
                if (transport.getOutputType() == byte[].class || transport.getOutputType() == ByteBuffer.class) {
                    byte[] buf = new byte[1024];
                    ByteBuffer bb = ByteBuffer.wrap(buf);
                    int c = 0;
                    while ((c = encoder.read(bb)) > 0) {
                        baos.write(buf, 0, c);
                        ((Buffer) bb).clear();
                    }
                    if (transport.getOutputType() == byte[].class) {
                        transport.send(baos.toByteArray());
                        sent = true;
                    } else {
                        transport.send(ByteBuffer.wrap(baos.toByteArray()));
                        sent = true;
                    }
                } else if (transport.getOutputType() == String.class || transport.getOutputType() == CharBuffer.class) {
                    StringBuilder sb = new StringBuilder();
                    char[] buf = new char[1024];
                    CharBuffer bb = CharBuffer.wrap(buf);
                    int c = 0;
                    while ((c = encoder.read(bb)) > 0) {
                        sb.append(buf, 0, c);
                        ((Buffer) bb).clear();
                    }
                    if (transport.getOutputType() == String.class) {
                        transport.send(sb.toString());
                        sent = true;
                    } else {
                        transport.send(CharBuffer.wrap(sb));
                        sent = true;
                    }
                } else {
                    throw new WAMPException("Output data format is not supported: " + transport.getOutputType());
                }
            } catch (IOException ioex) {
                ioex.printStackTrace();
                throw new WAMPException("Transport send error: " + ioex, ioex);
            } finally {
                baos.reset();
                if (sent) {
                    if (message != null && statistics != null) {
                        statistics.onSent(message);
                        statistics.setInputQueueSize(transport.getInputQueueSize());
                        statistics.setOutputQueueSize(transport.getOutputQueueSize());
                    }
                    for (WAMPTransportMessageListener l : listeners.get()) {
                        l.onMessageSent(this, message);
                    }
                }
            }
        }
    }

    @Override
    public WAMPMessage receive() throws WAMPException {
        try {
            if (last != null) {
                try {
                    return last;
                } finally {
                    last = null;
                }
            }

            Object buf = transport.receive();
            if (buf != null) {
                STATE st0 = decoder.getState();
                try {
                    synchronized (decoder) {
                        if (buf instanceof byte[]) {
                            decoder.write(ByteBuffer.wrap((byte[]) buf));
                        } else if (buf instanceof ByteBuffer) {
                            decoder.write((ByteBuffer) buf);
                        } else if (buf instanceof String) {
                            decoder.write(CharBuffer.wrap((String) buf));
                        } else if (buf instanceof CharBuffer) {
                            decoder.write((CharBuffer) buf);
                        } else {
                            throw new WAMPException("transport.data.error");
                        }

                        WAMPMessage r = null;
                        if (STATE.ok == decoder.getState()) {
                            r = new WAMPMessage(decoder.get());
                        } else {
                            r = new WAMPMessage(decoder.get());
                        }
                        if (r != null && statistics != null) {
                            statistics.onReceived(r);
                            statistics.setInputQueueSize(transport.getInputQueueSize());
                            statistics.setOutputQueueSize(transport.getOutputQueueSize());
                        }
                        for (WAMPTransportMessageListener l : listeners.get()) {
                            l.onMessageReceived(this, r);
                        }
                        return r;
                    }
                } catch (IOException ioex) {
                    ioex.printStackTrace();
                    System.err.println("MORE INFO FOR " + ioex + ":\n    st0=" + st0 + "\n    buf=" + ("" + buf).replace("\n ", "\n    "));
                    throw new WAMPException("Transport receive error: " + ioex, ioex);
                }
            }
        } catch (IOException ioex) {
            ioex.printStackTrace();
            throw new WAMPException(ioex);
        }
        return null;
    }

    @Override
    public void unreceive(WAMPMessage last) throws WAMPException {
        if (last == null) {
            return;
        }
        if (this.last != null) {
            throw new WAMPException("Only 1-level unreceive is allowed:"
                    + "\n  have last=" + this.last.toList().toString().replace("\n", "\n  ")
                    + "\n  new  last=" + last.toList().toString().replace("\n", "\n  ")
            );
        }
        this.last = last;
        for (WAMPTransportMessageListener l : listeners.get()) {
            l.onMessageUnreceived(this, last);
        }
    }

    @Override
    public void close() {
        transport.close();
    }

    @Override
    public boolean isOpen() {
        return transport != null && transport.isOpen();
    }

    @Override
    public void addWAMPTransportMessageListener(WAMPTransportMessageListener... ls) {
        listeners.add(ls);
    }

    @Override
    public void removeWAMPTransportMessageListener(WAMPTransportMessageListener... ls) {
        listeners.remove(ls);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append('{');
        sb.append("id=" + id);
        sb.append(", listeners=" + listeners.size());
        if (encoder != null) {
            sb.append(", encoder=" + (encoder.getClass().isAnonymousClass() ? encoder.getClass().getName() : encoder.getClass().getSimpleName()));
        }
        if (decoder != null) {
            sb.append(", decoder=" + (decoder.getClass().isAnonymousClass() ? decoder.getClass().getName() : decoder.getClass().getSimpleName()));
        }
        if (last != null) {
            sb.append("\n  last=" + last.toList().toString().replace("\n", "\n    "));
        }
        sb.append("\n  provider=" + ("" + provider).replace("\n", "\n    "));
        //sb.append("\n  transport=" + ("" + transport).replace("\n", "\n    "));
//        if (statistics != null) {
//            sb.append("\n  statistics=" + statistics.dumpStatistics(true).replace("\n", "\n    "));
//        }
        sb.append('\n');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + this.id;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final WAMPTransportJSON<?> other = (WAMPTransportJSON<?>) obj;
        if (this.id != other.id) {
            return false;
        }
        return true;
    }

    public static class TransportData {

        private List output;
        private List input;
        // preferred output format
        private Class outputType = byte[].class;

        public TransportData() {
            output = Collections.synchronizedList(new ArrayList());
            input = Collections.synchronizedList(new ArrayList());
        }

        public TransportData(Class outputType) {
            output = Collections.synchronizedList(new ArrayList());
            input = Collections.synchronizedList(new ArrayList());
            if (outputType != null) {
                this.outputType = outputType;
            }
        }

        public TransportData(List input, List output) {
            if (input == null) {
                input = Collections.synchronizedList(new ArrayList());
            }
            if (output == null) {
                output = Collections.synchronizedList(new ArrayList());
            }
            this.input = input;
            this.output = output;
        }

        public boolean isOpen() {
            return true;
        }

        public void close() {
        }

        public Object receive() throws IOException {
            synchronized (input) {
                if (!input.isEmpty()) {
                    return input.remove(0);
                } else {
                    return null;
                }
            }
        }

        public void send(Object... messages) throws IOException {
            if (messages != null) {
                for (Object msg : messages) {
                    if (hasData(msg)) {
                        output.add(msg);
                    }
                }
            }
        }

        /**
         * String-per-message received messages
         *
         * @param messages
         */
        public void add(Object... messages) {
            if (messages != null) {
                for (Object msg : messages) {
                    if (hasData(msg)) {
                        //System.out.println("Client: receive/#" + messages.length + ": " + msg);
                        input.add(msg);
                    }
                }
            }
        }

        /**
         * Per-message texts to send
         *
         * @return
         */
        public Object get() {
            if (!output.isEmpty()) {
                return output.remove(0);
            } else {
                return null;
            }
        }

        public boolean hasData(Object data) {
            if (data == null) {
                return false;
            } else if (data instanceof String) {
                return !((String) data).isEmpty();
            } else if (data instanceof Buffer) {
                return !((Buffer) data).hasRemaining();
            } else if (data instanceof byte[]) {
                return ((byte[]) data).length > 0;
            } else {
                return false;
            }
        }

        /**
         * @return the outputType
         */
        public Class getOutputType() {
            return outputType;
        }

        /**
         * @param outputType the outputType to set
         */
        public void setOutputType(Class outputType) {
            this.outputType = outputType;
        }

        public int getInputQueueSize() {
            return input.size();
        }

        public int getOutputQueueSize() {
            return output.size();
        }
    }

    public static class WAMPJSONTransportLoop {

        public WAMPTransportJSON local = new WAMPTransportJSON(Collections.synchronizedList(new ArrayList<>()), Collections.synchronizedList(new ArrayList<>())) {
            @Override
            public void send(WAMPMessage message) throws WAMPException {
                if (message != null) {
                    onL2R(message);
                }
                super.send(message);
            }

            @Override
            public WAMPMessage receive() throws WAMPException {
                WAMPMessage r = super.receive();
                if (r != null) {
                    onLR(r);
                }
                return r;
            }
        };
        public WAMPTransportJSON remote = new WAMPTransportJSON(local.transport.output, local.transport.input) {
            @Override
            public void send(WAMPMessage message) throws WAMPException {
                if (message != null) {
                    onR2L(message);
                }
                super.send(message);
            }

            @Override
            public WAMPMessage receive() throws WAMPException {
                WAMPMessage r = super.receive();
                if (r != null) {
                    onRR(r);
                }
                return r;
            }
        };

        public void onLR(WAMPMessage msg) {
        }

        public void onRR(WAMPMessage msg) {
        }

        public void onL2R(WAMPMessage msg) {
        }

        public void onR2L(WAMPMessage msg) {
        }
    }

    /**
     * @return the statistics
     */
    public WAMPMessageStatistics getStatistics() {
        return statistics;
    }

    /**
     * @param statistics the statistics to set
     */
    public void setStatistics(WAMPMessageStatistics statistics) {
        this.statistics = statistics;
    }

}
