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
package org.apache.camel.spring.boot;

import jakarta.annotation.PreDestroy;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.main.Main;
import org.apache.camel.main.MainListener;
import org.apache.camel.main.MainShutdownStrategy;
import org.apache.camel.main.SimpleMainShutdownStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.Map;

public class CamelSpringBootApplicationController implements CamelContextAware {
    private static final Logger LOG = LoggerFactory.getLogger(CamelSpringBootApplicationController.class);

    private final CamelSpringMain main;
    private CamelContext camelContext;

    public CamelSpringBootApplicationController(final ApplicationContext applicationContext) {
        this.main = new CamelSpringMain(applicationContext, this);
        // inject main listeners
        final Map<String, MainListener> listeners = applicationContext.getBeansOfType(MainListener.class);
        for (MainListener listener : listeners.values()) {
            this.main.addMainListener(listener);
        }
    }

    public Main getMain() {
        return main;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public MainShutdownStrategy getMainShutdownStrategy() {
        return this.main.getShutdownStrategy();
    }

    public Runnable getMainCompletedTask() {
        return main.getCompleteTask();
    }

    /**
     * Runs the application and blocks the main thread and shutdown Camel graceful when the JVM is stopping.
     */
    public void run() {
        LOG.debug("Controller is starting and waiting for Spring-Boot to stop or JVM to terminate");
        try {
            main.run();
            // keep the daemon thread running
            LOG.debug("Waiting for CamelContext to complete shutdown");
            this.main.getShutdownStrategy().await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        LOG.debug("CamelContext shutdown complete.");
    }

    /**
     * @deprecated use {@link #run()}
     */
    @Deprecated
    public void blockMainThread() {
        run();
    }

    // The method cannot to be private to prevent a failure at startup in native mode
    // Refer to https://github.com/spring-projects/spring-framework/pull/30654 for more details
    @PreDestroy
    void destroy() {
        main.completed();
    }

    private static class CamelSpringMain extends Main {
        final ApplicationContext applicationContext;
        final CamelSpringBootApplicationController controller;

        public CamelSpringMain(ApplicationContext applicationContext, CamelSpringBootApplicationController controller) {
            this.applicationContext = applicationContext;
            this.controller = controller;

            // use a simple shutdown strategy that does not install any shutdown hook as spring-boot
            // as spring-boot has its own hook we use
            this.shutdownStrategy = new SimpleMainShutdownStrategy();

            // turn off route collector on main as camel-spring-boot has already discovered the routes
            // and here we just use the main as implementation detail (to keep the jvm running)
            this.mainConfigurationProperties.setRoutesCollectorEnabled(false);
        }

        @Override
        protected ProducerTemplate findOrCreateCamelTemplate() {
            return applicationContext.getBean(ProducerTemplate.class);
        }

        @Override
        protected CamelContext createCamelContext() {
            return controller.camelContext;
        }

        @Override
        protected void postProcessCamelContext(CamelContext camelContext) throws Exception {
            // spring boot has configured camel context and no post processing is needed
        }
    }

}
