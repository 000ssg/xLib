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
package ssg.lib.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Array;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Stack;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.common.buffers.Bytes2Chars;
import ssg.lib.common.buffers.Chars2Bytes;

/**
 *
 * @author 000ssg
 */
public class JSON implements Cloneable {

    public static enum STATE {
        whitespace,
        name,
        value_separator,
        value,
        item_separator,
        ok
    }

    public static final String NULL = "null";
    public static final String TRUE = "true";
    public static final String FALSE = "false";
    public static final char OBJECT = '{';
    public static final char CLOSE_OBJECT = '}';
    public static final char LIST = '[';
    public static final char CLOSE_LIST = ']';
    public static final char ITEM_SEPARATOR = ',';
    public static final char VALUE_SEPARATOR = ':';
    public static final char TEXT_ESCAPE = '\\';
    public static final char COMMENT = '/';
    public static final char TEXT_EOF = (char) 0x1A;
    public static final char CHAR_EOF = (char) -1;

    STATE state = STATE.value;
    Refl refl;
    private String encoding = "UTF-8";

    public JSON() {
    }

    public JSON(Refl refl) {
        this.refl = refl;
    }

    public boolean isObject(Object o) {
        if (o instanceof String || o instanceof Number || o instanceof Boolean || o == null) {
            return false;
        }
        if (o instanceof Collection || o.getClass().isArray()) {
            return false;
        }
        return true;
    }

    public boolean isList(Object o) {
        if (o instanceof String || o instanceof Number || o instanceof Boolean || o == null) {
            return false;
        }
        return (o instanceof Collection || o.getClass().isArray());
    }

    public static class Decoder extends JSON {

        public boolean TRACE = false;
        Stack<Object> path = new Stack<>();
        StringBuilder sb = new StringBuilder();
        String name;
        Character textSeparator = null;
        boolean escaped = false;
        //
        boolean inComment = false;
        boolean inBlockComment = false;
        boolean mayBeComment = false;
        boolean mayBeEndOfComment = false;

        STATE state = STATE.value;
        Bytes2Chars b2c = new Bytes2Chars("UTF-8");
        Class[] top;
        int uescape = -1;

        public Decoder() {
        }

        public Decoder(Refl refl) {
            super(refl);
        }

        public Decoder(String encoding, Refl refl) {
            super(refl);
            if (encoding != null) {
                setEncoding(encoding);
            }
        }

        public Decoder(String encoding) {
            if (encoding != null) {
                setEncoding(encoding);
            }
        }

        @Override
        public void setEncoding(String encoding) {
            super.setEncoding(encoding);
            b2c = new Bytes2Chars(encoding);
        }

        public STATE getState() {
            return state;
        }

        ////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////// decode
        ////////////////////////////////////////////////////////////////////////////
        /**
         * Retrieve object and reset decoder.
         *
         * @param <T>
         * @return
         */
        public <T> T get() {
            try {
                Object obj = path.isEmpty() ? null : (T) path.get(0);
                if (obj != null && top != null && refl != null) {
                    for (Class cl : top) {
                        try {
                            if (cl.isAssignableFrom(obj.getClass())) {
                                break;
                            }
                            obj = refl.enrich(obj, cl);
                            break;
                        } catch (Throwable th) {
                            int a = 0;
                        }
                    }
                }
                return (T) obj;
            } finally {
                reset(null);
            }
        }

        public void reset(Class... top) {
            path.clear();
            sb.delete(0, sb.length());
            name = null;
            textSeparator = null;
            escaped = false;
            //
            inComment = false;
            inBlockComment = false;
            mayBeComment = false;
            mayBeEndOfComment = false;

            state = STATE.value;
            b2c.reset();
            this.top = top;
        }

        public Object createObject() {
            return new LinkedHashMap();
        }

        public Object createList() {
            return new ArrayList();
        }

        public boolean isObject() {
            if (path.isEmpty()) {
                return false;
            } else {
                Object o = path.peek();
                return isObject(o);
            }
        }

        public boolean isList() {
            if (path.isEmpty()) {
                return false;
            } else {
                Object o = path.peek();
                return isList(o);
            }
        }

