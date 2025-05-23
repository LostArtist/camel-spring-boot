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
package org.apache.camel.itest.springboot.util;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.itest.springboot.ITestConfig;
import org.apache.camel.itest.springboot.ITestConfigBuilder;
import org.apache.camel.itest.springboot.arquillian.SpringBootZipExporterImpl;
import org.apache.commons.io.IOUtils;
import org.jboss.arquillian.container.se.api.ClassPath;
import org.jboss.arquillian.core.spi.InvocationException;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.Configuration;
import org.jboss.shrinkwrap.api.ConfigurationBuilder;
import org.jboss.shrinkwrap.api.Domain;
import org.jboss.shrinkwrap.api.ExtensionLoader;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.impl.base.ServiceExtensionLoader;
import org.jboss.shrinkwrap.impl.base.URLPackageScanner;
import org.jboss.shrinkwrap.impl.base.asset.AssetUtil;
import org.jboss.shrinkwrap.impl.base.path.BasicPath;
import org.jboss.shrinkwrap.resolver.api.maven.ConfigurableMavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.PomEquippedResolveStage;
import org.jboss.shrinkwrap.resolver.api.maven.ScopeType;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinates;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependencies;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependency;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependencyExclusion;

/**
 * Packages a module in a spring-boot compatible nested-jar structure.
 */
public final class ArquillianPackager {

    /**
     * Spring-boot 1.4+ packaging model
     */
    private static final String LIB_FOLDER = "/BOOT-INF/lib";
    private static final String CLASSES_FOLDER = "BOOT-INF/classes";

    private static final Pattern PROP_PATTERN = Pattern.compile("(\\$\\{[^}]*})");

    private ArquillianPackager() {
    }

