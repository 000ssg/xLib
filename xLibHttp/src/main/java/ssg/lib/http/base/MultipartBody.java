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
package ssg.lib.http.base;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Multipart data support.
 */
public class MultipartBody extends Body {

    // multipart
    File folder;
    List<Part> parts = new ArrayList<Part>();
    // parser support
    byte[] separator = new byte[128];
    int separatorPos = 0;
    int skip = 0;
    boolean separatorReady = false;
    List<String> currentHeaders = new ArrayList<String>();
    Part currentPart = null;
    boolean headersReady = false;
    ByteBuffer bbb = ByteBuffer.allocate(1024);

    // send status
    boolean sending = false;
    int nextPartToSend = 0;

    public MultipartBody() {
    }

    public MultipartBody(HttpData http) {
        super(http);
        separator[0] = '\r';
        separator[1] = '\n';
        separatorPos = 2;
    }

    @Override
    public void initFrom(Body body) {
        MultipartBody mb = (MultipartBody) body;
        // multipart
        folder = mb.folder;
        for (Part p : mb.getParts()) {
            if (p != null) {
                parts.add(new Part(p));
            }
        }
        // parser support
        separator = Arrays.copyOf(mb.separator, mb.separator.length);
        separatorPos = mb.separatorPos;
        skip = mb.skip;
        separatorReady = mb.separatorReady;
        headersReady = mb.headersReady;
    }

    public List<Part> getParts() {
        return parts;
    }

    /**
     * Processes multipart content: 1st line is separator: stored and used as
     * delimiter as well as marker of 1st part. Lines after separator up to
     * empty -> headers. Bytes up to next separator - part content.
     *
     * @param bbs
     */
    @Override
    public void add(ByteBuffer... bbs) throws IOException {
        for (ByteBuffer bb : bbs) {
            if (bb == null || !bb.hasRemaining()) {
                continue;
            }
            // evaluate separator: 1st line until CR.
            while (!separatorReady && bb.hasRemaining()) {
                byte b = bb.get();
                length++;
                sz++;
                if (b == '\r') {
                    separatorReady = true;
                    if (separatorPos < separator.length) {
                        separator = Arrays.copyOf(separator, separatorPos);
                    }
                    separatorPos = 0;
                    skip = 1;
                } else {
                    if (separatorPos == separator.length) {
                        separator = Arrays.copyOf(separator, separator.length + 64);
                    }
                    separator[separatorPos++] = b;
                }
            }
            if (bb.hasRemaining()) {
                while (bb.hasRemaining() && bbb.hasRemaining()) {
                    byte b = bb.get();
                    //System.out.println("" + length + "/" + separatorPos + "\t: " + b + " '" + ((char) (0xFF & b)) + "'");
                    length++;
                    sz++;
                    if (skip > 0) {
                        skip--;
                        continue;
                    }
                    if (!headersReady) {
                        if (b == '\r') {
                            skip = 1;
                            if (bbb.position() == 0) {
                                headersReady = true;
                                continue;
                            } else {
                                ((Buffer) bbb).flip();
                                StringBuilder sb = new StringBuilder();
                                while (bbb.hasRemaining()) {
                                    sb.append((char) (0xFF & bbb.get()));
                                }
                                //System.out.println("" + length + "\t: hdr = " + sb.toString());
                                currentHeaders.add(sb.toString());
                                ((Buffer) bbb).clear();
                            }
                        } else {
                            if (!bbb.hasRemaining()) {
                                throw new IOException("Invalid multipart delimiter size: " + bbb.position());
                            }
                            bbb.put(b);
                        }
                    }
                    if (!headersReady) {
                        continue;
                    }
                    if (currentPart == null) {
                        currentPart = new Part(currentHeaders);
                        parts.add(currentPart);
                        currentHeaders.clear();
                    }
                    if (separator[separatorPos] == b) {
                        //System.out.println("SP["+separatorPos+"] == "+b);
                        separatorPos++;
                        if (separator.length == separatorPos) {
                            // end of part
                            if (bbb.position() > 0) {
                                ((Buffer) bbb).flip();
                                currentPart.add(bbb, true);
                            } else {
                                currentPart.add(ByteBuffer.allocate(0), true);
                            }
                            ((Buffer) bbb).clear();
                            currentPart = null;
                            skip = 2;
                            headersReady = false;
                            separatorPos = 0;
                        } else {
                            if (separatorPos == 1 && bbb.position() > 0) {
                                ((Buffer) bbb).flip();
                                currentPart.add(bbb, false);
                                ((Buffer) bbb).clear();
                            }
                            //                                if (separatorPos > 0) {
                            //                                    currentPart.add(ByteBuffer.wrap(separator, 0, separatorPos), false);
                            //                                    separatorPos = 0;
                            //                                }
                            //                                bbb.put(b);
                        }
                    } else {
                        //                            if (!bbb.hasRemaining()) {
                        //                                bbb.flip();
                        //                                currentPart.add(bbb, false);
                        //                                bbb.clear();
                        //                            }
                        if (separatorPos > 0) {
                            if (bbb.position() > 0) {
                                ((Buffer) bbb).flip();
                                currentPart.add(bbb, false);
                                ((Buffer) bbb).clear();
                            }
                            currentPart.add(ByteBuffer.wrap(separator, 0, separatorPos), false);
                            separatorPos = 0;
                        }
                        bbb.put(b);
                    }
                }
            }
        }
    }