        /**
         * bytes input. To indicate end of data and flush reminder put null
         * ByteBuffer[].
         *
         * @param bufs
         * @return
         * @throws IOException
         */
        public STATE write(ByteBuffer... bufs) throws IOException {
            b2c.write(bufs);
            if (bufs == null) {
                b2c.flush();
            }
            CharBuffer cb = b2c.read(1024);
            while (cb != null && cb.hasRemaining()) {
                write(cb);
                cb = b2c.read(1024);
            }
            return state;
        }

        /**
         * Converts accumulated chars to a JSON value. If asText - just returns
         * the chars.
         *
         * @param asText
         * @return
         * @throws IOException
         */
        public Object getValue(boolean asText) throws IOException {
            String vs = sb.toString();
            Object v = vs;
            if (!asText) {
                if (vs.isEmpty()) {
                } else if (NULL.equals(vs)) {
                    v = null;
                } else if (TRUE.equals(vs)) {
                    v = Boolean.TRUE;
                } else if (FALSE.equals(vs)) {
                    v = Boolean.FALSE;
                } else if (Character.isDigit(vs.charAt(0)) || '-' == vs.charAt(0)) {
                    // try number
                    try {
                        if (vs.contains(".") || vs.contains("e") || vs.contains("E")) {
                            v = Double.parseDouble(vs);
                        } else {

                            v = Long.parseLong(vs);
                        }
                    } catch (Throwable th) {
                        // javascript compatibility? https://forums.pentaho.com/threads/60913-javascript-long-datatype-issues/
                        if ("9223372036854776000".equals(vs)) {
                            v = Long.MAX_VALUE;
                        } else {
                            throw new IOException("Failed to parse numeric value: '" + vs + "': " + th);
                        }
                    }
                } else {
                    throw new IOException("Unrecognized JSON literal: '" + vs + "' (" + BufferTools.dump(vs.getBytes(getEncoding())).replace("\n", "\\n") + ").");
                }
            }
            sb.delete(0, sb.length());
            return v;
        }

        /**
         * Puts a value according to current state.
         *
         * @param v
         * @throws IOException
         */
        public void putValue(Object v) throws IOException {
            switch (state) {
                case whitespace:
                    if (v instanceof String) {
                        throw new IOException("Unexpected state[" + state + "]: need name or value for '" + v + "'.");
                    }
                case value:
                    if (path.isEmpty()) {
                        path.push(v);
                        if (isObject()) {
                            state = STATE.name;
                        } else if (isList()) {
                            state = STATE.value;
                        } else {
                            state = STATE.ok;
                        }
                    } else {
                        if (isObject()) {
                            if (name != null) {
                                ((Map) path.peek()).put(name, v);
                                name = null;
                                state = STATE.item_separator;
                            } else {
                                throw new IOException("Unexpected value: no name defined for object property for '" + v + "'.");
                            }
                        } else if (isList()) {
                            ((Collection) path.peek()).add(v);
                            state = STATE.item_separator;
                        } else {
                            throw new IOException("Unexpected value: need object or collection, got " + path.peek() + " for '" + v + "'.");
                        }
                        // add to path new object or list...
                        if (isObject(v)) {
                            path.add(v);
                            state = STATE.name;
                        } else if (isList(v)) {
                            path.add(v);
                            state = STATE.value;
                        }
                    }
                    break;
                case name:
                    name = "" + v;
                    state = STATE.value_separator;
                    break;
            }
        }

        /**
         * Invoked when comment is closed.
         *
         * @param comment
         */
        public void onComment(String comment) {
        }

        ////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////// decode
        ////////////////////////////////////////////////////////////////////////////
        public Decoder createDecoder(Class type) throws IOException {
            try {
                Decoder dec = (Decoder) clone();
                dec.refl = refl;
                dec.reset(type);
                return dec;
            } catch (Throwable th) {
                if (th instanceof IOException) {
                    throw (IOException) th;
                } else {
                    throw new IOException("Failed to decode", th);
                }
            }
        }