    public static Archive<?> springBootPackage(ITestConfig config) throws Exception {
        if (!new File(".").getCanonicalFile().getName().equals("camel-itest-spring-boot")) {
            throw new IllegalStateException(
                    "In order to run the integration tests, 'camel-itest-spring-boot' must be the working directory. Check your configuration.");
        }

        ExtensionLoader extensionLoader = new ServiceExtensionLoader(Collections.singleton(getExtensionClassloader()));
        extensionLoader.addOverride(ZipExporter.class, SpringBootZipExporterImpl.class);
        ConfigurationBuilder builder = new ConfigurationBuilder().extensionLoader(extensionLoader);
        Configuration conf = builder.build();

        Domain domain = ShrinkWrap.createDomain(conf);

        JavaArchive ark = domain.getArchiveFactory().create(JavaArchive.class, "test.jar");
        ark = ark.addAsManifestResource("BOOT-MANIFEST.MF", "MANIFEST.MF");
        ark = ark.addAsDirectories(LIB_FOLDER);
        ark = ark.addAsDirectories(CLASSES_FOLDER);

        if (config.getUseCustomLog()) {
            // Spring loads logback-spring not spring-logback
            ark = ark.addAsResource("logback-spring.xml", CLASSES_FOLDER + "/logback-spring.xml");
        }

        for (Map.Entry<String, String> res : config.getResources().entrySet()) {
            ark = ark.addAsResource(res.getKey(), CLASSES_FOLDER + "/" + res.getValue());
        }

        String version = System.getProperty("version_org.apache.camel:camel-core");
        if (version == null) {
            version = config.getMavenVersion();
        }
        if (version == null) {
            // It is missing when launching from IDE
            PomEquippedResolveStage pom = resolver(config).loadPomFromFile("pom.xml");
            MavenResolvedArtifact[] resolved = pom.importCompileAndRuntimeDependencies().resolve().withoutTransitivity()
                    .asResolvedArtifact();
            for (MavenResolvedArtifact dep : resolved) {
                if (dep.getCoordinate().getGroupId().equals("org.apache.camel.springboot")) {
                    version = dep.getCoordinate().getVersion();
                    break;
                }
            }
        }

        debug(config, "Resolved version: " + version);
        if (version == null) {
            throw new IllegalStateException("Cannot determine the current version of the camel component");
        }

        List<MavenDependencyExclusion> commonExclusions = new LinkedList<>();
        commonExclusions.add(MavenDependencies.createExclusion("commons-logging", "commons-logging"));
        commonExclusions.add(MavenDependencies.createExclusion("org.slf4j", "slf4j-log4j12"));
        commonExclusions.add(MavenDependencies.createExclusion("log4j", "log4j"));
        commonExclusions.add(MavenDependencies.createExclusion("log4j", "log4j-slf4j2-impl"));
        commonExclusions.add(MavenDependencies.createExclusion("org.apache.logging.log4j", "log4j"));
        commonExclusions.add(MavenDependencies.createExclusion("org.apache.logging.log4j", "log4j-core"));
        commonExclusions.add(MavenDependencies.createExclusion("org.apache.logging.log4j", "log4j-slf4j2-impl"));
        commonExclusions.add(MavenDependencies.createExclusion("log4j", "apache-log4j-extras"));
        commonExclusions.add(MavenDependencies.createExclusion("org.slf4j", "slf4j-simple"));
        commonExclusions.add(MavenDependencies.createExclusion("org.slf4j", "slf4j-jdk14"));
        commonExclusions.add(MavenDependencies.createExclusion("ch.qos.logback", "logback-classic"));
        commonExclusions.add(MavenDependencies.createExclusion("ch.qos.logback", "logback-core"));

        for (String ex : config.getMavenExclusions()) {
            commonExclusions.add(MavenDependencies.createExclusion(ex));
        }

        // Module dependencies
        List<MavenDependency> additionalDependencies = new LinkedList<>();
        for (String canonicalForm : config.getAdditionalDependencies()) {
            MavenCoordinate coord = MavenCoordinates.createCoordinate(canonicalForm);
            MavenDependency dep = MavenDependencies.createDependency(coord, ScopeType.RUNTIME, false);
            additionalDependencies.add(dep);
        }

        List<String> providedDependenciesXml = new LinkedList<>();
        List<ScopeType> scopes = new LinkedList<>();
        if (config.getIncludeProvidedDependencies() || config.getIncludeTestDependencies()
                || config.getUnitTestEnabled()) {

            if (config.getIncludeTestDependencies() || config.getUnitTestEnabled()) {
                scopes.add(ScopeType.TEST);
            }
            if (config.getIncludeProvidedDependencies()) {
                providedDependenciesXml.addAll(DependencyResolver
                        .getDependencies(config.getModuleBasePath() + "/pom.xml", ScopeType.PROVIDED.toString()));
                scopes.add(ScopeType.PROVIDED);
            }

        }

        List<String> cleanProvidedDependenciesXml = new LinkedList<>();
        for (String depXml : providedDependenciesXml) {
            if (validTestDependency(config, depXml, commonExclusions)) {
                depXml = useBOMVersionIfPresent(config, depXml);
                depXml = enforceExclusions(depXml, commonExclusions);
                depXml = switchToStarterIfPresent(depXml);
                cleanProvidedDependenciesXml.add(depXml);
            }
        }

        File moduleSpringBootPom = createUserPom(config, cleanProvidedDependenciesXml);

        List<ScopeType> resolvedScopes = new LinkedList<>();
        resolvedScopes.add(ScopeType.COMPILE);
        resolvedScopes.add(ScopeType.RUNTIME);
        resolvedScopes.addAll(scopes);

        Set<MavenResolvedArtifact> runtimeDependencies;
        try {
            runtimeDependencies = new HashSet<>(Arrays.asList(resolver(config).loadPomFromFile(moduleSpringBootPom)
                    .importDependencies(resolvedScopes.toArray(new ScopeType[0]))
                    .addDependencies(additionalDependencies).resolve().withTransitivity().asResolvedArtifact()));
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }

        // Load the test dependencies from the actual starter pom with transitivity
        // since most are defined in the parent pom
        Set<MavenResolvedArtifact> testProvidedDependenciesPom;
        try {
            testProvidedDependenciesPom = new HashSet<>(
                    Arrays.asList(resolver(config).loadPomFromFile(new File(config.getModuleBasePath() + "/pom.xml"))
                            .importTestDependencies().resolve().withTransitivity().asResolvedArtifact()));
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }

        // Remove duplicates between test and runtime dependencies
        Set<MavenResolvedArtifact> jUnitDependenciesDuplicates =
                getJUnitDependenciesDuplicates(runtimeDependencies, testProvidedDependenciesPom);

        runtimeDependencies.addAll(testProvidedDependenciesPom);
        List<MavenResolvedArtifact> dependencyArtifacts = new LinkedList<>(runtimeDependencies);
        lookForVersionMismatch(config, dependencyArtifacts);

        // Remove duplicates after the version mismatch analysis
        dependencyArtifacts.removeAll(jUnitDependenciesDuplicates);

        List<File> dependencies = new LinkedList<>();
        for (MavenResolvedArtifact a : dependencyArtifacts) {
            dependencies.add(a.asFile());
        }

        // The spring boot-loader dependency will be added to the main jar, so it should be excluded from the embedded
        // ones
        excludeDependencyRegex(dependencies, "^spring-boot-loader-[0-9].*");

        // Add all dependencies as spring-boot nested jars
        ark = addDependencies(config, ark, dependencies);

        // Add common packages to main jar
        ark = ark.addPackages(true, "org.jboss.shrinkwrap");

        // Add current classes to both location to be used by different classloaders
        ark = ark.addPackages(true, "org.apache.camel.itest.springboot");
        ark = addSpringbootPackage(ark, "org.apache.camel.itest.springboot");

        // CAMEL-10060 is resolved since 2.18 but some unit tests use custom (non spring-boot enabled) camel contexts
        ark = ark.addPackages(true, "org.apache.camel.converter.myconverter");

        ark = ark.addPackages(true, "org.springframework.boot.loader");

        ClassPath.Builder external = ClassPath.builder().add(ark);

        // overcome limitations of some JDKs
        external.addSystemProperty("javax.xml.accessExternalDTD", "all");
        external.addSystemProperty("javax.xml.accessExternalSchema", "all");
        // Set a java logging configuration which will not log to console
        external.addSystemProperty("java.util.logging.config.file",
                new File(".").getCanonicalPath() + "/target/test-classes/jul.properties");

        if (config.getUnitTestEnabled()) {
            external.addSystemProperty("container.user.dir", new File(config.getModuleBasePath()).getCanonicalPath());
            external.addSystemProperty("container.test.resources.dir",
                    new File(config.getModuleBasePath()).getCanonicalPath() + "/target/test-classes");
        }

        // Adding configuration properties
        for (Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
            if (e.getKey() instanceof String key && e.getValue() instanceof String) {
                if (key.startsWith(ITestConfigBuilder.CONFIG_PREFIX)) {
                    external.addSystemProperty(key, (String) e.getValue());
                }
            }
        }

        for (Map.Entry<String, String> e : config.getSystemProperties().entrySet()) {
            external.addSystemProperty(e.getKey(), e.getValue());
        }

        return external.build();
    }

