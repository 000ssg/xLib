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
package ssg.lib.http.dp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.common.JavaResourcesList;
import ssg.lib.common.Matcher;
import ssg.lib.common.Replacement;
import ssg.lib.http.HttpDataProcessor;
import ssg.lib.http.HttpResource;
import ssg.lib.http.base.HttpData;
import ssg.lib.common.MatchScanner;

/**
 *
 * @author 000ssg
 */
public class HttpResourceCollection implements HttpResource, MatchScanner<String, String> {

    public static boolean DEBUG = false;

    long timestamp = System.currentTimeMillis();
    String rootPath; // local path
    String path; // published path
    Map<String, HttpResource> collection = new LinkedHashMap<>();
    Map<String, String> contentTypes = new LinkedHashMap<String, String>() {
        {
            put(".html", "text/html");
            put(".incl", "text/plain");
            put(".jpeg", "image/jpeg");
            put(".jpg", "image/jpeg");
            put(".png", "image/png");
            put(".tif", "image/tiff");
            put(".tiff", "image/tiff");
            put(".bmp", "image/bmp");
            put(".css", "text/css");
            put(".js", "text/javascript");
            put(".json", "application/json");
            put(".bin", "application/binary");
            put(".dat", "application/binary");
        }
    };
    String[] parameterBounds = new String[]{"${", "}"};
    private String[] localizableBounds = new String[]{"#{", "}"};
    // resources scanning
    private JavaResourcesList jrl; // resource scanner: jar or dir
    private Map<String, List<String>> jrlm = null; // resource scanning result
    private Map<String, Long> jrlmts = new HashMap<>(); // resource scannin timestamp (for dir re-scan)

    /**
     *
     * @param path exposed path
     * @param rootPath actual resources path
     */
    public HttpResourceCollection(String path, String rootPath) {
        this.path = path;
        this.rootPath = rootPath;
    }

    /**
     * Returns list of matching resources as exposed paths
     *
     * @param pathMask
     * @return
     */
    public Collection<String> scan(Matcher<String> pathMask) {
        List<String> r = new ArrayList<>();

        if (jrl == null || jrlm == null) {
            if (jrl == null) {
                jrl = new JavaResourcesList(getClass().getClassLoader());
            }
            jrlm = jrl.listResources();
            long ts = System.currentTimeMillis();
            for (String key : jrlm.keySet()) {
                if (key.startsWith("file:")) {
                    // 
                    try {
                        long tts = new File(new URL(key).toURI()).lastModified();
                        if (tts > ts) {
                            // if resource change after scan has started, set its timestamp to start of scanning
                            tts = ts;
                        }
                        jrlmts.put(key, tts);
                    } catch (Throwable th) {
                        jrlmts.put(key, ts);
                    }
                } else {
                    jrlmts.put(key, ts);
                }
            }
        } else {
            // update non-static info
            for (String key : jrlm.keySet()) {
                if (key.startsWith("file:")) {
                    try {
                        Long ts = jrlmts.get(key);
                        URL url = new URL(key);
                        long tts = new File(url.toURI()).lastModified();
                        if (ts == null || tts > ts) {
                            List<String> lst = new ArrayList<>();
                            jrl.listUrlResources(url, lst);
                            jrlm.put(key, lst);
                            jrlmts.put(key, tts);
                        }
                    } catch (Throwable th) {
                    }
                }
            }
        }

        if (jrlm != null) {
            //System.out.println(" mask: " + pathMask);
            for (Entry<String, List<String>> entry : jrlm.entrySet()) {
                String pfx = entry.getKey();
                if (pfx.startsWith("file:/")) {
                    pfx = pfx.substring(6);
                }
                //System.out.println("  pfx: " + pfx);
                for (String s : entry.getValue()) {
                    //System.out.println("    s: " + s);
                    s = s.replace("\\", "/");
                    String p = (s.startsWith(pfx)) ? s.substring(pfx.length()) : s;
                    //System.out.println(" -> p: " + p);
                    float f = pathMask.match(p);
                    if (f > 0) {
                        //System.out.println("    f: " + f);
                        if (!r.contains(p)) {
                            r.add(p);
                        }
                    }
                }
            }
        }

        if (!r.isEmpty()) {
            Collections.sort(r);
        }

        return r;
    }

