/*
 * Copyright 2005-2020 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.fuse.mvnplugins.patch;

import java.io.File;

import com.google.inject.AbstractModule;
import org.apache.maven.Maven;
import org.apache.maven.internal.aether.DefaultRepositorySystemSessionFactory;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.repository.legacy.LegacyRepositorySystem;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.jboss.fuse.mvnplugins.patch.extensions.ZipRepositoryLayoutFactory;
import org.junit.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

public class MavenTest {

    @Test
    public void mavenComponentsUsingPlexus() throws Exception {
        // org.apache.maven.cli.MavenCli.doMain(org.apache.maven.cli.CliRequest)

        ClassWorld world = new ClassWorld("plexus.core", Thread.currentThread().getContextClassLoader());
        ClassRealm realm = world.getRealm("plexus.core");

        realm.setParentClassLoader(MavenTest.class.getClassLoader());

        ContainerConfiguration cc = new DefaultContainerConfiguration()
                .setClassWorld(world)
                .setRealm(realm)
                .setClassPathScanning(PlexusConstants.SCANNING_INDEX)
                .setAutoWiring(true)
                .setJSR250Lifecycle(true)
                .setName("maven");

        PlexusContainer container = new DefaultPlexusContainer(cc, new AbstractModule() {
            @Override
            protected void configure() {
                bind(ILoggerFactory.class).toInstance(LoggerFactory.getILoggerFactory());
            }
        });

        Maven maven = container.lookup(Maven.class);
        System.out.println(maven);

        DefaultRepositorySystemSessionFactory sf = container.lookup(DefaultRepositorySystemSessionFactory.class);
        System.out.println(sf);

        // (file:/data/ggrzybek/.m2/repository/org/apache/maven/maven-compat/3.6.3/maven-compat-3.6.3.jar <no signer certificates>)
        System.out.println(container.lookup(org.apache.maven.repository.RepositorySystem.class));
        // (file:/data/ggrzybek/.m2/repository/org/apache/maven/resolver/maven-resolver-impl/1.4.1/maven-resolver-impl-1.4.1.jar <no signer certificates>)
        System.out.println(container.lookup(org.eclipse.aether.RepositorySystem.class));
    }

    @Test
    public void mavenComponentsUsingLocator() throws Exception {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.setService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.setService(org.apache.maven.repository.RepositorySystem.class, LegacyRepositorySystem.class);
//        locator.setService(org.eclipse.aether.RepositorySystem.class, DefaultRepositorySystem.class);

        org.apache.maven.repository.RepositorySystem mavenSystem
                = locator.getService(org.apache.maven.repository.RepositorySystem.class);
        System.out.println(mavenSystem);
        // this can't be used this way, because it's annotation driven and dependencies are not injected
        // using org.eclipse.aether.spi.locator.Service.initService()
//        mavenSystem.resolve(new ArtifactResolutionRequest());

        org.eclipse.aether.RepositorySystem resolverSystem
                = locator.getService(org.eclipse.aether.RepositorySystem.class);
        System.out.println(resolverSystem);

        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepository = new LocalRepository(new File(System.getProperty("user.home"), ".m2/repository"));
        session.setLocalRepositoryManager(resolverSystem.newLocalRepositoryManager(session, localRepository));

        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(new DefaultArtifact("commons-io:commons-io:jar:2.8.0"));
        ArtifactResult result = resolverSystem.resolveArtifact(session, request);
        System.out.println(result.getArtifact().getFile());
    }

    @Test
    public void zipRemoteRepositories() throws Exception {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.setService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);

//        locator.addService(RepositoryLayoutProvider.class, ZipRepositoryLayoutProvider.class);
        locator.addService(RepositoryLayoutFactory.class, ZipRepositoryLayoutFactory.class);

        locator.addService(TransporterFactory.class, FileTransporterFactory.class);

        org.eclipse.aether.RepositorySystem resolverSystem
                = locator.getService(org.eclipse.aether.RepositorySystem.class);

        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepository = new LocalRepository(new File("target/repository"));
        session.setLocalRepositoryManager(resolverSystem.newLocalRepositoryManager(session, localRepository));

        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(new DefaultArtifact("commons-io:commons-io:jar:2.8.0"));

        RemoteRepository.Builder b1
                = new RemoteRepository.Builder("patch", "zip", new File("src/test/resources/patch-1.zip").toURI().toString());
        request.addRepository(b1.build());
        ArtifactResult result = resolverSystem.resolveArtifact(session, request);
        System.out.println(result.getArtifact().getFile());
    }

}