        public <T> T readObject(String s, Class type) throws IOException {
            Decoder dec = this;
            if (state != STATE.value || !path.isEmpty() || sb.length() > 0) {
                dec = createDecoder(type);
            } else {
                reset(type);
            }

            STATE st = dec.write(CharBuffer.wrap(s.toCharArray()));
            if (st == STATE.ok) {
                return dec.get();
            } else {
                throw new IOException("Incomplete data: " + st + ".");
            }
        }

        public <T> T readObject(InputStream is, Class type) throws IOException {
            Decoder dec = this;
            if (state != STATE.value || !path.isEmpty() || sb.length() > 0) {
                dec = createDecoder(type);
            } else {
                reset(type);
            }

            STATE st = dec.state;

            byte[] buf = new byte[1024];
            int c = 0;

            if (is.markSupported()) {
                is.mark(buf.length);
            }

            while ((c = is.read(buf)) != -1) {
                ByteBuffer bb = ByteBuffer.wrap(buf, 0, c);
                st = dec.write(bb);
                if (st == STATE.ok) {
                    if (bb.hasRemaining()) {
                        if (is.markSupported()) {
                            int skip = c - bb.remaining();
                            is.reset();
                            is.skip(skip);
                        }
                        int a = 0;
                    }
                    break;
                }
                if (is.markSupported()) {
                    is.mark(buf.length);
                }
            }
            if (st != STATE.ok && c == -1) {
                st = dec.write((ByteBuffer[]) null);
            }

            if (st == STATE.ok) {
                return dec.get();
            } else {
                throw new IOException("Incomplete data: " + st + ".");
            }
        }

        public <T> T readObject(Reader rdr, Class type) throws IOException {
            Decoder dec = this;
            if (state != STATE.value || !path.isEmpty() || sb.length() > 0) {
                dec = createDecoder(type);
            } else {
                reset(type);
            }

            STATE st = dec.state;

            char[] buf = new char[1024];
            int c = 0;

            if (rdr.markSupported()) {
                rdr.mark(buf.length);
            }
            while ((c = rdr.read(buf)) != -1) {
                CharBuffer cb = CharBuffer.wrap(buf, 0, c);
                st = dec.write(cb);
                if (st == STATE.ok) {
                    if (cb.hasRemaining()) {
                        if (rdr.markSupported()) {
                            int skip = c - cb.remaining();
                            rdr.reset();
                            rdr.skip(skip);
                        }
                    }
                    break;
                }
                if (rdr.markSupported()) {
                    rdr.mark(buf.length);
                }
            }
            if (st != STATE.ok && c == -1) {
                buf[0] = TEXT_EOF;
                st = dec.write(CharBuffer.wrap(buf, 0, 1));
            }

            if (st == STATE.ok) {
                return dec.get();
            } else {
                throw new IOException("Incomplete data: " + st + ".");
            }
        }

