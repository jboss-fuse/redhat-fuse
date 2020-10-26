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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;

class ZipRepositoryLayout implements RepositoryLayout {

    private final RemoteRepository repository;

    public ZipRepositoryLayout(RemoteRepository repository) {
        this.repository = repository;
    }

    @Override
    public URI getLocation(Artifact artifact, boolean upload) {
        // similar to
        // org.eclipse.aether.internal.impl.Maven2RepositoryLayoutFactory.Maven2RepositoryLayout.getLocation()
        StringBuilder path = new StringBuilder(128);
//        path.append("system/");
        path.append(artifact.getGroupId().replace('.', '/')).append('/');
        path.append(artifact.getArtifactId()).append('/');
        path.append(artifact.getBaseVersion()).append('/');
        path.append(artifact.getArtifactId()).append('-').append(artifact.getVersion());
        if (artifact.getClassifier().length() > 0) {
            path.append('-').append(artifact.getClassifier());
        }
        if (artifact.getExtension().length() > 0) {
            path.append('.').append(artifact.getExtension());
        }

        try {
            return new URI(null, null, path.toString(), null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public URI getLocation(Metadata metadata, boolean b) {
        StringBuilder path = new StringBuilder(128);
//        path.append("system/");
        path.append(metadata.getGroupId().replace('.', '/')).append('/');
        path.append(metadata.getArtifactId()).append('/');
        path.append("maven-metadata.xml");

        try {
            return new URI(null, null, path.toString(), null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<Checksum> getChecksums(Artifact artifact, boolean b, URI uri) {
        return Collections.singletonList(Checksum.forLocation(uri, "MD5"));
    }

    @Override
    public List<Checksum> getChecksums(Metadata metadata, boolean b, URI uri) {
        return Collections.singletonList(Checksum.forLocation(uri, "MD5"));
    }

}
