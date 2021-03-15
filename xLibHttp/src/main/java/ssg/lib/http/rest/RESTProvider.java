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
package ssg.lib.http.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author 000ssg
 */
public class RESTProvider {

    private String name;
    private String[] paths;
    private String description;
    private RESTAccess access;
    Map<String, Object> properties;

    private Collection<RESTMethod> methods = new ArrayList<RESTMethod>();

    /**
     * @return the methods
     */
    public Collection<RESTMethod> getMethods() {
        return methods;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the path
     */
    public String[] getPaths() {
        return paths;
    }

    /**
     * @param path the path to set
     */
    public void setPaths(String... paths) {
        this.paths = paths;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 19 * hash + (this.name != null ? this.name.hashCode() : 0);
        hash = 19 * hash + Arrays.deepHashCode(this.paths);
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
        final RESTProvider other = (RESTProvider) obj;
        if ((this.name == null) ? (other.name != null) : !this.name.equals(other.name)) {
            return false;
        }
        if (!Arrays.deepEquals(this.paths, other.paths)) {
            return false;
        }
        return true;
    }

    /**
     * @return the access
     */
    public RESTAccess getAccess() {
        return access;
    }

    /**
     * @param access the access to set
     */
    public void setAccess(RESTAccess access) {
        this.access = access;
    }

    public void setProperty(String name, Object v) {
        if (name == null || properties == null && v == null) {
            return;
        }
        if (properties == null) {
            properties = new LinkedHashMap<>();
        }
        if (v != null) {
            properties.put(name, v);
        } else if (properties.containsKey(name)) {
            properties.remove(name);
        }
    }

    public <Z> Z getProperty(String name) {
        return properties != null ? (Z) properties.get(name) : null;
    }
}
