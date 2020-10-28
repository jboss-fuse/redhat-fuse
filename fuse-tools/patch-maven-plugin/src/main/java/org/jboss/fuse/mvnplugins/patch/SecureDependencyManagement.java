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
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.interpolation.StringVisitorModelInterpolator;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.jboss.fuse.mvnplugins.patch.extensions.ZipWagon;
import org.jboss.fuse.mvnplugins.patch.model.AffectedArtifactSpec;
import org.jboss.fuse.mvnplugins.patch.model.CVE;
import org.jboss.fuse.mvnplugins.patch.model.FuseVersion;
import org.jboss.fuse.mvnplugins.patch.model.PatchMetadata;
import org.slf4j.Logger;

import static org.jboss.fuse.mvnplugins.patch.model.AffectedArtifactSpec.GVS;

@Component(role = AbstractMavenLifecycleParticipant.class)
public class SecureDependencyManagement extends AbstractMavenLifecycleParticipant {

    @Requirement
    private Logger logger;

    @Requirement
    private PlexusContainer container;

    @Requirement
    RepositorySystem repo;

    // coordinates of the plugin itself
    private String pluginGroupId;
    private String pluginArtifactId;

    // coordinates of the patch metadata for Fuse Spring Boot flavor
    private String mdSb2GroupId;
    private String mdSb2ArtifactId;
    private String mdSb2Type;

    // coordinates of the patch metadata for Fuse Karaf flavor
    private String mdKarafGroupId;
    private String mdKarafArtifactId;
    private String mdKarafType;

    // coordinates of the official Fuse Spring Boot BOM
    private String bomSb2GroupId;
    private String bomSb2ArtifactId;

    // coordinates of the official Fuse Karaf BOM
    private String bomKarafGroupId;
    private String bomKarafArtifactId;

    private File tmpDir = null;