    @Override
    public Collection<ByteBuffer> removeAsCollection() throws IOException {
        List<ByteBuffer> r = new ArrayList<ByteBuffer>();
        {
            Part p = parts.get(nextPartToSend);
            if (!p.headersSent) {
                r.add(ByteBuffer.wrap(separator));
                r.add(ByteBuffer.wrap("\r\n".getBytes()));
            }
            ByteBuffer bb = p.get(4096);
            if (bb != null) {
                r.add(bb);
                if (p.bodySent) {
                    nextPartToSend++;
                }
            } else {
                nextPartToSend++;
            }
        }
        if (nextPartToSend == parts.size()) {
            r.add(ByteBuffer.wrap(separator));
            r.add(ByteBuffer.wrap("\r\n".getBytes()));
            r.add(ByteBuffer.wrap("\r\n".getBytes()));
            nextPartToSend = -1;
            sending = false;
        }
        return r;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append((getClass().isAnonymousClass()) ? getClass().getName() : getClass().getSimpleName());
        sb.append("{");
        if (getFolder() != null) {
            sb.append("\n  folder=" + getFolder());
        }
        if (!parts.isEmpty()) {
            sb.append("\n  parts=" + parts.size());
            for (Part part : parts) {
                sb.append("\n    " + part.toString().replace("\n", "\n    "));
            }
        }
        sb.append("\n}");
        return sb.toString();
    }

    public class Part {

        public Map<String, String> disposition = new LinkedHashMap<String, String>();
        public List<String> headers;
        public String name;
        public String type;
        public String remoteFile;
        public File file;
        public byte[] bytes;
        public long length;
        // runtime...
        OutputStream os; // on download
        InputStream is; // on upload
        boolean headersSent = false;
        boolean bodySent = false;

        public Part(List<String> headers) {
            for (String s : headers) {
                if (s.trim().isEmpty()) {
                    continue;
                }
                if (s.toLowerCase().contains("content-disposition:")) {
                    String[] ss = s.substring(s.indexOf(":") + 1).split(";");
                    for (String s0 : ss) {
                        s0 = s0.trim();
                        if (s0.isEmpty()) {
                            continue;
                        }
                        if (s0.contains("=")) {
                            String[] ss0 = s0.split("=");
                            String v = ss0[1].trim();
                            if (v.startsWith("\"") && v.endsWith("\"")) {
                                v = v.substring(1, v.length() - 1);
                            }
                            disposition.put(ss0[0].trim(), v);
                        } else {
                            disposition.put(s0, null);
                        }
                    }
                    if (disposition.containsKey("name")) {
                        name = disposition.get("name");
                    }
                    if (disposition.containsKey("filename")) {
                        remoteFile = disposition.get("filename");
                        file = new File(getFolder(), disposition.get("filename"));
                    }
                } else if (s.toLowerCase().contains("content-type:")) {
                    type = s.substring(s.indexOf(":") + 1).trim();
                } else {
                    if (this.headers == null) {
                        this.headers = new ArrayList<String>();
                    }
                    this.headers.add(s);
                }
            }
        }

        public Part(Part original) {
            disposition.putAll(original.disposition);
            if (original.headers != null) {
                headers = new ArrayList<>();
                headers.addAll(original.headers);
            }
            name = original.name;
            type = original.type;
            remoteFile = original.remoteFile;
            file = original.file;
            bytes = (original.bytes != null) ? Arrays.copyOf(original.bytes, original.bytes.length) : null;
            length = original.length;
            // runtime...
            os = null;
            is = null;
            headersSent = false;
            bodySent = false;
        }

        public boolean ready() {
            return os == null && length > 0;
        }

        void add(ByteBuffer bb, boolean last) throws IOException {
            // TODO: parse part content...
            if (os == null) {
                if (!http.listeners.isEmpty()) {
                    for (HttpEventListener l : http.listeners) {
                        os = l.onMutlipartFile(http, this, folder, remoteFile);
                        if (os != null) {
                            break;
                        }
                    }
                }
                if (os == null && file != null) {
                    if (getFolder() == null) {
                    } else {
                        // load to file
                        os = new FileOutputStream(file);
                    }
                }
                if (os == null) {
                    // load in memory
                    os = new ByteArrayOutputStream();
                }
            }
            if (bb != null && bb.hasRemaining()) {
                byte[] buf = new byte[bb.remaining()];
                bb.get(buf);
                os.write(buf);
                length += buf.length;
            }
            if (last) {
                os.close();
                if (os instanceof ByteArrayOutputStream) {
                    bytes = ((ByteArrayOutputStream) os).toByteArray();
                }
            }
        }

