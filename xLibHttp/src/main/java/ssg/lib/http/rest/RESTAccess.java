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

import ssg.lib.http.RAT;
import java.io.Serializable;

/**
 *
 * @author 000ssg
 */
public class RESTAccess implements Serializable, Cloneable {

    private RAT instance = new RAT();
    private RAT method = new RAT();

    public boolean test(RAT caller) {
        boolean instanceOK = false;
        boolean methodOK = false;

        if (getInstance() != null) {
            instanceOK = getInstance().test(caller);
        } else {
            instanceOK = true;
        }
        if (getMethod() != null) {
            methodOK = getMethod().test(caller);
        } else {
            methodOK = true;
        }

        return instanceOK && methodOK;
    }

    @Override
    public RESTAccess clone() {
        try {
            RESTAccess copy = (RESTAccess) super.clone();

            if (copy.getInstance() != null) {
                copy.setInstance(copy.getInstance().clone());
            }

            if (copy.getMethod() != null) {
                copy.setMethod(copy.getMethod().clone());
            }

            return copy;
        } catch (CloneNotSupportedException cnsex) {
            cnsex.printStackTrace();
            return null;
        }
    }

    /**
     * @return the instance
     */
    public RAT getInstance() {
        return instance;
    }

    /**
     * @param instance the instance to set
     */
    public void setInstance(RAT instance) {
        this.instance = instance;
    }

    /**
     * @return the method
     */
    public RAT getMethod() {
        return method;
    }

    /**
     * @param method the method to set
     */
    public void setMethod(RAT method) {
        this.method = method;
    }

    @Override
    public String toString() {
        return "RESTAccess{"
                + "instance=" + ("" + instance).replace("\n", "\n  ")
                + "\n   method=" + ("" + method).replace("\n", "\n  ")
                + '\n'
                + '}';
    }

}
