/*
 * The MIT License
 *
 * Copyright 2020 000ssg.
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
package ssg.lib.oauth2.client;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author 000ssg
 */
public class OAuthUserInfoBase implements OAuthUserInfo {
    
    String id;
    String name;
    String email;
    String image;
    Map<String, Object> properties = new LinkedHashMap<String, Object>();

    public OAuthUserInfoBase(String id, String name, String email, String image) {
        this.id = id;
        this.name = name;
        this.email = (email != null) ? email.toLowerCase() : email;
        this.image = image;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String email() {
        return email;
    }

    @Override
    public String image() {
        return image;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append('{');
        sb.append("\n  id=" + id);
        sb.append("\n  name=" + name);
        sb.append("\n  email=" + email);
        sb.append("\n  image=" + image);
        sb.append("\n  properties[" + properties.size() + "]=");
        for (String key : properties.keySet()) {
            Object val = properties.get(key);
            sb.append("\n    " + key + ": ");
            sb.append(("" + val).replace("\n", "\n      "));
        }
        sb.append('}');
        return sb.toString();
    }
    
}
