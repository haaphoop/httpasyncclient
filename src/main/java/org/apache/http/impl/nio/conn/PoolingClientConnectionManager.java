/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.http.impl.nio.conn;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.nio.pool.PoolEntryCallback;
import org.apache.http.nio.concurrent.BasicFuture;
import org.apache.http.nio.concurrent.FutureCallback;
import org.apache.http.nio.conn.ManagedClientConnection;
import org.apache.http.nio.conn.ClientConnectionManager;
import org.apache.http.nio.conn.PoolStats;
import org.apache.http.nio.conn.scheme.SchemeRegistry;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOSession;

public class PoolingClientConnectionManager implements ClientConnectionManager {

    private final Log log = LogFactory.getLog(getClass());

    private final HttpSessionPool pool;
    private final SchemeRegistry schemeRegistry;

    public PoolingClientConnectionManager(
            final ConnectingIOReactor ioreactor,
            final SchemeRegistry schemeRegistry) {
        super();
        if (ioreactor == null) {
            throw new IllegalArgumentException("I/O reactor may not be null");
        }
        if (schemeRegistry == null) {
            throw new IllegalArgumentException("Scheme registory may not be null");
        }
        this.pool = new HttpSessionPool(ioreactor, schemeRegistry);
        this.schemeRegistry = schemeRegistry;
    }

    public PoolingClientConnectionManager(
            final ConnectingIOReactor ioreactor) {
        this(ioreactor, SchemeRegistryFactory.createDefault());
    }

    public SchemeRegistry getSchemeRegistry() {
        return this.schemeRegistry;
    }

    public synchronized Future<ManagedClientConnection> leaseConnection(
            final HttpRoute route,
            final Object state,
            final long timeout,
            final TimeUnit timeUnit,
            final FutureCallback<ManagedClientConnection> callback) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("I/O session request: route[" + route + "][state: " + state + "]");
            PoolStats totals = this.pool.getTotalStats();
            PoolStats stats = this.pool.getStats(route);
            this.log.debug("Total: " + totals);
            this.log.debug("Route [" + route + "]: " + stats);
        }
        BasicFuture<ManagedClientConnection> future = new BasicFuture<ManagedClientConnection>(
                callback);
        this.pool.lease(route, state, timeout, timeUnit, new InternalPoolEntryCallback(future));
        if (this.log.isDebugEnabled()) {
            if (!future.isDone()) {
                this.log.debug("I/O session could not be allocated immediately: " +
                        "route[" + route + "][state: " + state + "]");
            }
        }
        return future;
    }

    public synchronized void releaseConnection(final ManagedClientConnection conn) {
        if (!(conn instanceof ClientConnAdaptor)) {
            throw new IllegalArgumentException
                ("I/O session class mismatch, " +
                 "I/O session not obtained from this manager");
        }
        ClientConnAdaptor adaptor = (ClientConnAdaptor) conn;
        ClientConnectionManager manager = adaptor.getManager();
        if (manager != null && manager != this) {
            throw new IllegalArgumentException
                ("I/O session not obtained from this manager");
        }
        HttpPoolEntry entry = adaptor.getEntry();
        IOSession iosession = entry.getIOSession();
        if (this.log.isDebugEnabled()) {
            HttpRoute route = entry.getPlannedRoute();
            PoolStats totals = this.pool.getTotalStats();
            PoolStats stats = this.pool.getStats(route);
            this.log.debug("Total: " + totals);
            this.log.debug("Route [" + route + "]: " + stats);
            this.log.debug("I/O session released: " + entry);
        }
        this.pool.release(entry, adaptor.isReusable() && !iosession.isClosed());
    }

    public PoolStats getTotalStats() {
        return this.pool.getTotalStats();
    }

    public PoolStats getStats(final HttpRoute route) {
        return this.pool.getStats(route);
    }

    public void setTotalMax(int max) {
        this.pool.setTotalMax(max);
    }

    public void setDefaultMaxPerHost(int max) {
        this.pool.setDefaultMaxPerHost(max);
    }

    public void setMaxPerHost(final HttpRoute route, int max) {
        this.pool.setMaxPerHost(route, max);
    }

    public synchronized void shutdown() {
        this.log.debug("I/O session manager shut down");
        this.pool.shutdown();
    }

    class InternalPoolEntryCallback implements PoolEntryCallback<HttpRoute, HttpPoolEntry> {

        private final BasicFuture<ManagedClientConnection> future;

        public InternalPoolEntryCallback(
                final BasicFuture<ManagedClientConnection> future) {
            super();
            this.future = future;
        }

        public void completed(final HttpPoolEntry entry) {
            if (log.isDebugEnabled()) {
                log.debug("I/O session allocated: " + entry);
            }
            ManagedClientConnection conn = new ClientConnAdaptor(
                    PoolingClientConnectionManager.this,
                    entry);
            if (!this.future.completed(conn)) {
                pool.release(entry, true);
            }
        }

        public void failed(final Exception ex) {
            if (log.isDebugEnabled()) {
                log.debug("I/O session request failed", ex);
            }
            this.future.failed(ex);
        }

        public void cancelled() {
            log.debug("I/O session request cancelled");
            this.future.cancel(true);
        }

    }

}
