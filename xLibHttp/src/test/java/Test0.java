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

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author 000ssg
 */
public class Test0 {

    public static void toFormParameterValue(String pn, String pv, Object obj) {
        String[] pns = pn.split("\\[");
        Integer[] pnt = new Integer[pns.length];
        for (int i = 1; i < pns.length; i++) {
            pns[i] = pns[i].substring(0, pns[i].length() - 1);
            try {
                if (!pns[i].isEmpty()) {
                    pnt[i] = Integer.parseInt(pns[i]);
                }
            } catch (Throwable th) {
            }
        }
        if (pns.length == 1) {
            Map map = (Map) obj;
            map.put(pn, pv);
        } else {
            for (int i = 0; i < pns.length - 1; i++) {
                if (pns[i].isEmpty()) {
                    // terminal array value
                    int a=0;
                } else if (pnt[i] != null) {
                    // intermediate indexed array value
                    if(obj instanceof List){
                        List l=(List)obj;
                        if(pnt[i]<l.size()){
                            obj=l.get(pnt[i]);
                        }else if(pnt[i]==l.size()){
                            if (pns[i + 1].isEmpty() || pnt[i + 1] != null) {
                                obj= new ArrayList();
                                l.add(obj);
                            } else {
                                obj = new LinkedHashMap();
                                l.add(obj);
                            }
                        }
                    }else{
                        int a=0;
                    }
                } else {
                    // intermediate/terminal object property
                    if (obj instanceof Map) {
                        Object oo = ((Map) obj).get(pns[i]);
                        if (oo == null) {
                            if (pns[i + 1].isEmpty() || pnt[i + 1] != null) {
                                oo = new ArrayList();
                            } else {
                                oo = new LinkedHashMap();
                            }
                            ((Map) obj).put(pns[i], oo);
                        }
                        obj = oo;
                        int a = 0;
                    } else if (obj instanceof List) {
                        int a = 0;
                    } else {
                        // ???
                        int a = 0;
                    }
                }
            }
        }
        int last = pns.length - 1;

        if (pns[last].isEmpty()) {
            if (obj instanceof Collection) {
                ((Collection) obj).add(pv);
            } else {
                int a = 0;
            }
        } else if (pnt[last] != null) {
            int a = 0;
        } else {
            if (obj instanceof Map) {
                ((Map) obj).put(pns[last], pv);
            } else {
                int a = 0;
            }
        }
    }

    public static void toFormParameterValue0(String pn, String pv, Object old) {
        String[] pns = pn.split("\\[");
        Integer[] pnt = new Integer[pns.length];
        for (int i = 1; i < pns.length; i++) {
            pns[i] = pns[i].substring(0, pns[i].length() - 1);
            try {
                if (!pns[i].isEmpty()) {
                    pnt[i] = Integer.parseInt(pns[i]);
                }
            } catch (Throwable th) {
            }
        }
        if (pns.length == 1) {
            Map map = (Map) old;
            map.put(pn, pv);
        } else {
            Object obj = ((Map) old).get(pns[0]);
            int last = pns.length - 1;
            boolean lastIsMissing = false;
            for (int i = 1; i < last; i++) {
                // check if int array
                // eval current level object type
                if (pns[i].isEmpty()) {
                    // just array (must be terminal?)
                    throw new RuntimeException("non-terminal...");
//                    if (obj == null) {
//                        obj = new ArrayList();
//                        ((Map) old).put(pns[i - 1], obj);
//                    }
                } else if (pnt[i] != null) {
                    // list
                    if (obj == null) {
                        obj = new ArrayList();
                        if (pnt[i - 1] != null && old instanceof List) {
                            ((List) old).add(obj);
                        } else if (pnt[i - 1] == null && old instanceof Map) {
                            ((Map) old).put(pns[i - 1], obj);
                        }
                    } else if (obj instanceof Map && pnt[i] == null) {
                        obj = ((Map) obj).get(pns[i]);
                    } else if (obj instanceof List && pnt[i] != null && pnt[i] < ((List) obj).size()) {
                        obj = ((List) obj).get(pnt[i]);
                    } else if (obj instanceof List && pnt[i - 1] != null && pnt[i - 1] == ((List) obj).size()) {
                        obj = null;
                    }
                } else {
                    if (obj instanceof Map) {
                        Map m = (Map) obj;
                        if (pnt[i - 1] == null) {
                            obj = new LinkedHashMap();
                            m.put(pns[i - 1], obj);
                            m = (Map) obj;
                        }
                        if (m.containsKey(pns[i])) {
                            obj = m.get(pns[i]);
                        } else {
                            obj = new LinkedHashMap();
                            m.put(pns[i], obj);
                        }
                    } else {
                        if (obj == null) {
                            obj = new LinkedHashMap();
                            ((Map) old).put(pns[i - 1], obj);
                            old = obj;
                        } else {
                            obj = old;
                        }
                        //lastIsMissing = true;//else throw new RuntimeException("Map parent object is expected.");
                    }
                }
                old = obj;
            }

            if (pns[last].isEmpty()) {
                // just array (must be terminal?)
                if (obj == null) {
                    if (old instanceof Map) {
                        obj = new ArrayList();
                        ((Map) old).put(pns[last - 1], obj);
                    } else if (old instanceof List) {
                        obj = old;
                    } else {
                        obj = new ArrayList();
                        ((Map) old).put(pns[last - 1], obj);
                    }
                } else if (obj instanceof Map) {
                    List l = new ArrayList();
                    ((Map) obj).put(pns[last - 1], l);
                    obj = l;
                }
                ((List) obj).add(pv);
            } else if (pnt[last] != null) {
                // list
                if (obj == null) {
                    obj = new ArrayList();
                    ((Map) old).put(pns[last - 1], obj);
                }
                if (pnt[last] == ((List) obj).size()) {
                    ((List) obj).add(pv);
                } else {
                    ((List) obj).set(pnt[last], pv);
                }
            } else {
                // map
                if (obj == null) {
                    obj = new LinkedHashMap();
                    ((Map) old).put(pns[last - 1], obj);
                } else if (obj instanceof Map) {
                    Map m = (Map) obj;
                    obj = new LinkedHashMap();
                    m.put(pns[last - 1], obj);
                } else if (obj instanceof List) {
                    Map m = new LinkedHashMap();
                    ((List) obj).add(m);
                    obj = m;
                }
                ((Map) obj).put(pns[last], pv);
            }
        }
    }

