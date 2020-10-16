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
package org.jboss.fuse.mvnplugins.patch;

import java.io.File;
import java.io.FileInputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.slf4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Component(role = AbstractMavenLifecycleParticipant.class)
public class SecureDependencyManagement extends AbstractMavenLifecycleParticipant {

    @Requirement
    private Logger logger;

    @Requirement
    private PlexusContainer container;

    @Requirement
    RepositorySystem repo;

    public SecureDependencyManagement() {
    }

    @Override
    public void afterSessionStart(MavenSession session) throws MavenExecutionException {
        super.afterSessionStart(session);
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        long ts = System.currentTimeMillis();
        if (session == null) {
            return;
        }

        Boolean skip = null;
        for (MavenProject p : session.getProjects()) {
            for (Plugin bp : p.getBuildPlugins()) {
                if ("org.jboss.redhat-fuse:patch-maven-plugin".equals(bp.getKey())) {
                    if (bp.getConfiguration() instanceof Xpp3Dom) {
                        XmlPlexusConfiguration config = new XmlPlexusConfiguration((Xpp3Dom) bp.getConfiguration());
                        if (config.getChild("skip") != null) {
                            skip = "true".equals(config.getChild("skip").getValue());
                        }
                    }
                    break;
                }
            }
        }

        if (session.getUserProperties().containsKey("skipPatch")) {
            if (Boolean.parseBoolean((String) session.getUserProperties().get("skipPatch"))) {
                skip = true;
            }
        }

        if (skip == null) {
            skip = false;
        }

        logger.info("\n\n========== Idea about CVE fix delivery ==========\n");

        logger.info(" - Skip: {}", skip);

//        Artifact artifact = new DefaultArtifact("org.jboss.fuse", "cve-metadata", "xml", "7.8.0-SNAPSHOT");
//        File metadataResolved = null;
//        try {
//            logger.info("Fetching CVE Metadata...");
//            final List<RemoteRepository> repos = new LinkedList<>();
//            session.getCurrentProject().getRepositories().forEach(r -> {
//                repos.add(new RemoteRepository.Builder(r.getId(), "default", r.getUrl()).build());
//            });
//            ArtifactResult result = repo.resolveArtifact(session.getRepositorySession(), new ArtifactRequest(artifact, repos, null));
//            logger.debug("Resolved (File): {}", result.getArtifact().getFile());
//            logger.info(" - found CVE Metadata: " + result);
//            metadataResolved = result.getArtifact().getFile();
//        } catch (ArtifactResolutionException e) {
//            logger.info("Can't fetch CVE Metadata: " + e.getMessage());
//        }
//
//        final List<String[]> cves = new LinkedList<>();
//
//        try {
//            if (metadataResolved != null) {
//                logger.info("Processing CVE Metadata...");
//                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new FileInputStream(metadataResolved));
//                NodeList nl = doc.getDocumentElement().getChildNodes();
//                for (int i = 0; i < nl.getLength(); i++) {
//                    Node n = nl.item(i);
//                    if (n instanceof Element) {
//                        Element el = (Element) n;
//                        if (el.getTagName().equals("cve")) {
//                            // <cve id="CVE-2020-123456789" groupId="org.eclipse.jetty*" affected="9.4.25.v20191220" fix="9.4.27.v20200227" />
//                            String id = el.getAttribute("id");
//                            String groupId = el.getAttribute("groupId");
//                            String affected = el.getAttribute("affected");
//                            String fix = el.getAttribute("fix");
//                            logger.info(" - found " + id + " affecting " + groupId + " with version " + affected + " fixed by " + fix);
//                            cves.add(new String[] { id, groupId, affected, fix });
//                        }
//                    }
//                }
//            }
//        } catch (Exception e) {
//            throw new MavenExecutionException(e.getMessage(), e);
//        }
//
//        if (!cves.isEmpty()) {
//            logger.info("Processing managed dependencies...");
//
//            for (MavenProject project : session.getProjects()) {
//                logger.info("Finding CVEs in project: {}", project);
//                for (Dependency dependency : project.getDependencyManagement().getDependencies()) {
//                    String groupId = dependency.getGroupId();
//                    for (String[] cve : cves) {
//                        Pattern p = Pattern.compile(cve[1].replaceAll("\\.", "\\.").replaceAll("\\*", ".*"));
//                        Matcher m = p.matcher(groupId);
//                        if (m.matches() && dependency.getVersion().equals(cve[2])) {
//                            logger.info(" - Found affected managed dependency: " + dependency);
//                            project.getManagedVersionMap().get(dependency.getManagementKey()).setResolvedVersion(cve[3]);
//                            project.getManagedVersionMap().get(dependency.getManagementKey()).setVersion(cve[3]);
//                            dependency.setVersion(cve[3]);
//                        }
//                    }
//                }
//                for (Dependency dependency : project.getDependencies()) {
//                    for (String[] cve : cves) {
//                        Pattern p = Pattern.compile(cve[1].replaceAll("\\.", "\\.").replaceAll("\\*", ".*"));
//                        Matcher m = p.matcher(dependency.getGroupId());
//                        if (m.matches() && dependency.getVersion().equals(cve[2])) {
//                            dependency.setVersion(cve[3]);
//                        }
//                    }
//                }
//            }
//        }
        logger.info("Done in " + (System.currentTimeMillis() - ts) + "ms\n\n=================================================\n");
    }

}
