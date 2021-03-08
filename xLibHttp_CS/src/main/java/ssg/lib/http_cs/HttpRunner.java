/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ssg.lib.http_cs;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.HttpDataProcessor;
import ssg.lib.http.HttpService;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.dp.HttpDataProcessorFavIcon;
import ssg.lib.http.rest.RESTHttpDataProcessor;
import ssg.lib.net.CS;
import ssg.lib.net.TCPHandler;
import ssg.lib.service.Repository;

/**
 * Common base for deployable runnable. Provides non-reentrable start/stop to
 * allow CS embedding.
 *
 * @author sesidoro
 */
public class HttpRunner extends CS {

    public static final String CFG_HTTP_PORT = "httpPort";
    public static final String CFG_REST_PATH = "restPath";

    // builders/holders
    Http http;
    Integer httpPort;

    // http
    HttpApplication app;
    RESTHttpDataProcessor rest;
    URI restURI;
//    HttpConnectionUpgrade ws_wamp_connection_upgrade;

    // embedding CS support: nested (indirect) calls to start/stop are safe
    private transient Boolean starting = false;
    private transient Boolean stopping = false;

    @Override
    public void stop() throws IOException {
        synchronized (stopping) {
            if (!stopping) {
                stopping = true;
                onStopping();
                super.stop();
            }
        }
    }

    @Override
    public void start() throws IOException {
        synchronized (starting) {
            if (!starting) {
                starting = true;
                super.start();
                onStarted();
            }
        }
    }

    public HttpRunner() {
    }

    public HttpRunner(HttpApplication app) {
        this.app = app;
        http = new Http();
        http.configureApp(app);
    }

    public void configUpdated(String key, Object oldValue, Object newValue) {
    }

    public void initHttp() {
        if (http == null) {
            http = new Http();
            Repository<HttpDataProcessor> rep = http.getHttpService().getDataProcessors(null, null);
            if (rep == null) {
                rep = new Repository<>();
                http.getHttpService().setDataProcessors(rep);
            }
            rep.addItem(new HttpDataProcessorFavIcon() {

                @Override
                public boolean isPreventCacheing(HttpData data) {
                    return false;
                }
            });
        }
    }

    public Integer getHttpPort() {
        return httpPort;
    }

    public HttpRunner configureHttp(Integer httpPort) {
        Integer old = this.httpPort;
        this.httpPort = httpPort;
        configUpdated(CFG_HTTP_PORT, old, httpPort);
        return this;
    }

    public HttpRunner configureREST(String path) throws IOException {
        if (rest == null && path != null) {
            if (getApp() != null) {
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
            } else {
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
            }

            rest = new RESTHttpDataProcessor(path);
            if (getApp() == null) {
                initHttp();
                if (http.getHttpService().getDataProcessors(null, null) == null) {
                    http.getHttpService().setDataProcessors(new Repository<>());
                }
                http.getHttpService().getDataProcessors(null, null).addItem(rest);
            } else {
                if (getApp().getDataProcessors() == null) {
                    getApp().setDataPorcessors(new Repository<>());
                }
                getApp().getDataProcessors().addItem(rest);
            }
            configUpdated(CFG_REST_PATH, null, path);
        } else {
            throw new IOException("REST configuration is defined already.");
        }
        return this;
    }

    public void onStarted() throws IOException {
        if (httpPort != null) {
            if (http == null) {
                initHttp();
            }
            add(new TCPHandler(new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), httpPort)).defaultHandler(http.getDI()));
            if (getREST() != null) {
                String path = app != null ? app.getRoot() : "";
                if (!path.endsWith("/") || !getREST().getMatcher().getPath().startsWith("/")) {
                    path += "/";
                }
                path += getREST().getMatcher().getPath();
                try {
                    restURI = new URI("http://localhost:" + httpPort + path);
                } catch (Throwable th) {
                }
            }
        }
    }

    public void onStopping() throws IOException {

    }

    public HttpApplication getApp() {
        return app;
    }

    public RESTHttpDataProcessor getREST() {
        return rest;
    }

    public URI getREST_URI() {
        return restURI;
    }

    public HttpService getService() {
        return http != null ? http.getHttpService() : null;
    }
}