        public ByteBuffer get(int preferredSize) throws IOException {
            if (!headersSent) {
                ByteBuffer bb = ByteBuffer.allocate(1024);
                bb.put("content-disposition:".getBytes());
                for (String n : disposition.keySet()) {
                    String v = disposition.get(n);
                    bb.put(n.getBytes());
                    if (v != null) {
                        bb.put((byte) '=');
                        bb.put(v.getBytes());
                    }
                    bb.put((byte) ';');
                }
                bb.put("\r\n".getBytes());
                if (headers != null) {
                    for (String h : headers) {
                        bb.put(h.getBytes());
                        bb.put("\r\n".getBytes());
                    }
                }
                bb.put("\r\n".getBytes());
                ((Buffer) bb).flip();
                if (bytes == null && file != null && file.exists()) {
                    is = file.toURI().toURL().openStream();
                }
                headersSent = true;
                return bb;
            } else {
                if (bytes != null) {
                    ByteBuffer bb = ByteBuffer.wrap(bytes);
                    bodySent = true;
                    return bb;
                } else if (is != null) {
                    byte[] buf = new byte[(preferredSize > 0) ? preferredSize : 1024];
                    int c = is.read(buf);
                    if (c == -1) {
                        bodySent = true;
                        is.close();
                        is = null;
                        return null;
                    } else {
                        return ByteBuffer.wrap(buf, 0, c);
                    }
                }
            }
            return null;
        }

        public Object getValue() {
            if (file != null && file.exists()) {
                return file;
            } else if (file != null && bytes != null) {
                return bytes;
            } else {
                return bytes;
            }
        }

        public String getStringValue() {
            return getStringValue(null);
        }

        public String getStringValue(String encoding) {
            if (type != null && !type.contains("text")) {
                return null;
            }
            if (encoding == null) {
                encoding = "UTF-8";
            }
            Object obj = getValue();
            if (obj instanceof File) {
                try {
                    return null; //CommonTools.is2string(new FileInputStream((File) obj), encoding);
                } catch (Throwable th) {
                    return th.toString();
                }
            } else if (obj instanceof byte[]) {
                try {
                    return new String((byte[]) obj, encoding);
                } catch (Throwable th) {
                    return th.toString();
                }
            }
            return (obj != null) ? obj.toString() : null;
        }

        public Collection<String> getDispositions() {
            return disposition.keySet();
        }

        public boolean hasDisposition(String disp) {
            return disp != null && disposition.containsKey(disp);
        }

        public String getDisposition(String disp) {
            return (disp != null) ? disposition.get(disp) : null;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Part{name=" + name);
            if (type != null) {
                sb.append(", type=" + type);
            }
            if (remoteFile != null) {
                sb.append("\n  remoteFile=" + remoteFile + "\n  file=" + file + "\n  bytes=");
                if (file.exists()) {
                    sb.append(file.length());
                } else {
                    sb.append(bytes.length);
                }
            }
            if (!disposition.isEmpty()) {
                sb.append("\n  Content-Disposition");
                for (String key : disposition.keySet()) {
                    sb.append("\n    " + key);
                    String value = disposition.get(key);
                    if (value != null) {
                        sb.append("=" + value);
                    }
                }
                if (disposition.containsKey("form-data")) {
                    try {
                        Object v = getValue();
                        if (v instanceof File) {
                            sb.append("\n  Value (file): " + ((File) v).getAbsolutePath());
                        } else {
                            String s = getStringValue();
                            if (s != null) {
                                sb.append("\n  Value text: '" + ("" + s).replace("\n", "\n    ") + "'");
                            } else if (v instanceof byte[]) {
                                sb.append("\n  Value: " + ((byte[]) ((byte[]) v)).length + " bytes");
                            }
                        }
                    } catch (Throwable th) {
                    }
                }
            }
            if (headers != null) {
                sb.append("\n  Headers:");
                for (String s : headers) {
                    sb.append("\n    " + s);
                }
            }
            sb.append("\n}");
            return sb.toString();
        }
    }

    /**
     * @return the folder
     */
    public File getFolder() {
        return folder;
    }

    /**
     * @param folder the folder to set
     */
    public void setFolder(File folder) {
        this.folder = folder;
    }

    @Override
    public void fixBodyBeforeSend() throws IOException {
        super.fixBodyBeforeSend();
        sending = true;
        nextPartToSend = 0;
    }

    @Override
    public boolean isEmpty() {
        if (sending) {
            return nextPartToSend == -1;
        } else {
            return super.isEmpty();
        }
    }

}