    /**
     * In case Camel and Camel Spring Boot declare different JUnit versions, use the Spring Boot one to prevent issues
     *
     * @param runtimeDependencies
     * @param testProvidedDependencies
     * @return
     */
    private static Set<MavenResolvedArtifact> getJUnitDependenciesDuplicates(Set<MavenResolvedArtifact> runtimeDependencies, Set<MavenResolvedArtifact> testProvidedDependencies) {
        Set<MavenResolvedArtifact> junitRuntimeDependencies =
                runtimeDependencies.stream()
                        .filter(d -> d.getCoordinate().getArtifactId().startsWith("junit"))
                        .collect(Collectors.toSet());

        // For each JUnit runtime (Spring Boot) dependency, find test dependencies with same artifactId and groupId but different version
        return junitRuntimeDependencies.stream()
                .flatMap(junitDep -> testProvidedDependencies.stream()
                        .filter(testDep -> isSameArtifactWithDifferentVersion(junitDep, testDep)))
                .collect(Collectors.toSet());
    }

    /**
     * Checks if two artifacts have the same artifactId and groupId but different versions.
     *
     * @param artifact1 First artifact to compare
     * @param artifact2 Second artifact to compare
     * @return true if artifacts have same artifactId and groupId but different versions
     */
    private static boolean isSameArtifactWithDifferentVersion(
            MavenResolvedArtifact artifact1,
            MavenResolvedArtifact artifact2) {

        return artifact1.getCoordinate().getArtifactId().equals(artifact2.getCoordinate().getArtifactId()) &&
                artifact1.getCoordinate().getGroupId().equals(artifact2.getCoordinate().getGroupId()) &&
                !artifact1.getCoordinate().getVersion().equals(artifact2.getCoordinate().getVersion());
    }

