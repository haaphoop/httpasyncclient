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
package org.apache.http.nio.client.methods;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.UnsupportedCharsetException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

public abstract class AsyncCharConsumer<T> extends AbstractHttpAsyncResponseConsumer<T> {

    private final int bufSize;
    private String charsetName;
    private Charset charset;
    private CharsetDecoder decoder;
    private ByteBuffer bbuf;
    private CharBuffer cbuf;

    public AsyncCharConsumer(int bufSize) {
        super();
        this.bufSize = bufSize;
    }

    public AsyncCharConsumer() {
        this(8 * 1024);
    }

    protected abstract void onCharReceived(
            final CharBuffer buf, final IOControl ioctrl) throws IOException;

    @Override
    public synchronized void responseReceived(
            final HttpResponse response) throws IOException, HttpException {
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            this.charsetName = EntityUtils.getContentCharSet(entity);
        }
        if (this.charsetName == null) {
            this.charsetName = HTTP.DEFAULT_CONTENT_CHARSET;
        }
        super.responseReceived(response);
    }

    @Override
    protected void onContentReceived(
            final ContentDecoder decoder, final IOControl ioctrl) throws IOException {
        if (this.charset == null) {
            try {
                this.charset = Charset.forName(this.charsetName);
            } catch (UnsupportedCharsetException ex) {
                throw new UnsupportedEncodingException(this.charsetName);
            }
            this.decoder = this.charset.newDecoder();
            this.bbuf = ByteBuffer.allocate(this.bufSize);
            this.cbuf = CharBuffer.allocate(this.bufSize);
        }
        for (;;) {
            int bytesRead = decoder.read(this.bbuf);
            if (bytesRead <= 0) {
                break;
            }
            this.bbuf.flip();
            CoderResult result = this.decoder.decode(this.bbuf, this.cbuf, decoder.isCompleted());
            if (result.isError()) {
                result.throwException();
            }
            this.cbuf.flip();
            onCharReceived(this.cbuf, ioctrl);
            this.cbuf.clear();
            this.bbuf.clear();
        }
    }

    @Override
    void releaseResources() {
        this.charset = null;
        this.decoder = null;
        this.bbuf = null;
        this.cbuf = null;
        super.releaseResources();
    }

}
