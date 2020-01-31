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
package ssg.lib.common.buffers;

import java.io.IOException;
import java.nio.ByteBuffer;
import ssg.lib.common.Huffman;

/**
 *
 * @author 000ssg
 */
public class ByteBufferPipeHuffmanPack extends ByteBufferPipe {

    private Huffman.Encoder encoder;

    public ByteBufferPipeHuffmanPack() {
    }

    public ByteBufferPipeHuffmanPack(Huffman.Encoder encoder) {
        this.encoder = encoder;
    }

    public ByteBufferPipeHuffmanPack(BufferPipe<ByteBuffer> nested) {
        super(nested);
    }

    public ByteBufferPipeHuffmanPack(Huffman.Encoder encoder, BufferPipe<ByteBuffer> nested) {
        super(nested);
        this.encoder = encoder;
    }

    @Override
    public void write(ByteBuffer... bbs) throws IOException {
        if (bbs == null || bbs.length == 0) {
            return;
        }
        encoder.add(bbs);
        if (encoder.currentSize() > 10) {
            super.write(ByteBuffer.wrap(encoder.flushAndGet()));
        }
    }

    @Override
    public void close() throws IOException {
        encoder.close();
        if (encoder.currentSize() > 0) {
            super.write(ByteBuffer.wrap(encoder.flushAndGet()));
        }
        super.close();
    }

    /**
     * @return the encoder
     */
    public Huffman.Encoder getEncoder() {
        return encoder;
    }

    /**
     * @param encoder the encoder to set
     */
    public void setEncoder(Huffman.Encoder encoder) {
        this.encoder = encoder;
    }

}