        public STATE write(CharBuffer... bufs) throws IOException {
            if (bufs != null) {
                for (CharBuffer cb : bufs) {
                    if (cb != null) {
                        while (cb.hasRemaining()) {
                            char ch = cb.get();
                            if (TRACE) {
                                System.out.println(state + ", ch='" + ch + "', cm=" + inComment + ", txt=" + (textSeparator != null) + ", sb='" + sb.toString() + "'");
                            }
                            if (inComment) {
                                switch (ch) {
                                    case '\n':
                                        if (inBlockComment) {
                                            sb.append(ch);
                                        } else {
                                            onComment(sb.toString());
                                            sb.delete(0, sb.length());
                                            inComment = false;
                                            inBlockComment = false;
                                            mayBeComment = false;
                                            mayBeEndOfComment = false;
                                        }
                                        break;
                                    case '*':
                                        if (inBlockComment) {
                                            if (mayBeEndOfComment) {
                                                sb.append(ch);
                                            } else {
                                                mayBeEndOfComment = true;
                                            }
                                        } else {
                                            sb.append(ch);
                                        }
                                        break;
                                    case '/':
                                        if (mayBeEndOfComment) {
                                            onComment(sb.toString());
                                            sb.delete(0, sb.length());
                                            inComment = false;
                                            inBlockComment = false;
                                            mayBeComment = false;
                                            mayBeEndOfComment = false;
                                        }
                                        break;
                                    default:
                                        if (mayBeEndOfComment) {
                                            sb.append('*');
                                            mayBeEndOfComment = false;
                                        }
                                        sb.append(ch);
                                }
                                continue;
                            }
                            if (textSeparator != null) {
                                if (escaped) {
                                    switch (ch) {
                                        case 'n':
                                            sb.append('\n');
                                            break;
                                        case 'r':
                                            sb.append('\r');
                                            break;
                                        case 't':
                                            sb.append('\t');
                                            break;
                                        case 'f':
                                            sb.append('\f');
                                            break;
                                        case 'b':
                                            sb.append('\b');
                                            break;
                                        case '\\':
                                            sb.append('\\');
                                            break;
                                        case '"':
                                            sb.append('"');
                                            break;
                                        case '\'':
                                            sb.append('\'');
                                            break;
                                        case '/':
                                            sb.append('/');
                                            break;
                                        case 'u':
                                            uescape = 0;
                                            break;
                                        default:
                                            throw new IOException("Unknown escape sequence: \\" + ch + ": '" + sb.toString() + "'.");
                                    }
                                    escaped = false;
                                    continue;
                                } else if (ch == TEXT_ESCAPE) {
                                    escaped = true;
                                    continue;
                                }
                                if (ch == textSeparator) {
                                    putValue(getValue(true));
                                    textSeparator = null;
                                    uescape = -1;
                                } else {
                                    sb.append(ch);
                                    if (uescape != -1) {
                                        uescape++;
                                        if (uescape == 4) {
                                            int ech = Integer.parseInt(sb.substring(sb.length() - 4, sb.length()), 16);
                                            sb.delete(sb.length() - 4, sb.length());
                                            sb.append((char) ech);
                                            uescape = -1;
                                        }
                                    }
                                }
                            } else {
                                switch (ch) {
                                    case '/':
                                        if (mayBeComment) {
                                            inComment = true;
                                            inBlockComment = false;
                                            mayBeEndOfComment = false;
                                        } else {
                                            if (sb.length() > 0) {
                                                putValue(getValue(false));
                                            }
                                            mayBeComment = true;
                                        }
                                        break;
                                    case '*':
                                        if (mayBeComment) {
                                            inComment = true;
                                            inBlockComment = true;
                                            mayBeEndOfComment = false;
                                        } else {
                                            throw new IOException("Unexpected '" + ch + "'");
                                            //sb.append(ch);
                                        }
                                        break;
                                    case ' ':
                                    case '\r':
                                    case '\n':
                                    case '\t':
                                    case '\f':
                                        if (sb.length() > 0) {
                                            putValue(getValue(false));
                                        }
                                        break;
                                    case '"':
                                    case '\'':
                                        if (sb.length() > 0) {
                                            putValue(getValue(false));
                                        }
                                        textSeparator = ch;
                                        break;
                                    case OBJECT:
                                        if (sb.length() > 0) {
                                            putValue(getValue(false));
                                        }
                                        switch (state) {
                                            case value:
                                                putValue(createObject());
                                                break;
                                            default:
                                                throw new IOException("Unexpected object, expecting '" + state + "'.");
                                        }
                                        break;
                                    case CLOSE_OBJECT:
                                        if (sb.length() > 0) {
                                            putValue(getValue(false));
                                        }
                                        switch (state) {
                                            case name:
                                                if (!((Map) path.peek()).isEmpty()) {
                                                    throw new IOException("Unexpected object close. Need property name after separator!");
                                                }
                                            case item_separator:
                                                if (path.size() == 1) {
                                                    state = STATE.ok;
                                                } else {
                                                    path.pop();
                                                    if (path.peek() instanceof Map || path.peek() instanceof Collection) {
                                                        state = STATE.item_separator;
                                                    } else {
                                                        throw new IOException("Unexpected parent: need object of collection, got " + path.peek());
                                                    }
                                                }
                                                break;
                                            default:
                                                throw new IOException("Unexpected collection close. Expecting '" + state + "'.");
                                        }
                                        break;
                                    case LIST:
                                        if (sb.length() > 0) {
                                            putValue(getValue(false));
                                        }
                                        switch (state) {
                                            case value:
                                                putValue(createList());
                                                break;
                                            default:
                                                throw new IOException("Unexpected list, expecting '" + state + "'.");
                                        }
                                        break;
                                    case CLOSE_LIST:
                                        if (sb.length() > 0) {
                                            putValue(getValue(false));
                                        }
                                        switch (state) {
                                            case value:
                                                if (!((Collection) path.peek()).isEmpty()) {
                                                    throw new IOException("Unexpected collection close. Need item after separator!");
                                                }
                                            case item_separator:
                                                if (path.size() == 1) {
                                                    state = STATE.ok;
                                                } else {
                                                    path.pop();
                                                    if (path.peek() instanceof Map || path.peek() instanceof Collection) {
                                                        state = STATE.item_separator;
                                                    } else {
                                                        throw new IOException("Unexpected parent: need object of collection, got " + path.peek());
                                                    }
                                                }
                                                break;
                                            default:
                                                throw new IOException("Unexpected collection close. Expecting '" + state + "'.");
                                        }
                                        break;
                                    case VALUE_SEPARATOR:
                                        if (sb.length() > 0) {
                                            throw new IOException("Unexpected text before value separator. '" + sb + "'");
                                        }
                                        switch (state) {
                                            case value_separator:
                                                state = STATE.value;
                                                break;
                                            default:
                                                throw new IOException("Unexpected value separator. Need '" + state + "'");
                                        }
                                        break;
                                    case ITEM_SEPARATOR:
                                        if (sb.length() > 0) {
                                            putValue(getValue(false));
                                        }
                                        switch (state) {
                                            case item_separator:
                                                if (path.peek() instanceof Map) {
                                                    state = STATE.name;
                                                } else if (path.peek() instanceof Collection) {
                                                    state = STATE.value;
                                                } else {
                                                    throw new IOException("Unexpected item separator. Need '" + state + "'");
                                                }
                                                break;
                                            default:
                                                throw new IOException("Unexpected item separator. Need '" + state + "'");
                                        }
                                        break;
                                    case CHAR_EOF:
                                    case TEXT_EOF:
                                        if (sb.length() > 0) {
                                            putValue(getValue(false));
                                        }
                                        if (path.size() == 1) {
                                            state = STATE.ok;
                                        } else {
                                            throw new IOException("Unexpected EOF");
                                        }
                                        break;
                                    default:
                                        sb.append(ch);
                                }
                            }
                            if (STATE.ok == state) {
                                break;
                            }
                        }
                    }
                    if (STATE.ok == state) {
                        break;
                    }

                }
            }
            return state;
        }

