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

import org.apache.http.annotation.ThreadSafe;
import org.apache.http.nio.conn.scheme.Scheme;
import org.apache.http.nio.conn.scheme.SchemeRegistry;
import org.apache.http.nio.conn.ssl.SSLLayeringStrategy;

/**
 * @since 4.1
 */
@ThreadSafe
public final class SchemeRegistryFactory {

    public static SchemeRegistry createDefault() {
        SchemeRegistry registry = new SchemeRegistry();
        registry.register(
                new Scheme("http", 80, null));
        registry.register(
                new Scheme("https", 443, SSLLayeringStrategy.getLayeringStrategy()));
        return registry;
    }

}

