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
package org.apache.camel.spring.boot.vault;

import org.apache.camel.CamelContext;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext
@CamelSpringBootTest
@EnableAutoConfiguration
@SpringBootTest(classes = { KubernetesConfigMapVaultConfigurationTest.class }, properties = {
        "camel.vault.kubernetescm.refreshEnabled=true", "camel.vault.kubernetescm.configmaps=myconfigmap" })
public class KubernetesConfigMapVaultConfigurationTest {

    @Autowired
    private CamelContext camelContext;

    @Test
    public void testKubernetesVault() throws Exception {
        Assertions.assertEquals(true, camelContext.getVaultConfiguration().kubernetesConfigmaps().isRefreshEnabled());
        Assertions.assertEquals("myconfigmap", camelContext.getVaultConfiguration().kubernetesConfigmaps().getConfigmaps());
    }
}