    private static void lookForVersionMismatch(ITestConfig config, List<MavenResolvedArtifact> dependencyArtifacts) {
        Set<String> ignore = new HashSet<>(config.getIgnoreLibraryMismatch());

        // A list of known libraries that don't follow the all-artifacts-same-version convention
        ignore.add("com.atlassian.jira:jira-rest-java-client-api");
        ignore.add("com.fasterxml.jackson.module:jackson-module-scala_2.11"); // latest version not available
        ignore.add("com.github.jnr");
        ignore.add("com.sun.xml.bind:jaxb-xjc");
        ignore.add("com.azure:azure");
        ignore.add("com.datastax.oss:java");
        ignore.add("com.jcabi:jcabi");
        ignore.add("commons-beanutils:commons-beanutils");
        ignore.add("io.dropwizard.metrics:metrics-json"); // PR to spring-boot
        ignore.add("io.dropwizard.metrics:metrics-jvm"); // PR to spring-boot
        ignore.add("io.fabric8:kubernetes-");
        ignore.add("io.netty:netty:jar"); // an old version
        ignore.add("io.netty:netty-tcnative-boringssl-static");
        ignore.add("io.swagger:swagger-parser");
        ignore.add("io.opencensus:opencensus-");
        ignore.add("io.opentracing.contrib:opentracing-");
        ignore.add("org.apache.commons");
        ignore.add("org.apache.curator");
        ignore.add("org.apache.cxf:cxf-api");
        ignore.add("org.apache.geronimo.specs");
        ignore.add("org.apache.flink:flink-shaded-asm");
        ignore.add("org.apache.flink:flink-shaded-guava");
        ignore.add("org.apache.flink:flink-shaded-jackson");
        ignore.add("org.apache.flink:flink-shaded-netty");
        ignore.add("org.apache.logging.log4j:log4j-jcl");
        ignore.add("org.apache.maven");
        ignore.add("org.apache.parquet");
        ignore.add("org.apache.velocity");
        ignore.add("org.apache.qpid:qpid-jms-client");
        ignore.add("org.opensaml");
        ignore.add("org.ow2.asm"); // No problem
        ignore.add("org.codehaus.plexus");
        ignore.add("org.eclipse.jetty.websocket:websocket-api"); // PR to spring-boot
        ignore.add("org.jboss.arquillian.container");
        ignore.add("org.jboss:");
        ignore.add("org.hibernate:hibernate-validator"); // does not match with hibernate-core
        ignore.add("org.mortbay.jetty:servlet-api-2.5");
        ignore.add("org.scala-lang:scala-compiler");
        ignore.add("org.slf4j:slf4j-ext"); // PR to spring-boot
        ignore.add("org.easytesting");
        ignore.add("net.java.dev.jna:jna-platform"); // PR to spring-boot
        ignore.add("net.openhft");
        ignore.add("org.scala-lang.modules:scala-java8-compat_2.11");
        ignore.add("org.scala-lang.modules:scala-parser-combinators_2.12");
        ignore.add("org.scala-lang.modules:scala-xml_2.12");
        ignore.add("net.sourceforge.htmlunit:htmlunit-core-js"); // v 2.21 does not exist
        ignore.add("org.springframework.cloud"); // too many different versions
        ignore.add("org.springframework.data");
        ignore.add("org.springframework.security:spring-security-jwt");
        ignore.add("org.springframework.security:spring-security-rsa");
        ignore.add("org.springframework.social");
        ignore.add("org.webjars"); // No problem
        ignore.add("stax:stax-api");
        ignore.add("xml-apis:xml-apis-ext");
        ignore.add("org.jboss.logging");
        ignore.add("org.jboss.marshalling");
        ignore.add("org.jgroups:jgroups-raft");
        ignore.add("net.sourceforge.htmlunit:htmlunit");
        ignore.add("ai.djl.mxnet:mxnet-engine");
        ignore.add("ai.djl.mxnet:mxnet-model-zoo");
        ignore.add("ai.djl.mxnet:mxnet-native-auto");
        ignore.add("org.sonatype.plexus");
        ignore.add("org.codehaus.plexus");
        ignore.add("org.sonatype.sisu");
        ignore.add("com.healthmarketscience.jackcess");
        ignore.add("com.google.cloud");
        ignore.add("com.google.api");
        ignore.add("com.google.http-client");
        ignore.add("org.eclipse.rdf4j");
        ignore.add("org.scala-lang");
        ignore.add("org.datanucleus");
        ignore.add("org.apache.hive");

        // these are from camel-spring-boot and not camel repo so ignore them
        ignore.add("org.apache.camel:camel-spring-boot");

        // google grpc is a mix of all sort of different versions
        ignore.add("com.google.api.grpc");

        // microsoft azure msal4j-persistence-extension library introduced by datalake component
        ignore.add("com.microsoft.azure:msal4j-persistence-extension");

        Map<String, Map<String, Set<String>>> status = new TreeMap<>();
        Set<String> mismatches = new TreeSet<>();
        Set<String> potentialMismatches = new TreeSet<>();
        for (MavenResolvedArtifact a : dependencyArtifacts) {
            boolean ignoreCheck = false;
            String identifier = getIdentifier(a);
            for (String i : ignore) {
                if (identifier.startsWith(i)) {
                    ignoreCheck = true;
                    break;
                }
            }
            if (ignoreCheck) {
                continue;
            }

            String group = a.getCoordinate().getGroupId();
            String artifact = a.getCoordinate().getArtifactId();
            String version = a.getCoordinate().getVersion();

            String artifactPrefix = artifact;
            if (artifactPrefix.contains("-")) {
                artifactPrefix = artifactPrefix.substring(0, artifactPrefix.indexOf("-"));
            }
            String prefixId = group + ":" + artifactPrefix;

            if (!status.containsKey(prefixId)) {
                status.put(prefixId, new TreeMap<>());
            }

            for (Map.Entry<String, Set<String>> entry : status.get(prefixId).entrySet()) {
                Set<String> anotherVersions = entry.getValue();
                if (anotherVersions.size() == 1 && !sameVersion(anotherVersions.iterator().next(), version)) {
                    if (identifier.equals(entry.getKey())) {
                        // Same coordinates but different versions
                        mismatches.add(prefixId);
                    } else {
                        // Same prefix but different versions
                        potentialMismatches.add(prefixId);
                    }
                }
            }

            status.get(prefixId).computeIfAbsent(identifier, k -> new LinkedHashSet<>()).add(version);
        }

        StringBuilder message = new StringBuilder();
        for (String mismatch : mismatches) {
            message.append("Found mismatch for dependency ").append(mismatch).append(":\n");
            Map<String, Set<String>> artifacts = status.get(mismatch);
            for (String art : artifacts.keySet()) {
                Set<String> versions = artifacts.get(art);
                message.append(" - ").append(art).append(" --> ").append(versions).append("\n");
            }
        }

        if (message.length() > 0) {
            String alert = "Library version mismatch found.\n" + message;
            if (config.getFailOnRelatedLibraryMismatch()) {
                throw new InvocationException(new RuntimeException(alert));
            } else {
                warn(alert);
            }
        }
        for (String mismatch : potentialMismatches) {
            message.setLength(0);
            message.append("Found a potential version mismatch for dependency ").append(mismatch).append(":\n");
            Map<String, Set<String>> artifacts = status.get(mismatch);
            for (String art : artifacts.keySet()) {
                Set<String> versions = artifacts.get(art);
                message.append(" - ").append(art).append(" --> ").append(versions).append("\n");
            }
            warn(message.toString());
        }
    }