        @Override
        public String toString() {
            return ((getClass().isAnonymousClass()) ? getClass().getName() : getClass().getSimpleName())
                    + "{"
                    + "\n  state=" + state
                    + "\n  path=" + path
                    + "\n  sb=" + sb.toString().replace("\n", "\n  ")
                    + "\n  name=" + name
                    + "\n  textSeparator=" + textSeparator
                    + "\n  escaped=" + escaped
                    + "\n  inComment=" + inComment
                    + "\n  inBlockComment=" + inBlockComment
                    + "\n  mayBeComment=" + mayBeComment
                    + "\n  mayBeEndOfComment=" + mayBeEndOfComment
                    + "\n  b2c=" + b2c.toString().replace("\n", "\n  ")
                    + "\n  refl=" + refl
                    + "\n}";
        }

    }

    public static class Encoder extends JSON {

        Chars2Bytes c2b = new Chars2Bytes();
        Iterator<CharBuffer> iterator;
        CharBuffer buffer = CharBuffer.allocate(1024);

        // tree iterator...
        char[] indent = new char[4096];
        int indentSize = 2;
        String line = "\n";

        {
            Arrays.fill(indent, ' ');
        }

        public class TreeIterator implements Iterator<CharBuffer> {

            // stack keeps object-iterator pairs for OBJECT and LIST instances
            Stack<Object[]> stack = new Stack<>();
            // internal cache
            List<CharBuffer> sequence = new ArrayList<>();

