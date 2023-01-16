/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2017-2021 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.requesttracing.jaxrs.client;

import fish.payara.notification.requesttracing.RequestTraceSpan;
import fish.payara.nucleus.requesttracing.RequestTracingService;
import fish.payara.nucleus.requesttracing.domain.PropagationHeaders;
import fish.payara.opentracing.ScopeManager;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response.Status.Family;
import jakarta.ws.rs.core.Response.StatusType;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A filter that adds Payara request tracing propagation headers to JAX-RS Client requests.
 *
 * @author Andrew Pielage
 */
public class JaxrsClientRequestTracingFilter implements ClientRequestFilter, ClientResponseFilter {
    public static final String REQUEST_CONTEXT_TRACING_PREDICATE = "fish.payara.requesttracing.jaxrs.client.TracingPredicate";


    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        final PayaraTracingServices payaraTracingServices = new PayaraTracingServices();
        final RequestTracingService requestTracing = payaraTracingServices.getRequestTracingService();
        if (requestTracing != null && requestTracing.isRequestTracingEnabled()) {
            // ***** Request Tracing Service Instrumentation *****
            // If there is a trace in progress, add the propagation headers with the relevant details
            if (requestTracing.isTraceInProgress()) {
                // Check that we aren't overwriting a header
                if (!requestContext.getHeaders().containsKey(PropagationHeaders.PROPAGATED_TRACE_ID)) {
                    requestContext.getHeaders().add(PropagationHeaders.PROPAGATED_TRACE_ID,
                            requestTracing.getConversationID());
                }

                // Check that we aren't overwriting a header
                if (!requestContext.getHeaders().containsKey(PropagationHeaders.PROPAGATED_PARENT_ID)) {
                    requestContext.getHeaders().add(PropagationHeaders.PROPAGATED_PARENT_ID,
                            requestTracing.getStartingTraceID());
                }

                // Check that we aren't overwriting a relationship type
                if (!requestContext.getHeaders().containsKey(PropagationHeaders.PROPAGATED_RELATIONSHIP_TYPE)) {
                    if (requestContext.getMethod().equals("POST")) {
                        requestContext.getHeaders().add(PropagationHeaders.PROPAGATED_RELATIONSHIP_TYPE,
                                RequestTraceSpan.SpanContextRelationshipType.FollowsFrom);
                    } else {
                        requestContext.getHeaders().add(PropagationHeaders.PROPAGATED_RELATIONSHIP_TYPE,
                                RequestTraceSpan.SpanContextRelationshipType.ChildOf);
                    }
                }
            }

            // ***** OpenTracing Instrumentation *****
            // Check if we should trace this client call
            if (shouldTrace(requestContext)) {
                // Get or create the tracer instance for this application
                final Tracer tracer = payaraTracingServices.getActiveTracer();

                // Build a span with the required MicroProfile Opentracing tags
                SpanBuilder spanBuilder = tracer.buildSpan(requestContext.getMethod())
                        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                        .withTag(Tags.HTTP_METHOD.getKey(), requestContext.getMethod())
                        .withTag(Tags.HTTP_URL.getKey(), requestContext.getUri().toURL().toString())
                        .withTag(Tags.COMPONENT.getKey(), "jaxrs");

                // Get the propagated span context from the request if present
                // This is required to account for asynchronous client requests
                SpanContext parentSpanContext = (SpanContext) requestContext.getProperty(
                        PropagationHeaders.OPENTRACING_PROPAGATED_SPANCONTEXT);
                if (parentSpanContext != null) {
                    spanBuilder.asChildOf(parentSpanContext);
                } else {
                    parentSpanContext = SpanPropagator.propagatedContext();
                    if (parentSpanContext != null) {
                        spanBuilder.asChildOf(parentSpanContext);
                    }
                }

                // If there is a propagated span context, set it as a parent of the new span
                parentSpanContext = tracer.extract(Format.Builtin.HTTP_HEADERS,
                        new MultivaluedMapToTextMap(requestContext.getHeaders()));
                if (parentSpanContext != null) {
                    spanBuilder.asChildOf(parentSpanContext);
                }

                // Start the span and mark it as active
                Span span = spanBuilder.start();
                Scope scope = tracer.activateSpan(span);
                requestContext.setProperty(Scope.class.getName(), scope);

                // Inject the active span context for propagation
                tracer.inject(
                        span.context(),
                        Format.Builtin.HTTP_HEADERS,
                        new MultivaluedMapToTextMap(requestContext.getHeaders()));
            }
        }
    }

    // After method invocation
    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        final PayaraTracingServices payaraTracingServices = new PayaraTracingServices();
        final RequestTracingService requestTracing = payaraTracingServices.getRequestTracingService();
        // If request tracing is enabled, and there's a trace actually in progress, add info about method
        if (requestTracing != null && requestTracing.isRequestTracingEnabled() && shouldTrace(requestContext)) {
            // Get the active span from the application's tracer instance
            Span activeSpan = payaraTracingServices.getActiveTracer().scopeManager().activeSpan();
            if (activeSpan == null) {
                // This should really only occur when enabling request tracing due to the nature of the
                // service not being enabled when making the request to enable it, and then
                // obviously being enabled on the return. Any other entrance into here is likely a bug
                // caused by a tracing context not being propagated.
                Logger.getLogger(JaxrsClientRequestTracingFilter.class.getName()).log(Level.FINE,
                        "activeScope in opentracing request tracing filter was null for {0}", responseContext);
                return;
            }

            try {
                // Get the response status and add it to the active span
                StatusType statusInfo = responseContext.getStatusInfo();
                activeSpan.setTag(Tags.HTTP_STATUS.getKey(), statusInfo.getStatusCode());

                // If the response status is an error, add error info to the active span
                if (statusInfo.getFamily() == Family.CLIENT_ERROR || statusInfo.getFamily() == Family.SERVER_ERROR) {
                    activeSpan.setTag(Tags.ERROR.getKey(), true);
                    activeSpan.log(Collections.singletonMap("event", "error"));
                    activeSpan.log(Collections.singletonMap("error.object", statusInfo.getFamily()));
                }
            } finally {
                activeSpan.finish();
                Object scopeObj = requestContext.getProperty(Scope.class.getName());
                if (scopeObj != null && scopeObj instanceof Scope) {
                    try (Scope scope = (Scope) scopeObj) {
                        requestContext.removeProperty(Scope.class.getName());
                    }
                }
            }
        }
    }

    private boolean shouldTrace(ClientRequestContext requestContext) {
        Object traceFilter = requestContext.getConfiguration().getProperty(REQUEST_CONTEXT_TRACING_PREDICATE);
        if (traceFilter instanceof Predicate) {
            return ((Predicate<ClientRequestContext>) traceFilter).test(requestContext);
        }
        return true;
    }

    /**
     * Class used for converting a MultivaluedMap from Headers to a TextMap, to allow it to be injected into the Tracer.
     */
    private class MultivaluedMapToTextMap implements TextMap {

        private final MultivaluedMap<String, Object> map;

        /**
         * Initialises this object with the MultivaluedMap to wrap.
         *
         * @param map The MultivaluedMap to convert to a TextMap
         */
        public MultivaluedMapToTextMap(MultivaluedMap<String, Object> map) {
            this.map = map;
        }

        @Override
        public Iterator<Map.Entry<String, String>> iterator() {
            return new MultiValuedMapStringIterator(map.entrySet());

        }

        @Override
        public void put(String key, String value) {
            map.add(key, value);
        }

    }

    private class MultiValuedMapStringIterator implements Iterator<Map.Entry<String, String>> {

        private final Iterator<Map.Entry<String, List<Object>>> mapIterator;

        private Map.Entry<String, List<Object>> mapEntry;
        private Iterator<Object> mapEntryIterator;

        public MultiValuedMapStringIterator(Set<Map.Entry<String, List<Object>>> entrySet){
            mapIterator = entrySet.iterator();
        }

        @Override
        public boolean hasNext() {
            // True if the MapEntry (value) is not equal to null and has another value, or if there is another key
            return ((mapEntryIterator != null && mapEntryIterator.hasNext()) || mapIterator.hasNext());
        }

        @Override
        public Map.Entry<String, String> next() {
            if (mapEntry == null || (!mapEntryIterator.hasNext() && mapIterator.hasNext())) {
                mapEntry = mapIterator.next();
                mapEntryIterator = mapEntry.getValue().iterator();
            }

            // Return either the next map entry with toString, or an entry with no value if there isn't one
            if (mapEntryIterator.hasNext()) {
                return new AbstractMap.SimpleImmutableEntry<>(mapEntry.getKey(), mapEntryIterator.next().toString());
            }
            return new AbstractMap.SimpleImmutableEntry<>(mapEntry.getKey(), null);
        }


    }

}
