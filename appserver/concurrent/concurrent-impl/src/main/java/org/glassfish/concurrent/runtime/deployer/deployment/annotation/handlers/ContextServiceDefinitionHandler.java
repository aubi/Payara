/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) [2022] Payara Foundation and/or its affiliates. All rights reserved.
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
package org.glassfish.concurrent.runtime.deployer.deployment.annotation.handlers;

import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.deployment.ContextServiceDefinitionDescriptor;
import com.sun.enterprise.deployment.ResourceDescriptor;
import com.sun.enterprise.deployment.annotation.context.ResourceContainerContext;
import com.sun.enterprise.deployment.annotation.handlers.AbstractResourceHandler;
import jakarta.enterprise.concurrent.ContextServiceDefinition;
import jakarta.inject.Inject;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.apf.AnnotationHandlerFor;
import org.glassfish.apf.AnnotationInfo;
import org.glassfish.apf.AnnotationProcessorException;
import org.glassfish.apf.HandlerProcessingResult;
import org.glassfish.concurrent.runtime.ConcurrentRuntime;
import org.glassfish.concurrent.runtime.deployer.ContextServiceConfig;
import org.glassfish.config.support.TranslatedConfigView;
import org.glassfish.deployment.common.JavaEEResourceType;
import org.jvnet.hk2.annotations.Service;

/**
 * Handler for @ContextServiceDefinition.
 *
 * @author Petr Aubrecht <aubrecht@asoftware.cz>
 */
@Service
@AnnotationHandlerFor(ContextServiceDefinition.class)
public class ContextServiceDefinitionHandler extends AbstractResourceHandler {

    private static final Logger logger = Logger.getLogger(ContextServiceDefinitionHandler.class.getName());

    @Inject
    private Domain domain;

    @Override
    protected HandlerProcessingResult processAnnotation(AnnotationInfo annotationInfo,
            ResourceContainerContext[] resourceContainerContexts)
            throws AnnotationProcessorException {
        logger.log(Level.INFO, "Entering ContextServiceDefinitionHandler.processAnnotation");
        ContextServiceDefinition contextServiceDefinition = (ContextServiceDefinition) annotationInfo.getAnnotation();

        processSingleAnnotation(contextServiceDefinition, resourceContainerContexts);

        return getDefaultProcessedResult();
    }

    public void processSingleAnnotation(ContextServiceDefinition contextServiceDefinition, ResourceContainerContext[] resourceContainerContexts) {
        //        AnnotatedElement annotatedElement = annotationInfo.getAnnotatedElement();
        //        logger.log(Level.INFO, "Trying to create custom context service by annotation");
        ContextServiceConfig contextServiceConfig = new ContextServiceConfig(contextServiceDefinition.name(),
                "???",
                "true");
        ConcurrentRuntime concurrentRuntime = ConcurrentRuntime.getRuntime();
        // create a context service
        //        ContextServiceImpl managedExecutorServiceImpl =
        concurrentRuntime.getContextService(null, contextServiceConfig);

        // add to contexts
        ContextServiceDefinitionDescriptor cdd = createDescriptor(contextServiceDefinition);
        for (ResourceContainerContext context : resourceContainerContexts) {
            Set<ResourceDescriptor> csddes = context.getResourceDescriptors(JavaEEResourceType.CSDD);
            csddes.add(cdd);
        }
    }

    public ContextServiceDefinitionDescriptor createDescriptor(ContextServiceDefinition contectServiceDefinition) {
        ContextServiceDefinitionDescriptor csdd = new ContextServiceDefinitionDescriptor();
        csdd.setDescription("Context Service Definition");
        csdd.setName(TranslatedConfigView.expandValue(contectServiceDefinition.name()));
//        csdd.setPropagated(Stream.of(contectServiceDefinition.propagated()).map(x -> TranslatedConfigView.expandValue(x)).toArray(String[]::new));
//        csdd.setCleared(Stream.of(contectServiceDefinition.cleared()).map(x -> TranslatedConfigView.expandValue(x)).toArray(String[]::new));
//        csdd.setUnchanged(Stream.of(contectServiceDefinition.unchanged()).map(x -> TranslatedConfigView.expandValue(x)).toArray(String[]::new));
        return csdd;
    }
}
