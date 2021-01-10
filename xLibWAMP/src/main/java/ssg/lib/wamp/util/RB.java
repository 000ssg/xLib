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
package ssg.lib.wamp.util;

import java.lang.reflect.Array;
import java.security.InvalidParameterException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 *
 * @author sesidoro
 */
public class RB {
    
    public static final String TYPE = "type";
    public static final String NAME = "name";
    public static final String PROC = "proc";
    public static final String PROCEDURE = "procedure";
    public static final String FUNCTION = "function";
    public static final String PARAMETERS = "parameters";
    public static final String OPTIONAL = "optional";
    public static final String ORDER = "order";
    public static final String RETURNS = "returns";
    
    Map<String, Object> data = WAMPTools.createDict(null);
    
    public RB() {
    }
    
    public Map<String, Object> data() {
        return data;
    }
    
    public <T> T data(Object... keys) throws InvalidParameterException {
        Object d = data;
        if (keys != null && keys.length > 0) {
            int ord = 0;
            for (Object key : keys) {
                if (!(key instanceof Number)) {
                    if (d instanceof Map) {
                        d = ((Map) d).get(key);
                    } else {
                        throw new InvalidParameterException("Invalid key[" + ord + "]: need map for key '" + key + "'.");
                    }
                } else {
                    if (d instanceof List) {
                        try {
                            d = ((List) d).get(((Number) key).intValue());
                        } catch (Throwable th) {
                            throw new InvalidParameterException("Error for key[" + ord + "] in list(" + ((List) d).size() + ") for key " + key + ": " + th);
                        }
                    } else if (d.getClass().isArray()) {
                        try {
                            d = Array.get(d, ((Number) key).intValue());
                        } catch (Throwable th) {
                            throw new InvalidParameterException("Error for key[" + ord + "] in array(" + Array.getLength(d) + ") for key " + key + ": " + th);
                        }
                    } else {
                        throw new InvalidParameterException("Invalid key[" + ord + "]: need list/array for key " + key + ".");
                    }
                }
                ord++;
            }
        }
        return (T) d;
    }
    
    @Override
    public String toString() {
        return toString(data);
    }
    
    public static String toString(Object item) {
        StringBuilder sb = new StringBuilder();
        if (item instanceof Map) {
            sb.append("{");
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) item).entrySet()) {
                sb.append("\n  ");
                sb.append(entry.getKey());
                sb.append(": ");
                sb.append(toString(entry.getValue()).replace("\n", "\n  "));
            }
            if (sb.length() > 1) {
                sb.append("\n");
            }
            sb.append("}");
        } else if (item instanceof List) {
            sb.append("[");
            for (Object li : (List) item) {
                sb.append("\n  ");
                sb.append(toString(li).replace("\n", "\n  "));
            }
            if (sb.length() > 1) {
                sb.append("\n");
            }
            sb.append("]");
        } else {
            sb.append(item);
        }
        return sb.toString();
    }
    
    public RB procedure(Collection<RB> rbs) {
        if (rbs != null) {
            return procedure(rbs.toArray(new RB[rbs.size()]));
        }
        return this;
    }
    
    public RB procedure(RB... rbs) {
        if (rbs != null) {
            for (RB rb : rbs) {
                if (rb == null) {
                    continue;
                }
                String type = (String) rb.data.get(TYPE);
                String name = (String) rb.data.get(NAME);
                if (name != null && (PROCEDURE.equals(type) || FUNCTION.equals(type))) {
                    Map<String, Object> procs = (Map) data.get(PROC);
                    if (procs == null) {
                        procs = WAMPTools.createDict(null);
                        data.put(PROC, procs);
                    }
                    List<Map<String, Object>> ps = (List) procs.get(name);
                    if (ps == null) {
                        ps = WAMPTools.createList();
                        procs.put(name, ps);
                    }
                    ps.add(rb.data);
                    rb.data.remove(NAME);
                } else {
                    throw new RBException("Cannot add unnamed procedure or function: " + toString(rb));
                }
            }
        }
        return this;
    }
    
    public RB element(Collection<RB> rbs) {
        if (rbs != null) {
            return element(rbs.toArray(new RB[rbs.size()]));
        }
        return this;
    }
    
    public RB element(RB... rbs) {
        if (rbs != null) {
            for (RB rb : rbs) {
                if (rb == null) {
                    continue;
                }
                String type = (String) rb.data.get(TYPE);
                String name = (String) rb.data.get(NAME);
                if (name != null) {
                    Map<String, Object> els = (Map) data.get(type);
                    if (els == null) {
                        els = WAMPTools.createDict(null);
                        data.put(type, els);
                    }
                    els.put(name, rb.data);
                    rb.data.remove(NAME);
                } else {
                    throw new RBException("Cannot add element without name: " + toString(rb));
                }
            }
        }
        return this;
    }
    
    public RB value(String name, Object value) {
        data.put(name, value);
        return this;
    }
    
    public RB noValue(String name) {
        data.remove(name);
        return this;
    }
    
    public RB parameter(int order, String name, String type, boolean optional) {
        String ct = (String) data.get(TYPE);
        if (PROCEDURE.equals(ct) || FUNCTION.equals(ct)) {
            List<Map<String, Object>> ps = (List) data.get(PARAMETERS);
            if (ps == null) {
                ps = WAMPTools.createList();
                data.put(PARAMETERS, ps);
            }
            Map<String, Object> pv = WAMPTools.createDict(TYPE, type, OPTIONAL, optional);
            if (name != null) {
                pv.put(NAME, name);
            }
            if (order >= 0) {
                pv.put(ORDER, order);
            }
            ps.add(pv);
        } else {
            throw new RBException("Cannot add parameter to non procedure or function item in: " + toString(data));
        }
        return this;
    }
    
    public RB returns(String type) {
        String ct = (String) data.get(TYPE);
        if (FUNCTION.equals(ct)) {
            data.put(RETURNS, type);
        } else {
            throw new RBException("Cannot add returns type to non function item in: " + toString(data));
        }
        return this;
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////// root item builder
    ////////////////////////////////////////////////////////////////////////
    public static RB root() {
        RB r = new RB();
        return r;
    }
    
    public static RB root(String type, String name) {
        RB r = new RB();
        r.data.put(TYPE, type);
        if (name != null) {
            r.data.put(NAME, name);
        }
        return r;
    }
    
    public static RB procedure(String name) {
        return root(PROCEDURE, name);
    }
    
    public static RB function(String name) {
        return root(FUNCTION, name);
    }
    
    public static RB type(String name) {
        return root("type", name);
    }
    
    public static RB error(String name) {
        return root("error", name);
    }
    
    public static RB pub(String name) {
        return root("pub", name);
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////// utilities
    ////////////////////////////////////////////////////////////////////////////
    public static class RBException extends RuntimeException {
        
        public RBException() {
        }
        
        public RBException(String message) {
            super(message);
        }
        
        public RBException(String message, Throwable cause) {
            super(message, cause);
        }
        
        public RBException(Throwable cause) {
            super(cause);
        }
        
    }
}
