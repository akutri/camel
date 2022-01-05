/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.coap;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The CoAP producer.
 */
public class CoAPProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(CoAPProducer.class);

    private final CoAPEndpoint endpoint;
    private CoapClient client;

    public CoAPProducer(CoAPEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (endpoint.isNotify()) {
            notifyResource(exchange);
            return;
        }

        CoapClient client = getClient(exchange);
        String ct = exchange.getIn().getHeader(CoAPConstants.CONTENT_TYPE, String.class);
        if (ct == null) {
            // ?default?
            ct = "application/octet-stream";
        }
        String method = CoAPHelper.getDefaultMethod(exchange, client);
        int mediaType = MediaTypeRegistry.parse(ct);
        CoapResponse response = null;
        boolean pingResponse = false;
        switch (method) {
            case CoAPConstants.METHOD_GET:
                response = client.get();
                break;
            case CoAPConstants.METHOD_DELETE:
                response = client.delete();
                break;
            case CoAPConstants.METHOD_POST:
                byte[] bodyPost = exchange.getIn().getBody(byte[].class);
                response = client.post(bodyPost, mediaType);
                break;
            case CoAPConstants.METHOD_PUT:
                byte[] bodyPut = exchange.getIn().getBody(byte[].class);
                response = client.put(bodyPut, mediaType);
                break;
            case CoAPConstants.METHOD_PING:
                pingResponse = client.ping();
                break;
            default:
                break;
        }

        if (response != null) {
            CoAPHelper.convertCoapResponseToMessage(response, exchange.getOut());
        }

        if (method.equalsIgnoreCase(CoAPConstants.METHOD_PING)) {
            Message resp = exchange.getOut();
            resp.setBody(pingResponse);
        }
    }

    private synchronized CoapClient getClient(Exchange exchange) throws IOException, GeneralSecurityException {
        if (client == null) {
            URI uri = exchange.getIn().getHeader(CoAPConstants.COAP_URI, URI.class);
            if (uri == null) {
                uri = endpoint.getUri();
            }
            client = endpoint.createCoapClient(uri);
        }
        return client;
    }

    private void notifyResource(Exchange exchange) throws IOException, GeneralSecurityException {
        URI uri = exchange.getIn().getHeader(CoAPConstants.COAP_URI, URI.class);
        if (uri == null) {
            uri = endpoint.getUri();
        }
        CamelCoapResource resource = endpoint.getCamelCoapResource(uri.getPath());
        if (resource == null) {
            throw new IllegalStateException("Resource not found: " + endpoint.getUri());
        }
        if (!resource.isObservable()) {
            LOG.warn("Ignoring notification attempt for resource that is not observable: " + endpoint.getUri());
            return;
        }
        resource.changed();
    }
}
