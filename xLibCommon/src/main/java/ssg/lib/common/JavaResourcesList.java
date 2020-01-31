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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 *
 * @author sesidoro
 */
public class JavaResourcesList {

    private ClassLoader classLoader;

    public JavaResourcesList(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public Map<String, List<String>> listResources() {
        Map<String, List<String>> r = new LinkedHashMap<>();

        try {

            List<URL> urls = (classLoader instanceof URLClassLoader)
                    ? Arrays.asList(((URLClassLoader) classLoader).getURLs())
                    : (classLoader != null)
                            ? Collections.list(classLoader.getResources(""))
                            : null;

            if (urls == null || urls.isEmpty()) {
                urls = new ArrayList();
                String[] paths = System.getProperty("java.class.path").split(System.getProperty("path.separator"));
                for (String p : paths) {
                    try {
                        File f = new File(p);
                        URL u = f.toURI().toURL();
                        urls.add(u);
                    } catch (Throwable th) {
                    }
                }
            }

            for (URL url : urls) {
                List<String> list = new ArrayList<>();
                r.put(url.toString(), list);
                listUrlResources(url, list);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return r;
    }

    public List<String> listResources(List<String> list) {
        if (list == null) {
            list = new ArrayList<>();
        }
        try {

            List<URL> urls = (classLoader instanceof URLClassLoader)
                    ? Arrays.asList(((URLClassLoader) classLoader).getURLs())
                    : (classLoader != null)
                            ? Collections.list(classLoader.getResources(""))
                            : null;

            if (urls == null || urls.isEmpty()) {
                urls = new ArrayList();
                String[] paths = System.getProperty("java.class.path").split(System.getProperty("path.separator"));
                for (String p : paths) {
                    try {
                        File f = new File(p);
                        URL u = f.toURI().toURL();
                        urls.add(u);
                    } catch (Throwable th) {
                    }
                }
            }

            for (URL url : urls) {
                listUrlResources(url, list);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public void listUrlResources(URL url, List<String> list) throws URISyntaxException, IOException {
        File file = new File(url.toURI());

        if (file.isDirectory()) {
            listDirContent(file, list);
        } else {
            listJarContent(new JarFile(file), list);
        }
    }

    private void listJarContent(JarFile jarFile, List<String> list) {
        Enumeration<JarEntry> enumeration = jarFile.entries();
        while (enumeration.hasMoreElements()) {
            JarEntry je = enumeration.nextElement();
            if (je.isDirectory()) {
                continue;
            }
            list.add(je.getName());
        }
    }

    private void listDirContent(File dir, List<String> list) throws IOException {
        String[] children = dir.list();
        for (String aChildren : children) {
            visitAllDirsAndFiles(new File(dir, aChildren), list);
        }
    }

    private void visitAllDirsAndFiles(File file, List<String> list) throws IOException {
        if (file.isDirectory()) {
            listDirContent(file, list);
        } else {
            list.add(file.getCanonicalPath());
        }
    }

    public static void main(String... args) throws Exception {
        JavaResourcesList rl = new JavaResourcesList(null);
        Map<String, List<String>> map = rl.listResources();
        for (String key : map.keySet()) {
            List<String> lst = map.get(key);
            System.out.println("Source[" + lst.size() + "]: " + key);
        }
        List<String> list = rl.listResources(null);
        for (String s : list) {
            System.out.println(s);
        }
    }
}