    public static void main(String... args) throws Exception {
        Map params = new LinkedHashMap();

        String text = "added%5B0%5D%5Bid%5D=added_1570027886859&added%5B0%5D%5Bfrom%5D=1575151200000&added%5B0%5D%5Bto%5D=1583013599999&added%5B0%5D%5Bstart%5D=28800000&added%5B0%5D%5Bduration%5D=3600000&added%5B0%5D%5Bend%5D=32400000&added%5B0%5D%5Broom%5D=Kuparitie+1&added%5B0%5D%5Bname%5D=Koreografia+ballet&added%5B0%5D%5BweekDays%5D%5B%5D=2&added%5B0%5D%5BweekDays%5D%5B%5D=3";

        //text = "modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5Bhidden%5D=false&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5BconfSize%5D=0&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5Bstart%5D=28800000&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5Bicon%5D=icons8-pilates-90.png&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5BmaxSize%5D=10&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5BmyState%5D=&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5Broom%5D=Kuparitie+1&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5Bduration%5D=3600000&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5Bsize%5D=0&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5BweekDays%5D%5B%5D=2&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5BweekDays%5D%5B%5D=3&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5BweekDays%5D%5B%5D=5&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5Btrainer%5D=jana%40kuntajana.fi&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5Bname%5D=Koreografia+ballet&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5Bcourse%5D=Pilates&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5Bfrom%5D=1575151200000&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5Bend%5D=32400000&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5Bto%5D=1583013599999&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5Bid%5D=dccf25c1-0120-46af-9a65-79208e2dee96&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5BshortName%5D=Plt&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5Bgroup%5D=Pilates&modified%5Bdccf25c1-0120-46af-9a65-79208e2dee96%5D%5Bparticipants%5D=";

        List<String[]> fps = new ArrayList<>();
        String encoding = "UTF-8";
        String[] fparams = text.split("&");
        for (String fparam : fparams) {
            int idx = fparam.indexOf("=");
            if (idx == -1) {
                // param without value
                String pn = URLDecoder.decode(fparam, encoding);
                fps.add(new String[]{pn, null});
            } else {
                String pn = fparam.substring(0, idx);
                String pv = fparam.substring(idx + 1);
                try {
                    pn = URLDecoder.decode(pn, encoding);
                    pv = URLDecoder.decode(pv, encoding);
                    fps.add(new String[]{pn, pv});
                } catch (Throwable th) {
                    encoding = "ISO-8859-1"; // enforce default encoding for URLDecoder...
                    pn = URLDecoder.decode(pn, encoding);
                    pv = URLDecoder.decode(pv, encoding);
                    fps.add(new String[]{pn, pv});
                }
            }
        }

        if(1==0){
        fps.clear();
        for (String pn : new String[]{"a", "b[]", "c[0][]", "c[1][]", "c[2][id]", "d[id][main]"}) {
            fps.add(new String[]{pn, "ooo"});
        }}

        for (String[] pnv : fps) {
            String pn = pnv[0];
            String pv = pnv[1];
            System.out.println("----------------------------\n-- params: " + params + "\n-- pn: " + pn);
            toFormParameterValue(pn, pv, params);
        }
        System.out.println("----------------------------\n-- params: " + params);
    }
}