    @Override
    public void afterSessionStart(MavenSession session) throws MavenExecutionException {
        super.afterSessionStart(session);
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        if (session == null) {
            return;
        }
        long ts = System.currentTimeMillis();
        configureProperties(session);

        if (shouldSkip(session)) {
            return;
        }

        logger.info("\n\n========== Red Hat Fuse Maven patching ==========\n");

        try {
            // detect which BOM we're using - we can't have both Karaf and Spring Boot bom for example
            // we have to review "original model", because at this stage, the effective <dependencyManagement>
            // has already its bom-imports "resolved"
            // (by org.apache.maven.model.building.DefaultModelBuilder.importDependencyManagement())
            Dependency bom = findProductBOM(session);
            if (bom == null) {
                return;
            }

            List<RemoteRepository> repositories = configureRepositories(session);

            // because we have 2 supported products (identified by artifactId of BOMs, where groupId=org.jboss.redhat-fuse):
            //  - fuse-karaf-bom 7.x.y.fuse-7xyNNN[-redhat-nnnnn] - for Fuse Karaf up to 7.7
            //  - fuse-karaf-bom 7.x.y.fuse-sb2-7xyNNN[-redhat-nnnnn] - for Fuse Karaf starting from 7.8
            //  - fuse-springboot-bom 7.x.y.fuse-7xyNNN[-redhat-nnnnn] - for Fuse Spring Boot 1 up to 7.7
            //  - fuse-springboot-bom 7.x.y.fuse-sb2-7xyNNN[-redhat-nnnnn] - for Fuse Spring Boot 2 starting from 7.4
            // we have to fetch/check the metadata ourselves and not simply have it checked when resolving RELEASE
            // artifact

            List<MetadataRequest> metadataRequests = configurePatchMetadataRequest(session, bom, repositories);
            List<MetadataResult> metadataResults = repo.resolveMetadata(session.getRepositorySession(), metadataRequests);

            // The metadata result may come from different sources (latest /metadata/versioning/lastUpdated wins), but
            // also may contain metadata versions for different Fuse versions (whatever the flavor), like:
            // <?xml version="1.0" encoding="UTF-8"?>
            // <metadata>
            //   <groupId>org.jboss.redhat-fuse</groupId>
            //   <artifactId>fuse-springboot-patch-metadata</artifactId>
            //   <versioning>
            //     <release>7.8.0.fuse-sb2-780025</release>
            //     <versions>
            //       <version>7.8.0.fuse-sb2-780025</version>
            //       <version>7.7.0.fuse-sb2-770010</version>
            //       <version>7.7.0.fuse-770010</version>
            //     </versions>
            //     <lastUpdated>20201023104706</lastUpdated>
            //   </versioning>
            // </metadata>
            // thus we have to find proper version of patch metadata depending on which Fuse version we're running in
            // (in terms of used product BOM)
            String version = findLatestMetadataVersion(bom, metadataResults);
            if (version == null) {
                logger.warn("[PATCH] Can't find latest patch metadata for {} in any of configured repositories.",
                        String.format("%s/%s/%s BOM", bom.getGroupId(), bom.getArtifactId(), bom.getVersion()));
                if (!logger.isDebugEnabled()) {
                    logger.warn("[PATCH] Please enable debug logging (-X) to see more details."
                            + " Perhaps the metadata was previously downloaded from different repository?");
                }
                return;
            }

            // we'll be looking for metadata specific to given flavor of Fuse
            ArtifactRequest request = configurePatchArtifactRequest(session, bom, version);
            request.setRepositories(repositories);

            ArtifactResult result;
            try {
                result = repo.resolveArtifact(session.getRepositorySession(), request);
                logger.info("[PATCH] Resolved patch descriptor: {}", result.getArtifact().getFile());
            } catch (ArtifactResolutionException e) {
                logger.warn("[PATCH] Unable to find patch metadata in any of the configured repositories");
                return;
            }

            PatchMetadata patch = readPatchMetadata(result.getArtifact().getFile());
            FuseVersion bomVersion = new FuseVersion(bom.getVersion());
            // validate if the metadata is for our project - just sanity check
            if (!bomVersion.canUse(patch.getProductVersionRange())) {
                logger.warn("[PATCH] Patch metadata is applicable to Fuse version {} and can't be used with {}.",
                        patch.getProductVersionRange(),
                        String.format("%s/%s/%s BOM", bom.getGroupId(), bom.getArtifactId(), bom.getVersion()));
                return;
            }

            logger.info("[PATCH] Patch metadata found for {}/{}/{}",
                    patch.getProductGroupId(), patch.getProductArtifactId(), patch.getProductVersionRange());
            int cveCount = patch.getCves().size();
            int fixCount = patch.getFixes().size();
            if (cveCount > 0) {
                logger.info("[PATCH]  - patch contains {} CVE {}", cveCount, cveCount > 1 ? "fixes" : "fix");
            }
            if (fixCount > 0) {
                logger.info("[PATCH]  - patch contains {} other {}", fixCount, fixCount > 1 ? "fixes" : "fix");
            }

            if (cveCount > 0) {
                logger.info("[PATCH] Processing managed dependencies to apply CVE fixes...");

                for (CVE cve : patch.getCves()) {
                    logger.info("[PATCH] - {}", cve);
                    for (AffectedArtifactSpec spec : cve.getAffected()) {
                        logger.info("[PATCH]   Applying change {}", spec);
                        for (MavenProject project : session.getProjects()) {
                            if (project.getDependencyManagement() != null) {
                                for (Dependency dependency : project.getDependencyManagement().getDependencies()) {
                                    if (spec.matches(dependency)) {
                                        logger.info("[PATCH]    - managed dependency: {}/{}/{} -> {}",
                                                dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(),
                                                spec.getFixVersion());
                                        project.getManagedVersionMap().get(dependency.getManagementKey()).setResolvedVersion(spec.getFixVersion().toString());
                                        project.getManagedVersionMap().get(dependency.getManagementKey()).setVersion(spec.getFixVersion().toString());
                                        dependency.setVersion(spec.getFixVersion().toString());
                                    }
                                }
                            }
                            for (Dependency dependency : project.getDependencies()) {
                                if (spec.matches(dependency)) {
                                    logger.info("[PATCH]    - dependency: {}/{}/{} -> {}",
                                            dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(),
                                            spec.getFixVersion());
                                    dependency.setVersion(spec.getFixVersion().toString());
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            if (tmpDir != null) {
                cleanupRepository(tmpDir);
            }
            logger.info("[PATCH] Done in " + (System.currentTimeMillis() - ts) + "ms\n\n=================================================\n");
        }
    }

    private void configureProperties(MavenSession session) throws MavenExecutionException {
        Properties props = new Properties();
        try (InputStream is = getClass().getResourceAsStream("/plugin.properties")) {
            props.load(is);
        } catch (IOException e) {
            throw new MavenExecutionException("Can't load plugin.properties",
                    session.getCurrentProject().getFile());
        }

        pluginGroupId = props.getProperty("plugin.groupId");
        pluginArtifactId = props.getProperty("plugin.artifactId");

        mdSb2GroupId = props.getProperty("patch-metadata.sb2.groupId");
        mdSb2ArtifactId = props.getProperty("patch-metadata.sb2.artifactId");
        mdSb2Type = props.getProperty("patch-metadata.sb2.type");

        mdKarafGroupId = props.getProperty("patch-metadata.karaf.groupId");
        mdKarafArtifactId = props.getProperty("patch-metadata.karaf.artifactId");
        mdKarafType = props.getProperty("patch-metadata.karaf.type");

        bomSb2GroupId = props.getProperty("bom.sb2.groupId");
        bomSb2ArtifactId = props.getProperty("bom.sb2.artifactId");

        bomKarafGroupId = props.getProperty("bom.karaf.groupId");
        bomKarafArtifactId = props.getProperty("bom.karaf.artifactId");
    }

    private boolean shouldSkip(MavenSession session) {
        // <configuration>/<skip>
        Boolean skip = null;

        for (MavenProject p : session.getProjects()) {
            for (Plugin bp : p.getBuildPlugins()) {
                if ((pluginGroupId + ":" + pluginArtifactId).equals(bp.getKey())) {
                    if (bp.getConfiguration() instanceof Xpp3Dom) {
                        XmlPlexusConfiguration config = new XmlPlexusConfiguration((Xpp3Dom) bp.getConfiguration());
                        if (config.getChild("skip") != null) {
                            skip = "true".equalsIgnoreCase(config.getChild("skip").getValue());
                        }
                    }
                    break;
                }
            }
        }

        if (session.getUserProperties().containsKey("skipPatch")) {
            if (Boolean.parseBoolean(session.getUserProperties().getProperty("skipPatch"))) {
                skip = true;
            }
        }

        return skip != null && skip;
    }

    /**
     * <p>This method returns list of {@link RemoteRepository repositories} to be checked for patch metadata and
     * artifacts.</p>
     *
     * <p>Patch metadata and artifacts will be resolved using normal Maven mechanisms. We however provide special
     * kind of {@link RemoteRepository} that can be accessed through provided ZIP file (shipped as a
     * <em>patch</em>).</p>
     *
     * @param session
     * @return
     */
    private List<RemoteRepository> configureRepositories(MavenSession session) throws MavenExecutionException {
        List<RemoteRepository> repositories = new ArrayList<>();

        String patch = session.getUserProperties().getProperty("patch");
        if ("true".equals(patch) || (patch != null && "".equals(patch.trim()))) {
            logger.warn("[PATCH] -Dpatch used, but patch location not specified. Are you sure correct -Dpatch=location is used?");
        } else {
            if (patch != null) {
                File pf = new File(patch);
                if (!pf.isFile()) {
                    logger.warn("[PATCH] Patch repository {} is not accessible. Project repositories will be used", patch);
                } else {
                    String canonicalPath = null;
                    try {
                        canonicalPath = pf.getCanonicalPath();
                    } catch (IOException ignored) {
                    }
                    logger.info("[PATCH] Reading metadata and artifacts from {}", canonicalPath);

                    // instead of "opening" and "closing" ZIP patch repository in ZipWagon,
                    // we'll unpack it now
                    tmpDir = ZipWagon.unpackPatchRepository(pf);

                    // ID of the repository is tricky question. If we use pf.getName() as ID (which looks nice), we
                    // may have problems later, because that's what Aether does with its _remote.repositories file!
                    // for example if the patch file was patch-1.zip and we used it as repo id, we'd get
                    // _remote.repositories file with:
                    //     fuse-springboot-patch-metadata-7.8.0.fuse-sb2-781023.xml>patch-3.zip=
                    // and next resolution of org.jboss.redhat-fuse:fuse-springboot-patch-metadata:RELEASE with
                    // different patch of from remote repos would ONLY because the ID of the repo wouldn't match...
                    RemoteRepository.Builder zipRepository
                            = new RemoteRepository.Builder("fuse-patch", "zip", "zip:" + tmpDir.toURI().toString());
                    repositories.add(zipRepository.build());
                }
            }
        }

        if (repositories.size() == 0) {
            for (org.apache.maven.model.Repository repo : session.getCurrentProject().getRepositories()) {
                String id = repo.getId() == null ? UUID.randomUUID().toString() : repo.getId();
                RemoteRepository.Builder builder = new RemoteRepository.Builder(id, repo.getLayout(), repo.getUrl());
                repositories.add(builder.build());
            }
            logger.info("[PATCH] Reading patch metadata and artifacts from {} project {}", repositories.size(),
                    repositories.size() > 1 ? "repositories" : "repository");
            for (RemoteRepository r : repositories) {
                logger.info("[PATCH]  - {}: {}", r.getId(), r.getUrl());
            }
        }

        return repositories;
    }

    /**
     * Iterate over all the projects in the session, check their {@code <dependencyManagement>} and
     * checks a list of all {@link Dependency} for them. These will have all the placeholders
     * resolved (because for example the version may have been parameterized) - this is required,
     * because {@link MavenProject#getOriginalModel()} was checked. Then, among all the dependencies we find the
     * <em>product BOM</em> to identify which product we're using.
     *
     * @param session
     * @return
     */
    private Dependency findProductBOM(MavenSession session) {
        List<Dependency> result = new LinkedList<>();

        for (MavenProject mp : session.getProjects()) {
            MavenProject _mp = mp;
            while (_mp != null) {
                DependencyManagement dm = _mp.getOriginalModel().getDependencyManagement();
                if (dm != null) {
                    List<Dependency> projectDependencies = new LinkedList<>();
                    for (Dependency d : dm.getDependencies()) {
                        if ("import".equals(d.getScope()) && "pom".equals(d.getType())) {
                            projectDependencies.add(d);
                        }
                    }
                    result.addAll(interpolate(session, _mp, projectDependencies));
                }
                _mp = _mp.getParent();
            }
        }

        Dependency springBootBom = null;
        Dependency karafBom = null;

        for (Dependency d : result) {
            if (bomSb2GroupId.equals(d.getGroupId()) && bomSb2ArtifactId.equals(d.getArtifactId())) {
                springBootBom = d;
            }
            if (bomKarafGroupId.equals(d.getGroupId()) && bomKarafArtifactId.equals(d.getArtifactId())) {
                karafBom = d;
            }
        }

        if (!(karafBom != null || springBootBom != null)) {
            logger.info("[PATCH] No project in the reactor uses Fuse Karaf or Fuse Spring Boot BOM. Skipping patch processing.");
            return null;
        }

        if (karafBom != null && springBootBom != null) {
            logger.warn("[PATCH] Reactor uses both Fuse Karaf and Fuse Spring Boot BOMs. Please use only one. Skipping patch processing.");
            return null;
        }

        return karafBom == null ? springBootBom : karafBom;
    }

    /**
     * Use Maven machinery to interpolate possible properties in ad-hoc model with BOM-dependencies.
     * @param session
     * @param mp
     * @param projectDependencies
     * @return
     */
    private List<Dependency> interpolate(MavenSession session, MavenProject mp, List<Dependency> projectDependencies) {
        // when operating on org.apache.maven.project.MavenProject.getOriginalModel(), we won't
        // get our model interpolated, so we have to do it ourselves
        Model m = new Model();
        DependencyManagement dm = new DependencyManagement();
        m.setDependencyManagement(dm);
        dm.getDependencies().addAll(projectDependencies);

        // properties from project hierarchy, starting from top
        Properties props = new Properties();
        Deque<MavenProject> projects = new LinkedList<>();
        MavenProject _mp = mp;
        while (_mp != null) {
            projects.push(_mp);
            _mp = mp.getParent();
        }
        while (projects.size() > 0) {
            Properties _props = projects.pop().getProperties();
            if (_props != null) {
                props.putAll(_props);
            }
        }
        m.setProperties(props);

        StringVisitorModelInterpolator interpolator = new StringVisitorModelInterpolator();
        ModelBuildingRequest req = new DefaultModelBuildingRequest();
        req.getSystemProperties().putAll(session.getSystemProperties());
        req.getUserProperties().putAll(session.getUserProperties());
        interpolator.interpolateModel(m, null, req, null);

        return m.getDependencyManagement().getDependencies();
    }

    /**
     * Prepares {@link MetadataRequest}s to get maven-metadata.xml for proper groupId:artifactId for given product. We
     * have to consult this metadata ourselves, because single groupId:artifactId is used for all the versions of
     * supported products.
     * @param session
     * @param repositories
     * @return
     */
    private List<MetadataRequest> configurePatchMetadataRequest(MavenSession session, Dependency productBom, List<RemoteRepository> repositories) {
        List<MetadataRequest> requests = new ArrayList<>(repositories.size());
        String groupId = null;
        String artifactId = null;
        if (bomSb2ArtifactId.equals(productBom.getArtifactId())) {
            groupId = mdSb2GroupId;
            artifactId = mdSb2ArtifactId;
        } else {
            groupId = mdKarafGroupId;
            artifactId = mdKarafArtifactId;
        }

        DefaultMetadata md = new DefaultMetadata(groupId, artifactId, "maven-metadata.xml", Metadata.Nature.RELEASE);
        // local repository
        requests.add(new MetadataRequest(md, null, ""));
        // remote repositories
        for (RemoteRepository repo : repositories) {
            requests.add(new MetadataRequest(md, repo, ""));
        }

        return requests;
    }

    /**
     * Finds the latest suitable version of the patch metadata to use, which depends on Fuse flavor and version
     * @param bom
     * @param results
     * @return
     */
    private String findLatestMetadataVersion(Dependency bom, List<MetadataResult> results) {
        FuseVersion bomVersion = new FuseVersion(bom.getVersion());

        Map<String, Versioning> metadata = new TreeMap<>();
        for (MetadataResult result : results) {
            if (result != null && result.isResolved()) {
                try (FileReader reader = new FileReader(result.getMetadata().getFile())) {
                    org.apache.maven.artifact.repository.metadata.Metadata md = new MetadataXpp3Reader().read(reader);
                    Versioning v = md.getVersioning();
                    if (v != null) {
                        // we don't care about /metadata/versioning/release, because it may be for newly deployed
                        // metadata for older version of Fuse
                        metadata.put(v.getLastUpdated(), v);
                    }
                } catch (IOException | XmlPullParserException e) {
                    logger.warn("[PATCH] Problem parsing Maven Metadata {}: {}", result.getMetadata().getFile(), e.getMessage(), e);
                }
            }
        }

        Set<ComparableVersion> versions = new TreeSet<>(Comparator.reverseOrder());
        // iterate from oldest to newest metadata, where newer overwrite older versions
        for (Versioning versioning : metadata.values()) {
            for (String version : versioning.getVersions()) {
                // the problem is that "canonical" maven versions are ONLY:
                //  - major, major.minor or major.minor.build
                //  - major-qualifier, major.minor-qualifier or major.minor.build-qualifier
                // when qualifier is parsable as int, it'll become "build number", when version is unparsable,
                // everything becomes just the "qualifier", so:
                // 1-1 == 1.0.0 with build number = 1
                // 1-a == 1.0.0 with qualifier = "a"
                // 1.1.1.1 == 0.0.0 with qualifier = "1.1.1.1"
                // so for example new org.apache.maven.artifact.versioning.DefaultArtifactVersion("1.2.3.4") will
                // return:
                // DefaultArtifactVersion.getMajorVersion(): "0"
                // DefaultArtifactVersion.getQualifier(): "1.2.3.4"
                //
                // we can imagine the problems with jackson-databind 2.9.10.4-redhat-00001 or
                // fuse-springboot-bom 7.7.0.fuse-sb2-770010-redhat-00001 (actual version in MRRC/ga)
                //
                // fortunately GenericArtifactVersions uses org.apache.maven.artifact.versioning.ComparableVersion
                // when comparing, but we have to take care of checking major.minor version
                FuseVersion metadataVersion = new FuseVersion(version);
                if (bomSb2ArtifactId.equals(bom.getArtifactId())) {
                    // Fuse SB1 or SB2, so we have to check which one we use
                    if (bomVersion.getMajor() != metadataVersion.getMajor()
                            || bomVersion.getMinor() != metadataVersion.getMinor()
                            || bomVersion.isSb1() != metadataVersion.isSb1()) {
                        logger.debug("[PATCH] Skipping metadata {}", version);
                        continue;
                    }
                } else {
                    // Fuse Karaf, so just compare version range
                    if (bomVersion.getMajor() != metadataVersion.getMajor()
                            || bomVersion.getMinor() != metadataVersion.getMinor()) {
                        logger.debug("[PATCH] Skipping metadata {}", version);
                        continue;
                    }
                }
                logger.debug("[PATCH] Found metadata {}", version);
                versions.add(new ComparableVersion(version));
            }
        }

        // simply return newest version
        return versions.size() == 0 ? null : versions.iterator().next().toString();
    }

    /**
     * Checks which BOM do we use in one of reactors projects (if at all) and prepares an {@link ArtifactRequest}
     * to fetch relevant, product-dependent metadata (e.g., for Fuse Spring Boot or Fuse Karaf).
     * @param session
     * @param productBom the only valid product BOM
     * @param version
     * @return
     */
    private ArtifactRequest configurePatchArtifactRequest(MavenSession session, Dependency productBom, String version) {
        ArtifactRequest request = new ArtifactRequest();

        if (bomSb2ArtifactId.equals(productBom.getArtifactId())) {
            request.setArtifact(new DefaultArtifact(String.format("%s:%s:%s:%s", mdSb2GroupId, mdSb2ArtifactId,
                    mdSb2Type, version)));
        } else {
            request.setArtifact(new DefaultArtifact(String.format("%s:%s:%s:%s", mdKarafGroupId, mdKarafArtifactId,
                    mdKarafType, version)));
        }

        return request;
    }

    /**
     * Parses Patch metadata XML into {@link PatchMetadata}
     * @param patchMetadataFile
     * @return
     * @throws MavenExecutionException
     */
    private PatchMetadata readPatchMetadata(File patchMetadataFile) throws MavenExecutionException {
        PatchMetadata patch = new PatchMetadata();

        try {
            try (FileReader reader = new FileReader(patchMetadataFile)) {
                Xpp3Dom dom = Xpp3DomBuilder.build(reader);

                Xpp3Dom productDom = dom.getChild("product-bom");
                if (productDom == null) {
                    throw new IllegalStateException("Can't find <product-bom> element in patch metadata");
                }
                patch.setProductGroupId(productDom.getAttribute("groupId"));
                patch.setProductArtifactId(productDom.getAttribute("artifactId"));
                patch.setProductVersionRange(GVS.parseVersionRange(productDom.getAttribute("versions")));

                Xpp3Dom cvesWrapper = dom.getChild("cves");
                if (cvesWrapper != null) {
                    for (Xpp3Dom cveDom : cvesWrapper.getChildren("cve")) {
                        CVE cve = new CVE();
                        cve.setId(cveDom.getAttribute("id"));
                        cve.setDescription(cveDom.getAttribute("description"));
                        cve.setCveLink(cveDom.getAttribute("cve-link"));
                        cve.setBzLink(cveDom.getAttribute("bz-link"));
                        patch.getCves().add(cve);
                        for (Xpp3Dom affects : cveDom.getChildren("affects")) {
                            AffectedArtifactSpec spec = new AffectedArtifactSpec();
                            spec.setGroupIdSpec(affects.getAttribute("groupId"));
                            spec.setArtifactIdSpec(affects.getAttribute("artifactId"));
                            spec.setVersionRange(GVS.parseVersionRange(affects.getAttribute("versions")));
                            spec.setFixVersion(GVS.parseVersion(affects.getAttribute("fix")));
                            cve.getAffected().add(spec);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new MavenExecutionException(e.getMessage(), e);
        }

        return patch;
    }

    private void cleanupRepository(File tmpDir) {
        try {
            Files.walkFileTree(tmpDir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    file.toFile().delete();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    dir.toFile().delete();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.warn("Problem during temporary patch repository cleanup: {}", e.getMessage(), e);
        }
    }

}
