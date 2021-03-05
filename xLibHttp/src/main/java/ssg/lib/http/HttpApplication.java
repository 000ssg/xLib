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
package ssg.lib.http;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.common.TaskProvider;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.http.base.HttpResponse;
import ssg.lib.service.DataProcessor;
import ssg.lib.service.Repository;

/**
 * Http application represents named "root" to distinguish session scopes.
 *
 * @author 000ssg
 */
public class HttpApplication implements Serializable, Cloneable, TaskProvider {

    private String name;
    private String root;
    private Map<String, Object> properties = new LinkedHashMap<>();
    private Repository<DataProcessor> dataProcessors;
    private boolean basicAuthEnabled = false;
    transient HttpAuthenticator auth;
    HttpMatcher matcher;

    public HttpApplication() {
        name = "Default";
        root = "/";
        matcher = new HttpMatcher(root);
    }

    public HttpApplication(
            String name,
            String root
    ) {
        this.name = name;
        this.root = root;
        getProperties().put("name", name);
        getProperties().put("root", root);
        matcher = new HttpMatcher(root);
    }

    public HttpMatcher getMatcher() {
        return matcher;
    }

    public <T extends HttpApplication> T configureName(String name) {
        this.name = name;
        getProperties().put("name", name);
        return (T) this;
    }

    public <T extends HttpApplication> T configureBasicAuthentication(boolean enable) {
        setBasicAuthEnabled(enable);
        return (T) this;
    }

    public <T extends HttpApplication> T configureRoot(String root) throws IOException {
        if (this.root == null || this.root.equals(root)) {
            this.root = root;
        } else {
            throw new IOException("Cannot change root from '" + this.root + "' to '" + root + "'.");
        }
        getProperties().put("root", root);
        matcher = new HttpMatcher(root);
        return (T) this;
    }

    public <T extends HttpApplication> T configureDataProcessors(Repository<DataProcessor> dataProcessors) {
        this.dataProcessors = dataProcessors;
        return (T) this;
    }

    public <T extends HttpApplication> T configureDataProcessor(int order, DataProcessor... dataProcessors) {
        if (this.dataProcessors == null) {
            this.dataProcessors = new Repository<DataProcessor>();
        }
        if (dataProcessors != null) {
            this.dataProcessors.addItem(order, dataProcessors);
        }
        return (T) this;
    }

    public <T extends HttpApplication> T configureProperty(String name, Object value) throws IOException {
        if ("name".equals(name)) {
            configureName((String) value);
        } else if ("root".equals(name)) {
            configureRoot((String) value);
        } else {
            getProperties().put(name, value);
        }
        return (T) this;
    }

    public <T extends HttpApplication> T configureAuthentication(HttpAuthenticator auth) throws IOException {
        if (this.auth == null || this.auth.equals(auth)) {
            this.auth = auth;
        } else {
            throw new IOException("Cannot override HttpAuthenticator.");
        }
        return (T) this;
    }

    ////////////////////////////////////////////////////////////////////////////
    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the root
     */
    public String getRoot() {
        return root;
    }

    public String getDefaultPage(HttpData data) {
        return getRoot() + ((getRoot().isEmpty() || getRoot().endsWith("/") ? "" : "/")) + "index.html";
    }

    @Override
    public String toString() {
        return "HttpApplication{" + "name=" + name + ", root=" + root + '}';
    }

    public <T> T getProperty(String name) {
        return (T) getProperties().get(name);
    }

    /**
     * @return the properties
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * @return the dataProcessors
     */
    public Repository<DataProcessor> getDataProcessors() {
        return dataProcessors;
    }

    /**
     * @param dataPorcessors the dataProcessors to set
     */
    public void setDataPorcessors(Repository<DataProcessor> dataPorcessors) {
        this.dataProcessors = dataPorcessors;
    }

    @Override
    public List<Task> getTasks(TaskPhase... phases) {
        if (phases == null || phases.length == 0) {
            return Collections.emptyList();
        }
        List<Task> r = new ArrayList<>();

        for (DataProcessor dp : dataProcessors.items()) {
            List<Task> dpr = dp.getTasks(phases);
            if (dpr != null) {
                r.addAll(dpr);
            }
        }

        Collections.sort(r, TaskProvider.getTaskComparator(true));
        return r;
    }

    /**
     * Perform actions needed to authenticate user and return true. If no such
     * action - return false.
     *
     * @param req
     * @return
     * @throws IOException
     */
    public boolean doAuthentication(HttpRequest req) throws IOException {
        if (req.isHeaderLoaded() && !req.getResponse().getHead().isSent() && isBasicAuthEnabled()) {
            HttpSession sess = req.getHttpSession();
            HttpResponse resp = req.getResponse();
            resp.setResponseCode(401, "Access denied");
            String dn = sess.getBaseURL();
            if (dn.contains("//")) {
                dn = dn.substring(dn.indexOf("//") + 2);
            }
            resp.setHeader("WWW-Authenticate", "Basic realm=\"" + dn + "\", charset=\"UTF-8\"");
            resp.onHeaderLoaded();
            resp.onLoaded();
            return true;
        }
        return false;
    }

    /**
     * @return the basicAuthEnabled
     */
    public boolean isBasicAuthEnabled() {
        return basicAuthEnabled;
    }

    /**
     * @param basicAuthEnabled the basicAuthEnabled to set
     */
    public void setBasicAuthEnabled(boolean basicAuthEnabled) {
        this.basicAuthEnabled = basicAuthEnabled;
    }

    public HttpUser onAuhtenticatedUser(HttpSession session, HttpUser user) {
        return user;
    }

    /**
     * Evaluates supported locales and returns preferred one.
     *
     * @param data
     * @return
     */
    public String chooseLocale(HttpData data) {
        if (data.getHead().getHeader1(HttpData.HH_ACCEPT_LANGUAGE) != null) {
            String[] ll = data.getHead().getHeader1(HttpData.HH_ACCEPT_LANGUAGE).split(";");
            String l = ll[0];
            return l.contains(",") ? l.split(",")[0] : l;
        }
        return "en";
    }
}
