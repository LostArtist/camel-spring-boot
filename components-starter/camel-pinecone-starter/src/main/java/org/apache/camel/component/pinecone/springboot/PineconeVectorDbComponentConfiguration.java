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
package org.apache.camel.component.pinecone.springboot;

import org.apache.camel.component.pinecone.PineconeVectorDbConfiguration;
import org.apache.camel.spring.boot.ComponentConfigurationPropertiesCommon;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Perform operations on the Pinecone Vector Database.
 * 
 * Generated by camel-package-maven-plugin - do not edit this file!
 */
@ConfigurationProperties(prefix = "camel.component.pinecone")
public class PineconeVectorDbComponentConfiguration
        extends
            ComponentConfigurationPropertiesCommon {

    /**
     * Whether to enable auto configuration of the pinecone component. This is
     * enabled by default.
     */
    private Boolean enabled;
    /**
     * Sets the cloud type to use (aws/gcp/azure)
     */
    private String cloud;
    /**
     * Sets the cloud region
     */
    private String cloudRegion;
    /**
     * Sets the Collection Dimension to use (1-1536)
     */
    private Integer collectionDimension = 1536;
    /**
     * Sets the Collection Similarity Metric to use
     * (cosine/euclidean/dotproduct)
     */
    private String collectionSimilarityMetric;
    /**
     * The configuration;. The option is a
     * org.apache.camel.component.pinecone.PineconeVectorDbConfiguration type.
     */
    private PineconeVectorDbConfiguration configuration;
    /**
     * Sets a custom host URL to connect to
     */
    private String host;
    /**
     * Sets the index name to use
     */
    private String indexName;
    /**
     * Whether the producer should be started lazy (on the first message). By
     * starting lazy you can use this to allow CamelContext and routes to
     * startup in situations where a producer may otherwise fail during starting
     * and cause the route to fail being started. By deferring this startup to
     * be lazy then the startup failure can be handled during routing messages
     * via Camel's routing error handlers. Beware that when the first message is
     * processed then creating and starting the producer may take a little time
     * and prolong the total processing time of the processing.
     */
    private Boolean lazyStartProducer = false;
    /**
     * Set the proxy host
     */
    private String proxyHost;
    /**
     * Set the proxy port
     */
    private Integer proxyPort;
    /**
     * Whether the client uses Transport Layer Security (TLS) to secure
     * communications
     */
    private Boolean tls = true;
    /**
     * Sets the API key to use for authentication
     */
    private String token;
    /**
     * Whether autowiring is enabled. This is used for automatic autowiring
     * options (the option must be marked as autowired) by looking up in the
     * registry to find if there is a single instance of matching type, which
     * then gets configured on the component. This can be used for automatic
     * configuring JDBC data sources, JMS connection factories, AWS Clients,
     * etc.
     */
    private Boolean autowiredEnabled = true;

    public String getCloud() {
        return cloud;
    }

    public void setCloud(String cloud) {
        this.cloud = cloud;
    }

    public String getCloudRegion() {
        return cloudRegion;
    }

    public void setCloudRegion(String cloudRegion) {
        this.cloudRegion = cloudRegion;
    }

    public Integer getCollectionDimension() {
        return collectionDimension;
    }

    public void setCollectionDimension(Integer collectionDimension) {
        this.collectionDimension = collectionDimension;
    }

    public String getCollectionSimilarityMetric() {
        return collectionSimilarityMetric;
    }

    public void setCollectionSimilarityMetric(String collectionSimilarityMetric) {
        this.collectionSimilarityMetric = collectionSimilarityMetric;
    }

    public PineconeVectorDbConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(PineconeVectorDbConfiguration configuration) {
        this.configuration = configuration;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public Boolean getLazyStartProducer() {
        return lazyStartProducer;
    }

    public void setLazyStartProducer(Boolean lazyStartProducer) {
        this.lazyStartProducer = lazyStartProducer;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public Boolean getTls() {
        return tls;
    }

    public void setTls(Boolean tls) {
        this.tls = tls;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Boolean getAutowiredEnabled() {
        return autowiredEnabled;
    }

    public void setAutowiredEnabled(Boolean autowiredEnabled) {
        this.autowiredEnabled = autowiredEnabled;
    }
}