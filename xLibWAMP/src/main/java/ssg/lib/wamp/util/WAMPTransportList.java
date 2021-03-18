package ssg.lib.wamp.util;

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
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.WAMPTransport;
import ssg.lib.wamp.stat.WAMPMessageStatistics;

/**
 * In-memory WAMP transport. Useful for tests and local connections.
 *
 * @author 000ssg
 */
public class WAMPTransportList<P> implements WAMPTransport {

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    public static boolean GLOBAL_ENABLE_TRACE_MESSAGES = true;

    public final int id = NEXT_ID.getAndIncrement();

    P provider;
    TransportData transport;
    private WAMPMessageStatistics statistics;
    WAMPMessage last;
    public boolean ENABLE_TRACE_MESSAGES = GLOBAL_ENABLE_TRACE_MESSAGES;
    public String TRACE_MESSAGES = null;

    public WAMPTransportList() {
        transport = new TransportData();
    }

    public WAMPTransportList(Class outputType) {
        transport = new TransportData();
        transport.setOutputType(outputType);
    }

    public WAMPTransportList(P provider, Class outputType) {
        this.provider = provider;
        transport = new TransportData();
        transport.setOutputType(outputType);
    }

    public WAMPTransportList(TransportData transport) {
        this.transport = transport;
    }

    public WAMPTransportList(P provider, TransportData transport) {
        this.provider = provider;
        this.transport = transport;
    }

    public WAMPTransportList(List input, List output) {
        transport = new TransportData(input, output);
    }

    public WAMPTransportList(List input, List output, Class outputType) {
        transport = new TransportData(input, output);
        transport.setOutputType(outputType);
    }

    @Override
    public void send(WAMPMessage message) throws WAMPException {
        try {
            transport.send(message);
            if (message != null && statistics != null) {
                statistics.onSent(message);
            }
            if (ENABLE_TRACE_MESSAGES && message != null && TRACE_MESSAGES != null) {
                System.out.println("[" + System.currentTimeMillis() + "][" + TRACE_MESSAGES + "-" + id + "]-OU: " + ("" + message.toList()).replace("\n", " ").replace("  ", " ").replace("  ", " ").replace("  ", " ").replace("  ", " ").replace("  ", " "));
            }
        } catch (WAMPException wex) {
            throw wex;
        } catch (IOException ioex) {
            throw new WAMPException(ioex);
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
            WAMPMessage r = transport.receive();
            if (r != null && statistics != null) {
                statistics.onReceived(r);
            }
            if (ENABLE_TRACE_MESSAGES && r != null && TRACE_MESSAGES != null) {
                System.out.println("[" + System.currentTimeMillis() + "][" + TRACE_MESSAGES + "-" + id + "]-IN: " + ("" + r.toList()).replace("\n", " ").replace("  ", " ").replace("  ", " ").replace("  ", " ").replace("  ", " ").replace("  ", " "));
            }
            return r;
        } catch (IOException ioex) {
            ioex.printStackTrace();
            throw new WAMPException(ioex);
        }
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
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isOpen() {
        return transport != null && transport.isOpen();
    }

    public static class TransportData {

        private List<List> output;
        private List<List> input;
        // preferred output format
        private Class outputType = byte[].class;

        public TransportData() {
            output = WAMPTools.createSynchronizedList();
            input = WAMPTools.createSynchronizedList();
        }

        public TransportData(Class outputType) {
            output = WAMPTools.createSynchronizedList();
            input = WAMPTools.createSynchronizedList();
            if (outputType != null) {
                this.outputType = outputType;
            }
        }

        public TransportData(List input, List output) {
            if (input == null) {
                input = WAMPTools.createSynchronizedList();
            }
            if (output == null) {
                output = WAMPTools.createSynchronizedList();
            }
            this.input = input;
            this.output = output;
        }

        public boolean isOpen() {
            return true;
        }

        public WAMPMessage receive() throws IOException {
            if (!input.isEmpty()) {
                WAMPMessage r = new WAMPMessage(input.remove(0));
                if (r != null) {
                    onReceived(r);
                }
                return r;
            } else {
                return null;
            }
        }

        public void send(WAMPMessage... messages) throws IOException {
            if (messages != null) {
                for (WAMPMessage msg : messages) {
                    if (hasData(msg)) {
                        onSend(msg);
                        output.add(msg.toList());
                    }
                }
            }
        }

        /**
         * String-per-message received messages
         *
         * @param messages
         */
        public void add(List... messages) {
            if (messages != null) {
                for (List msg : messages) {
                    if (hasData(msg)) {
                        onRCV(msg);
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
        public List get() {
            if (!output.isEmpty()) {
                List r = output.remove(0);
                if (r != null) {
                    onSND(r);
                }
                return r;
            } else {
                return null;
            }
        }

        public boolean hasData(Object data) {
            if (data == null) {
                return false;
            } else if (data instanceof WAMPMessage) {
                return !((WAMPMessage) data).toList().isEmpty();
            } else if (data instanceof List) {
                return !((List) data).isEmpty();
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

        ////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////// Spy listeners
        ////////////////////////////////////////////////////////////////////////
        public void onSend(WAMPMessage message) {
        }

        public void onSND(List message) {
        }

        public void onRCV(List message) {
        }

        public void onReceived(WAMPMessage message) {
        }
    }

    /**
     * Closed loop messaging with statistics.
     */
    public static class WAMPTransportLoop {

        static AtomicInteger NEXT_TRANSPORT_ID = new AtomicInteger(1);
        int id = NEXT_TRANSPORT_ID.getAndIncrement();

        public boolean TRACE_MSG = false;
        public boolean TRACE_DATA = false;

        public WAMPTransportList local = new WAMPTransportList(new TransportData() {
            // app message to send
            @Override
            public void onSend(WAMPMessage message) {
                if (TRACE_MSG) {
                    System.out.println("[L" + id + "].onSend    (" + message.toList() + ")");
                }
            }

            // serialized message to send over wire
            @Override
            public void onSND(List message) {
                if (TRACE_DATA) {
                    System.out.println("[L" + id + "].onSND     (" + message + ")");
                }
            }

            // serialized message received over wire
            @Override
            public void onRCV(List message) {
                if (TRACE_MSG) {
                    System.out.println("[L" + id + "].onRCV     (" + message + ")");
                }
            }

            // received app message
            @Override
            public void onReceived(WAMPMessage message) {
                if (TRACE_MSG) {
                    System.out.println("[L" + id + "].onReceived(" + message.toList() + ")");
                }
            }
        });
        public WAMPTransportList remote = new WAMPTransportList(new TransportData(local.transport.output, local.transport.input) {
            // app message to send
            @Override
            public void onSend(WAMPMessage message) {
                if (TRACE_MSG) {
                    System.out.println("[R" + id + "].onSend    (" + message.toList() + ")");
                }
            }

            // serialized message to send over wire
            @Override
            public void onSND(List message) {
                if (TRACE_DATA) {
                    System.out.println("[R" + id + "].onSND     (" + message + ")");
                }
            }

            // serialized message received over wire
            @Override
            public void onRCV(List message) {
                if (TRACE_MSG) {
                    System.out.println("[R" + id + "].onRCV     (" + message + ")");
                }
            }

            // received app message
            @Override
            public void onReceived(WAMPMessage message) {
                if (TRACE_MSG) {
                    System.out.println("[R" + id + "].onReceived(" + message.toList() + ")");
                }
            }
        });

        public WAMPTransportLoop() {
        }

        public String getStat() {
            StringBuilder sb = new StringBuilder();
            long ts = System.currentTimeMillis();
            return sb.toString();
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
