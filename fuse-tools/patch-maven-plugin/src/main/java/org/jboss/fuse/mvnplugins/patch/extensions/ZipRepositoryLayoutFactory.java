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
package org.jboss.fuse.mvnplugins.patch.extensions;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory;
import org.eclipse.aether.spi.locator.Service;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionRange;

@Component(role = RepositoryLayoutFactory.class)
public class ZipRepositoryLayoutFactory implements RepositoryLayoutFactory, Service {

    @Override
    public void initService(ServiceLocator locator) {
        locator.getService(RepositoryLayout.class);
    }

    @Override
    public RepositoryLayout newInstance(RepositorySystemSession repositorySystemSession, final RemoteRepository repository) throws NoRepositoryLayoutException {
        if (!"zip".equals(repository.getContentType())) {
            throw new NoRepositoryLayoutException(repository);
        }
        InputStream stream = RepositoryLayout.class.getClassLoader().getResourceAsStream("META-INF/maven/org.apache.maven.resolver/maven-resolver-api/pom.properties");
        Properties props = new Properties();
        if (stream != null) {
            try {
                props.load(stream);
            } catch (IOException ignored) {
            }
        }
        String version = props.getProperty("version");
        GenericVersionScheme gvs = new GenericVersionScheme();
        boolean oldResolver = true;
        if (version != null) {
            try {
                // In Maven 3.8.8, maven-resolver is at version 1.6.3
                VersionRange range = gvs.parseVersionRange("[1, 1.9)");
                Version v = gvs.parseVersion(version);
                oldResolver = range.containsVersion(v);
            } catch (Exception ignored) {
            }
        }

        if (oldResolver) {
            // fallback to Maven before 3.9
            return new ZipRepositoryLayout38(repository);
        } else {
            return new ZipRepositoryLayout39(repository);
        }
    }

    @Override
    public float getPriority() {
        return 0;
    }

}