            public TreeIterator(Object obj) {
                push(obj);
            }

            /**
             * Cascaded push...
             *
             * @param obj
             */
            void push(Object obj) {
                if (obj == null) {
                    sequence.add(CharBuffer.wrap(NULL));
                } else if (obj instanceof String || obj.getClass().isEnum()) {
                    if (!(obj instanceof String)) {
                        obj = obj.toString();
                    }
                    sequence.add(CharBuffer.wrap("\""));
                    obj = ((String) obj)
                            .replace("\\", "\\\\")
                            .replace("/", "\\/")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r")
                            .replace("\t", "\\t")
                            .replace("\f", "\\f")
                            .replace("\b", "\\b")
                            .replace("\"", "\\\"");
                    sequence.add(CharBuffer.wrap((String) obj));
                    sequence.add(CharBuffer.wrap("\""));
                } else if (obj instanceof Number) {
                    sequence.add(CharBuffer.wrap(((Number) obj).toString()));
                } else if (obj instanceof Boolean) {
                    sequence.add(CharBuffer.wrap(((Boolean) obj) ? TRUE : FALSE));
                } else if (obj instanceof byte[]) {
                    Base64.Encoder b64 = Base64.getEncoder();
                    obj = new String(b64.encode((byte[]) obj));
                    sequence.add(CharBuffer.wrap("\""));
                    sequence.add(CharBuffer.wrap((String) obj));
                    sequence.add(CharBuffer.wrap("\""));
                } else if (isObject(obj)) {
                    sequence.add(CharBuffer.wrap("" + OBJECT));
                    Iterator it = iterator(obj);
                    if (it.hasNext()) {
                        stack.push(new Object[]{obj, it});
                        if (indentSize > 0) {
                            sequence.add(CharBuffer.wrap(line));
                            sequence.add(CharBuffer.wrap(indent, 0, stack.size() * indentSize));
                        }
                        Object n = it.next();
                        push(n);
                        sequence.add(CharBuffer.wrap("" + VALUE_SEPARATOR));
                        if (obj instanceof Map) {
                            push(((Map) obj).get((String) n));
                        } else {
                            push(refl.value(obj, (String) n));
                        }
                    } else {
                        sequence.add(CharBuffer.wrap("" + CLOSE_OBJECT));
                    }
                } else if (isList(obj)) {
                    sequence.add(CharBuffer.wrap("" + LIST));
                    Iterator it = iterator(obj);
                    if (it.hasNext()) {
                        stack.push(new Object[]{obj, it});
                        if (indentSize > 0) {
                            sequence.add(CharBuffer.wrap(line));
                            sequence.add(CharBuffer.wrap(indent, 0, stack.size() * indentSize));
                        }
                        push(it.next());
                    } else {
                        sequence.add(CharBuffer.wrap("" + CLOSE_LIST));
                    }
                }
            }

            @Override
            public boolean hasNext() {
                if (sequence.isEmpty()) {
                    return false;
                }
                if (sequence.size() > 1) {
                    return true;
                }
                CharBuffer cb = sequence.get(0);
                if (!cb.hasRemaining() && stack.isEmpty()) {
                    return false;
                } else {
                    return true;
                }
            }

            @Override
            public CharBuffer next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                CharBuffer cb = sequence.get(0);
                if (!cb.hasRemaining()) {
                    sequence.remove(0);
                    cb = sequence.isEmpty() ? null : sequence.get(0);
                }

