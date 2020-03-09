/*
 * The MIT License
 *
 * Copyright 2020 sesidoro.
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
package ssg.lib.api.wamp_cs;

import java.util.ArrayList;
import java.util.List;
import ssg.lib.net.CS;
import ssg.lib.net.CSGroup;
import ssg.lib.service.DF_Service;

/**
 *
 * @author sesidoro
 */
public class ServiceGroup implements CSGroup {

    String name;
    DF_Service service;
    List<CSGroup> groups = new ArrayList<>();

    public ServiceGroup() {
    }

    public ServiceGroup(String name) {
        this.name = name;
    }

    public ServiceGroup addCSGroup(CSGroup... groups) {
        if (groups != null) {
            for (CSGroup csg : groups) {
                if (csg != null && !this.groups.contains(csg)) {
                    this.groups.add(csg);
                }
            }
        }
        return this;
    }
    
    public ServiceGroup setService(DF_Service service) {
        if (groups != null) {
            for (CSGroup csg : groups) {
                if (csg != null && !this.groups.contains(csg)) {
                    this.groups.add(csg);
                }
            }
        }
        return this;
    }

    @Override
    public void onStarted(CS cs) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void onStop(CS cs) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