    @Override
    public HttpResource find(String path) {
        if (path == null) {
            return null;
        }
        if (collection.containsKey(path)) {
            return collection.get(path);
        } else {
            if (path.contains("?")) {
                path = path.substring(0, path.indexOf("?"));
            }
            if (!path.contains(".")) {
                return null;
            }
            String ext = path.substring(path.lastIndexOf(".")).toLowerCase();
            if (!contentTypes.containsKey(ext)) {
                return null;
            }
            HttpResource res = null;
            try {
                int ppl = this.path.lastIndexOf("/");
                String resPath = rootPath + path.substring(ppl);
                URL resURL = null;
                Long urlTimestamp = null;
                if (resPath.startsWith("resource:")) {
                    // java resource
                    resPath = resPath.substring(resPath.indexOf(":") + 1);
                    resURL = getClass().getClassLoader().getResource(resPath);
                    // if none - try recoded path...
                    if (resURL == null) {
                        String rp = URLDecoder.decode(resPath, "ISO-8859-1");
                        if (DEBUG) {
                            System.out.println("   path: " + resPath + "\n    ISO-8859-1: " + rp);
                        }
                        resURL = getClass().getClassLoader().getResource(rp);
                        if (resURL == null) {
                            try {
                                rp = URLDecoder.decode(resPath, "UTF-8");
                                if (DEBUG) {
                                    System.out.println("    UTF-8     : " + rp);
                                }
                                resURL = getClass().getClassLoader().getResource(rp);
                            } catch (Throwable th) {
                            }
                        }
                    }
                    if (DEBUG) {
                        System.out.println("    URL       : " + resURL);
                    }
                } else if (resPath.contains("//") && resPath.contains(":")) {
                    // URL
                    resURL = new URL(resPath);
                } else {
                    // file?
                    File file = new File(resPath);
                    resURL = file.toURI().toURL();
                    urlTimestamp = file.lastModified();
                }
                if (resURL != null) {
                    res = new HttpResourceURL(resURL, path, contentTypes.get(ext), (urlTimestamp != null) ? urlTimestamp : timestamp());
                    initResourceParameters(res);
                }
            } catch (Throwable th) {
                th.printStackTrace();
            }
            if (res != null) {
                collection.put(path, res);
            }
            return res;
        }
    }

    public void initResourceParameters(HttpResource res) throws IOException {
        if (parameterBounds != null || localizableBounds != null) {
            String ct = res.contentType();
            if (ct.contains("text") || ct.contains("html") || ct.contains("script") || ct.contains("css")) {
                String[][] cc = HttpDataProcessor.initTextParameters(
                        new String[][]{parameterBounds, localizableBounds},
                        "UTF-8",
                        res.open(null)
                );
                res.parameters(cc[0]);
                res.localizeable(cc[1]);
            }
        }
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String contentType() {
        return null;
    }

    @Override
    public long size() {
        return -1;
    }

    @Override
    public InputStream open(HttpData httpData, Replacement... replacements) throws IOException {
        throw new UnsupportedOperationException("Not supported for collection resource. Use find to get actual resource");
    }

    @Override
    public byte[] data(HttpData httpData, Replacement... replacements) throws IOException {
        throw new UnsupportedOperationException("Not supported for collection resource. Use find to get actual resource");
    }

    @Override
    public long timestamp() {
        return timestamp;
    }

    @Override
    public Long expires() {
        return null;
    }

    @Override
    public String[] parameters() {
        return null;
    }

    public void parameters(String[] parameters) {
    }

    @Override
    public String[] localizeable() {
        return null;
    }

    @Override
    public void localizeable(String[] localizeable) {
    }

    /**
     * @return the parameterBounds
     */
    public String[] getParametersBounds() {
        return parameterBounds;
    }

    /**
     * @param parameterBounds the parametersBounds to set
     */
    public void setParameterBounds(String[] parameterBounds) {
        this.parameterBounds = parameterBounds;
    }

    /**
     * @return the localizableBounds
     */
    public String[] getLocalizableBounds() {
        return localizableBounds;
    }

    /**
     * @param localizableBounds the localizableBounds to set
     */
    public void setLocalizableBounds(String[] localizableBounds) {
        this.localizableBounds = localizableBounds;
    }

    @Override
    public boolean requiresInitialization(HttpData httpData) {
        throw new UnsupportedOperationException("Not supported: found resources should be used instead.");
    }

}