                if (cb == null) {
                    if (stack.isEmpty()) {
                        throw new NoSuchElementException();
                    } else {
                        Object[] oo = stack.peek();
                        Iterator it = (Iterator) oo[1];
                        if (!it.hasNext()) {
                            stack.pop();
                            // close structure
                            if (indentSize > 0) {
                                sequence.add(CharBuffer.wrap(line));
                                sequence.add(CharBuffer.wrap(indent, 0, stack.size() * indentSize));
                            }
                            if (isObject(oo[0])) {
                                sequence.add(CharBuffer.wrap("" + CLOSE_OBJECT));
                            } else if (isList(oo[0])) {
                                sequence.add(CharBuffer.wrap("" + CLOSE_LIST));
                            } else {
                                throw new NoSuchElementException("Cannot close unrecognized structure (neither object nor list): " + oo[0]);
                            }
                        } else {
                            sequence.add(CharBuffer.wrap("" + ITEM_SEPARATOR));
                            if (indentSize > 0) {
                                sequence.add(CharBuffer.wrap(line));
                                sequence.add(CharBuffer.wrap(indent, 0, stack.size() * indentSize));
                            }
                            if (isObject(oo[0])) {
                                //System.out.println("OO: "+oo[0].getClass());
                                Object n = it.next();
                                sequence.add(CharBuffer.wrap("\""));
                                sequence.add(CharBuffer.wrap((String) n));
                                sequence.add(CharBuffer.wrap("\""));
                                sequence.add(CharBuffer.wrap("" + VALUE_SEPARATOR));
                                if (oo[0] instanceof Map) {
                                    push(((Map) oo[0]).get(n));
                                } else {
                                    push(refl.value(oo[0], (String) n));
                                }
                            } else if (isList(oo[0])) {
                                push(it.next());
                            } else {
                                throw new NoSuchElementException("Cannot encode item in unrecognized structure (neither object nor list): " + oo[0]);
                            }
                        }
                    }
                    cb = sequence.get(0);
                }

                return cb;
            }

            @Override
            public String toString() {
                return ((getClass().isAnonymousClass()) ? getClass().getName() : getClass().getSimpleName())
                        + "{"
                        + "\n  stack=" + stack
                        + "\n  sequence=" + sequence
                        + "\n}";
            }

        }

        public Encoder() {
        }

        public Encoder(Refl refl) {
            super(refl);
        }

        public Encoder(String encoding, Refl refl) {
            super(refl);
            if (encoding != null) {
                setEncoding(encoding);
            }
        }

        public Encoder(String encoding) {
            if (encoding != null) {
                setEncoding(encoding);
            }
        }

        @Override
        public void setEncoding(String encoding) {
            super.setEncoding(encoding);
            c2b = new Chars2Bytes(encoding);
        }

        public Encoder createEncoder(Object root) throws IOException {
            try {
                Encoder enc = (Encoder) clone();
                enc.refl = refl;
                enc.put(root);
                return enc;
            } catch (Throwable th) {
                if (th instanceof IOException) {
                    throw (IOException) th;
                } else {
                    throw new IOException("Failed to create encoder for " + root, th);
                }
            }
        }

        public void put(Object root) {
            c2b.reset();
            ((Buffer) buffer).clear();
            iterator = new TreeIterator(root);
            state = STATE.value;
        }

        Iterator iterator(final Object obj) {
            //System.out.println("Refl:iterator:" + obj);
            if (obj == null) {
                return null;
            } else if (isObject(obj)) {
                if (obj instanceof Map) {
                    return ((Map) obj).keySet().iterator();
                } else {
                    return (refl != null) ? refl.names(obj).iterator() : new Iterator() {
                        @Override
                        public boolean hasNext() {
                            return false;
                        }

                        @Override
                        public Object next() {
                            throw new NoSuchElementException();
                        }
                    };
                }
            } else if (isList(obj)) {
                if (obj instanceof Collection) {
                    return ((Collection) obj).iterator();
                } else {
                    return new Iterator() {
                        int size = Array.getLength(obj);
                        int pos = 0;

                        @Override
                        public boolean hasNext() {
                            return pos < size;
                        }

                        @Override
                        public Object next() {
                            if (hasNext()) {
                                return Array.get(obj, pos++);
                            } else {
                                throw new NoSuchElementException();
                            }
                        }
                    };
                }
            } else {
                throw new NoSuchElementException("Unsupported type for iterator: " + obj);
            }
        }