    private static boolean sameVersion(String v1, String v2) {
        if (v1.indexOf(".") != v1.lastIndexOf(".") && v2.indexOf(".") != v2.lastIndexOf(".")) {
            // truncate up to minor version
            int v1MinSplit = v1.indexOf(".", v1.indexOf(".") + 1);
            v1 = v1.substring(0, v1MinSplit);

            int v2MinSplit = v2.indexOf(".", v2.indexOf(".") + 1);
            v2 = v2.substring(0, v2MinSplit);
        }

        return v1.equals(v2);
    }

    private static String getIdentifier(MavenResolvedArtifact a) {
        return a.getCoordinate().getGroupId() + ":" + a.getCoordinate().getArtifactId() + ":"
                + a.getCoordinate().getType() + ":" + a.getCoordinate().getClassifier();
    }

    private static File createUserPom(ITestConfig config, List<String> cleanTestProvidedDependencies) throws Exception {
        String pom;
        String template = "/application-pom-sb" + config.getSpringBootMajorVersion() + ".xml";
        try (InputStream pomTemplate = ArquillianPackager.class.getResourceAsStream(template)) {
            pom = IOUtils.toString(Objects.requireNonNull(pomTemplate), StandardCharsets.UTF_8);
        }

        StringBuilder dependencies = new StringBuilder();
        for (String dep : cleanTestProvidedDependencies) {
            dependencies.append(dep);
            dependencies.append("\n");
        }

        pom = pom.replace("<!-- DEPENDENCIES -->", dependencies.toString());

        Map<String, String> resolvedProperties = new TreeMap<>();
        Matcher m = PROP_PATTERN.matcher(pom);
        while (m.find()) {
            String property = m.group();
            String resolved = DependencyResolver
                    .resolveModuleOrParentProperty(new File(new File(config.getModuleBasePath()), "pom.xml"), property);
            resolvedProperties.put(property, resolved);
        }

        for (String property : resolvedProperties.keySet()) {
            pom = pom.replace(property, resolvedProperties.get(property));
        }

        pom = pom.replace("#{module}", config.getModuleName());

        // some modules are not in component-starter
        String path = config.getModuleBasePath();
        File pomFile = new File(path + "/target/itest-spring-boot-pom.xml");
        pomFile.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(pomFile)) {
            IOUtils.write(pom, fw);
        }

