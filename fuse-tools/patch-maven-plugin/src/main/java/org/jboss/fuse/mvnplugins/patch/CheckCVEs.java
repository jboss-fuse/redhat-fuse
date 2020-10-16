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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.slf4j.Logger;

@Mojo(name = "check-cves", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true, inheritByDefault = false, aggregator = true)
public class CheckCVEs extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Component
    private Logger logger;

    @Parameter(defaultValue = "${skipPatch}")
    private boolean skip = false;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        logger.info("check-cves");
        logger.info(project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion());
        for (Dependency dependency : project.getDependencyManagement().getDependencies()) {
            if (dependency.getGroupId().equals("org.eclipse.jetty")) {
                dependency.setVersion("9.4.27.v20200227");
            }
        }
        for (Dependency dependency : project.getDependencies()) {
            logger.info(" - " + dependency);
        }
    }

}
