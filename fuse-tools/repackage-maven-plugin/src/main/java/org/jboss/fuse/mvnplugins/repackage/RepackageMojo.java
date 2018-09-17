package org.jboss.fuse.mvnplugins.repackage;

/*
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
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.classworlds.ClassRealm;
import org.codehaus.classworlds.ClassWorld;
import org.codehaus.classworlds.DuplicateRealmException;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

/**
 * Mojo that repackages an existing maven plugin
 */
@Mojo(
        name = "repackage",
        threadSafe = true,
        requiresProject = true,
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresDependencyResolution = ResolutionScope.RUNTIME
)
public class RepackageMojo extends AbstractMojo {

    private static final String M2E_LIFECYCLE_MAPPING_METADATA_PATH = "META-INF/m2e/lifecycle-mapping-metadata.xml";

    public static final String PLUGIN_DESCRIPTOR_PATH = "META-INF/maven/plugin.xml";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.dependencyArtifacts}", required = true, readonly = true)
    private Set<Artifact> dependencyArtifacts;

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private File outputDirectory;

    @Parameter(defaultValue = "${project.groupId}", readonly = true)
    private String groupId;
    @Parameter(defaultValue = "${project.artifactId}", readonly = true)
    private String artifactId;
    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String version;

    /**
     * @throws MojoExecutionException
     */
    public void execute() throws MojoExecutionException {

        if (!"maven-plugin".equals(project.getPackaging())) {
            getLog().info("Skipping.. This project does not use maven-plugin packaging.");
            return;
        }

        ClassRealm realm = createDependenciesRealm();
        Document document = parsePluginDescriptor(realm.getResource(PLUGIN_DESCRIPTOR_PATH));

        Node node = document.selectSingleNode("//plugin/groupId");
        String originalGroupId = node.getText();
        node.setText(groupId);

        node = document.selectSingleNode("//plugin/artifactId");
        String originalArtifactId = node.getText();
        node.setText(artifactId);

        node = document.selectSingleNode("//plugin/version");
        String originalVersion = node.getText();
        node.setText(version);

        Element dependencies = (Element) document.selectSingleNode("//plugin/dependencies");
        Element dependency = dependencies.addElement("dependency");
        dependency.addElement("groupId").addText(originalGroupId);
        dependency.addElement("artifactId").addText(originalArtifactId);
        dependency.addElement("version").addText(originalVersion);
        dependency.addElement("type").addText("jar");
        dependency.detach();
        dependencies.content().add(0, dependency);

        writePluginDescriptor(document);
        writeM2eConfiguration(realm);
    }

    private void writeM2eConfiguration(ClassRealm realm) throws MojoExecutionException {
        URL m2eFileUrl = realm.getResource(M2E_LIFECYCLE_MAPPING_METADATA_PATH);
        if (m2eFileUrl != null) {
            try (InputStream inputStream = m2eFileUrl.openStream()) {
                writeM2eConfiguration(inputStream);
            } catch (IOException e) {
                throw new MojoExecutionException("Could not write the m2e config file", e);
            }
        }
    }

    private void writeM2eConfiguration(InputStream inputStream) throws IOException {
        File file = new File(outputDirectory, M2E_LIFECYCLE_MAPPING_METADATA_PATH);
        file.getParentFile().mkdirs();
        Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void writePluginDescriptor(Document document) throws MojoExecutionException {
        File file = new File(outputDirectory, PLUGIN_DESCRIPTOR_PATH);
        file.getParentFile().mkdirs();
        try (FileOutputStream os = new FileOutputStream(file)) {
            OutputFormat format = OutputFormat.createPrettyPrint();
            format.setEncoding(StandardCharsets.UTF_8.name());
            XMLWriter writer = new XMLWriter(os, format);
            writer.write(document);
            writer.flush();
        } catch (Exception e) {
            throw new MojoExecutionException("Could not write the maven plugin descriptor", e);
        }
    }

    private Document parsePluginDescriptor(URL url) throws MojoExecutionException {
        getLog().info("pd: " + url);
        try {
            SAXReader saxBuilder = new SAXReader();
            return saxBuilder.read(url);
        } catch (Exception e) {
            throw new MojoExecutionException("Could not parse the maven plugin descriptor", e);
        }
    }

    private ClassRealm createDependenciesRealm() throws MojoExecutionException {
        try {
            ClassWorld world = new ClassWorld();
            ClassRealm realm = world.newRealm(getClass().getName(),null);
            for (Artifact artifact : dependencyArtifacts) {
                realm.addConstituent(artifact.getFile().toURI().toURL());
            }
            return realm;
        } catch (DuplicateRealmException | MalformedURLException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
}
