/**
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.jboss.redhat.fuse.mojo.develop;

import org.jboss.redhat.fuse.mojo.build.ApplyMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "deploy", requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.VALIDATE)
@Execute(phase = LifecyclePhase.INSTALL)
public class DeployMojo extends ApplyMojo {
    @Override
    public void executeInternal() throws MojoExecutionException {
    }
}