        ////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////// encode
        ////////////////////////////////////////////////////////////////////////////
        /**
         * Convert object to string
         *
         * @param obj
         * @return
         * @throws IOException
         */
        public String writeObject(Object obj) throws IOException {
            Encoder enc = this;
            if (iterator != null && iterator.hasNext()) {
                enc = createEncoder(obj);
            } else {
                put(obj);
            }

            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            CharBuffer cb = CharBuffer.wrap(buf);
            int c = enc.read(cb);
            while (c > 0) {
                sb.append(buf, 0, c);
                ((Buffer) cb).clear();
                c = enc.read(cb);
            }
            return sb.toString();
        }

        /**
         * Write object as string to writer
         *
         * @param obj
         * @param wr
         * @return
         * @throws IOException
         */
        public long writeObject(Object obj, Writer wr) throws IOException {
            Encoder enc = this;
            if (iterator != null && iterator.hasNext()) {
                enc = createEncoder(obj);
            } else {
                put(obj);
            }

            long cc = 0;
            char[] buf = new char[1024];
            CharBuffer cb = CharBuffer.wrap(buf);
            int c = read(cb);
            while (c > 0) {
                cc += c;
                wr.write(buf, 0, c);
                ((Buffer) cb).clear();
                c = read(cb);
            }
            return cc;
        }

        /**
         * write object as string to byte stream
         *
         * @param obj
         * @param os
         * @return
         * @throws IOException
         */
        public long writeObject(Object obj, OutputStream os) throws IOException {
            Encoder enc = this;
            if (iterator != null && iterator.hasNext()) {
                enc = createEncoder(obj);
            } else {
                put(obj);
            }

            long cc = 0;
            byte[] buf = new byte[1024];
            ByteBuffer cb = ByteBuffer.wrap(buf);
            int c = read(cb);
            while (c > 0) {
                cc += c;
                os.write(buf, 0, c);
                ((Buffer) cb).clear();
                c = read(cb);
            }
            return cc;
        }

        public int read(ByteBuffer buf) throws IOException {
            int cc = 0;
            int c = read(buffer);
            if (c == -1) {
                cc = -1;
            }
            while (c > 0) {
                ((Buffer) buffer).flip();
                c2b.write(buffer);

                c = c2b.read(buf);
                if (c >= 0) {
                    cc += c;
                }

                ((Buffer) buffer).clear();
                if (c < 0 || !buf.hasRemaining()) {
                    break;
                }
                c = read(buffer);
                if (c < 0) {
                    c2b.flush();
                    c = c2b.read(buf);
                    if (c > 0) {
                        cc += c;
                    }
                    break;
                }
            }
            return cc;
        }

        public ByteBuffer read(int expectedSize) throws IOException {
            ByteBuffer bb = ByteBuffer.allocate(4096);
            if (read(bb) >= 0) {
                ((Buffer) bb).flip();
                return bb;
            }
            return null;
        }

        public int read(CharBuffer buf) throws IOException {
            if (!iterator.hasNext()) {
                return -1;
            }
            int c = 0;
            CharBuffer cb = iterator.next();
            while (cb.hasRemaining() && buf.hasRemaining()) {
                while (cb.hasRemaining() && buf.hasRemaining()) {
                    buf.put(cb.get());
                    c++;
                }
                if (buf.hasRemaining() && iterator.hasNext()) {
                    cb = iterator.next();
                }
            }
            return c;
        }

        public CharBuffer readChars(int expectedSize) throws IOException {
            CharBuffer bb = CharBuffer.allocate(4096);
            if (read(bb) >= 0) {
                ((Buffer) bb).flip();
                return bb;
            }
            return null;
        }

        @Override
        public String toString() {
            return ((getClass().isAnonymousClass()) ? getClass().getName() : getClass().getSimpleName())
                    + "{"
                    + "\n  indent=" + indent
                    + "\n  indentSize=" + indentSize
                    + "\n  line=" + line
                    + "\n  c2b=" + c2b
                    + "\n  refl=" + refl
                    + "\n  buffer=" + buffer
                    + (iterator != null ? "\n  iterator=" + iterator.toString().replace("\n", "\n  ") : "")
                    + "\n}";
        }
    }

    /**
     * @return the encoding
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * @param encoding the encoding to set
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }
}
