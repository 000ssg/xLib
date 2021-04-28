/*
 * The MIT License
 *
 * Copyright 2021 000ssg.
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
package ssg.lib.http_cs;

import ssg.lib.common.TaskExecutor;
import ssg.lib.di.DI;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.HttpAuthenticator;
import ssg.lib.http.HttpAuthenticator.Domain;
import ssg.lib.http.HttpAuthenticator.HttpSimpleAuth;
import ssg.lib.http.HttpService;
import ssg.lib.service.DF_Service;

/**
 * Http helps to construct DI handler for CS.
 *
 * Http represents hierarchy auth-httpService-service-di. Depending on
 * constructor it may require to build intermediaries. The only no-build variant
 * is httpService+di.
 *
 * @author 000ssg
 */
public class Http {
    
    DI di;
    DF_Service service;
    HttpService httpService;
    HttpAuthenticator auth;

    /**
     * Minimal variant. Results in no authenticator. On init service,
     * httpService and di are built.
     */
    public Http() {
    }

    /**
     * Minimal variant with authenticator. On init service, httpService and di
     * are built.
     */
    public Http(HttpAuthenticator auth) {
        this.auth = auth;
    }

    /**
     * Http service is defined (no access to auth). On init service and di are
     * built.
     */
    public Http(HttpService httpService) {
        this.httpService = httpService;
    }

    /**
     * No access to auth or service. No built items.
     *
     * @param httpService
     * @param di
     */
    public Http(HttpService httpService, DI di) {
        this.httpService = httpService;
        this.di = di;
    }

    /**
     * Returns handler for use with CS's TCPHandler.
     *
     * @return
     */
    public DI getDI() {
        if (di == null) {
            init();
        }
        return di;
    }

    /**
     * Returns DF_Service instance that manages HttpService. May be use to
     * modiufy its behaviour (e.g. add SSL support).
     *
     * @return
     */
    public DF_Service getService() {
        if (di == null) {
            init();
        }
        return service;
    }

    /**
     * Returns provided or build HttpService. May be used to configure
     * applications and/or other Http handling items.
     *
     * @return
     */
    public HttpService getHttpService() {
        if (di == null) {
            init();
        }
        return httpService;
    }

    /**
     * Provides access to Http authenticator, e.g. to manage supported features,
     * users, etc.
     *
     * @param <T>
     * @return
     */
    public <T extends HttpAuthenticator> T getAuth() {
        if (di == null) {
            init();
        }
        return (T) auth;
    }

    /**
     * Ensure DI is avaliable. Builds missing component if missing. Safe to
     * invoke multiple times being incremental.
     *
     * @param <T>
     * @return
     */
    public <T extends Http> T init() {
        if (di == null) synchronized (this) {
            if (httpService == null) {
                httpService = buildHttpService(auth);
            }
            if (service == null) {
                service = buildService();
            }
            di = service
                    .configureService(-1, httpService)
                    .buildDI();
        }
        return (T) this;
    }

    /**
     * HttpService construction tuning enabler
     *
     * @param auth
     * @return
     */
    public HttpService buildHttpService(HttpAuthenticator auth) {
        return new HttpService(auth instanceof Domain ? new HttpSimpleAuth().configureDomain((Domain) auth) : auth);
    }

    /**
     * DF_Service construction tuner. By defaults creates instance of DF_Service
     * and assigns new TaskExecutorPool.
     *
     * @return
     */
    public DF_Service buildService() {
        return new DF_Service()
                .configureExecutor(new TaskExecutor.TaskExecutorPool());
    }

    ////////////////////////////////////////////////////////////////////////////
    public <T extends Http> T configureApp(HttpApplication app) {
        HttpService http = getHttpService();
        http.configureApplication(0, app);
        return (T) this;
    }
}