        return pomFile;
    }

    private static ConfigurableMavenResolverSystem resolver(ITestConfig config) {
        return Maven.configureResolver().workOffline(config.getMavenOfflineResolution());
    }

    private static ClassLoader getExtensionClassloader() {
        ClassLoader cl = AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
            @Override
            public ClassLoader run() {
                return Thread.currentThread().getContextClassLoader();
            }
        });
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }

        return cl;
    }

    private static boolean validTestDependency(ITestConfig config, String dependencyXml,
            List<MavenDependencyExclusion> exclusions) {

        boolean valid = true;
        for (MavenDependencyExclusion excl : exclusions) {
            String groupId = excl.getGroupId();
            String artifactId = excl.getArtifactId();

            boolean notExclusion = !dependencyXml.contains("<exclusions>")
                    || dependencyXml.indexOf(groupId) < dependencyXml.indexOf("<exclusions>");

            if (dependencyXml.contains(groupId) && dependencyXml.contains(artifactId) && notExclusion) {
                valid = false;
                break;
            }
        }

        if (!valid) {
            debug(config, "Discarded test dependency: "
                    + dependencyXml.replace("\n", "").replace("\r", "").replace("\t", ""));
        }

        return valid;
    }

    private static String enforceExclusions(String dependencyXml, List<MavenDependencyExclusion> exclusions) {

        if (!dependencyXml.contains("<exclusions>")) {
            dependencyXml = dependencyXml.replace("</dependency>", "<exclusions></exclusions></dependency>");
        }

        for (MavenDependencyExclusion excl : exclusions) {
            String groupId = excl.getGroupId();
            String artifactId = excl.getArtifactId();

            dependencyXml = dependencyXml.replace("</exclusions>", "<exclusion><groupId>" + groupId
                    + "</groupId><artifactId>" + artifactId + "</artifactId></exclusion></exclusions>");
        }

        return dependencyXml;
    }

    private static String switchToStarterIfPresent(String dependencyXml) {

        String groupId = textBetween(dependencyXml, "<groupId>", "</groupId>");
        String artifactId = textBetween(dependencyXml, "<artifactId>", "</artifactId>");
        String type = textBetween(dependencyXml, "<type>", "</type>");

        if ("org.apache.camel".equals(groupId) && artifactId.startsWith("camel-") && !"test-jar".equals(type)) {
            String starterArtifact = artifactId + "-starter";
            File starterFile = new File("../../components-starter/" + starterArtifact);
            if (starterFile.exists()) {
                dependencyXml = dependencyXml.replace(artifactId, starterArtifact);
            }
        }

        return dependencyXml;
    }

    private static String useBOMVersionIfPresent(ITestConfig config, String dependencyXml) {

        String groupId = textBetween(dependencyXml, "<groupId>", "</groupId>");
        String artifactId = textBetween(dependencyXml, "<artifactId>", "</artifactId>");

        String version = config.getTestLibraryVersions().get(groupId + ":" + artifactId);
        boolean stripVersion = false;
        if (version == null) {
            boolean testsLib = dependencyXml.contains("<classifier>tests");
            stripVersion = !testsLib && BOMResolver.getInstance(config).getBOMVersion(groupId, artifactId) != null;
            // skip if its org.infinispan as we need explict version to work-around a problem when using test-jar
            // dependencies
            if ("org.infinispan".equals(groupId)) {
                stripVersion = false;
            }
        }

        if (version != null) {
            if (dependencyXml.contains("<version>")) {
                int from = dependencyXml.indexOf("<version>") + 9;
                int to = dependencyXml.indexOf("</version>");

                dependencyXml = dependencyXml.substring(0, from) + version + dependencyXml.substring(to);
            } else {
                String kw = "</artifactId>";
                int pos = dependencyXml.indexOf(kw) + kw.length();
                dependencyXml = dependencyXml.substring(0, pos) + "<version>" + version + "</version>"
                        + dependencyXml.substring(pos);
            }
        } else if (stripVersion && dependencyXml.contains("<version>")) {
            int from = dependencyXml.indexOf("<version>");
            int to = dependencyXml.indexOf("</version>") + 10;
            dependencyXml = dependencyXml.substring(0, from) + dependencyXml.substring(to);
        }

        return dependencyXml;
    }

    private static String textBetween(String text, String start, String end) {
        int sp = text.indexOf(start);
        int rsp = sp + start.length();
        int ep = text.indexOf(end);
        if (sp < 0 || ep < 0 || ep <= rsp) {
            return "";
        }

        return text.substring(rsp, ep);
    }

    private static boolean excludeDependencyRegex(List<File> dependencies, String regex) {
        Pattern pattern = Pattern.compile(regex);
        int count = 0;
        for (Iterator<File> it = dependencies.iterator(); it.hasNext();) {
            File f = it.next();
            if (pattern.matcher(f.getName()).matches()) {
                it.remove();
                count++;
                break;
            }
        }
        return count > 0;
    }

    private static JavaArchive addDependencies(ITestConfig config, JavaArchive ark, Collection<File> deps) {
        Set<File> dependencySet = new HashSet<>(deps);
        for (File d : dependencySet) {
            debug(config, "Adding spring-boot dependency: " + d.getName());
            ark = ark.add(new FileAsset(d), LIB_FOLDER + "/" + d.getName());
        }

        return ark;
    }

    private static JavaArchive addSpringbootPackage(JavaArchive ark, String... packageNames) {

        Iterable<ClassLoader> classLoaders = Collections.singleton(Thread.currentThread().getContextClassLoader());

        for (String packageName : packageNames) {
            for (final ClassLoader classLoader : classLoaders) {

                final URLPackageScanner.Callback callback = new URLPackageScanner.Callback() {
                    @Override
                    public void classFound(String className, Asset asset) {
                        ArchivePath classNamePath = AssetUtil.getFullPathForClassResource(className);

                        asset = new ClassLoaderAsset(classNamePath.get().substring(1), classLoader);
                        ArchivePath location = new BasicPath(CLASSES_FOLDER + "/", classNamePath);
                        ark.add(asset, location);
                    }
                };
                final URLPackageScanner scanner = URLPackageScanner.newInstance(true, classLoader, callback,
                        packageName);
                scanner.scanPackage();
            }
        }

        return ark;
    }

    private static void debug(ITestConfig config, String str) {
        if (config.getDebugEnabled()) {
            System.out.println("DEBUG>>> " + str);
        }
    }

    private static void warn(String str) {
        System.out.println("WARN>>> " + str);
    }
}
