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
package org.jboss.redhat.fuse;

import org.jboss.redhat.fuse.mojo.AbstractFabric8Mojo;
import org.jboss.redhat.fuse.mojo.build.ApplyMojo;
import org.jboss.redhat.fuse.mojo.build.BuildMojo;
import org.jboss.redhat.fuse.mojo.build.HelmMojo;
import org.jboss.redhat.fuse.mojo.build.PushMojo;
import org.jboss.redhat.fuse.mojo.build.ResourceMojo;
import org.jboss.redhat.fuse.mojo.develop.DebugMojo;
import org.jboss.redhat.fuse.mojo.develop.DeployMojo;
import org.jboss.redhat.fuse.mojo.develop.LogMojo;
import org.jboss.redhat.fuse.mojo.develop.RunMojo;
import org.jboss.redhat.fuse.mojo.develop.StartMojo;
import org.jboss.redhat.fuse.mojo.develop.StopMojo;
import org.jboss.redhat.fuse.mojo.develop.UndeployMojo;
import org.jboss.redhat.fuse.mojo.develop.WatchMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.MojoRule;

import org.junit.Rule;
import static org.junit.Assert.*;
import org.junit.Test;
import java.io.File;

public class MojoTest
{
    @Rule
    public MojoRule rule = new MojoRule()
    {
        @Override
        protected void before() throws Throwable 
        {
        }

        @Override
        protected void after()
        {
        }
    };

    public void fabric8Test(AbstractFabric8Mojo am) throws MojoExecutionException, MojoFailureException {
        try {
            assertNotNull(am);
            am.execute();
            assertTrue("Should not get here", false);
        } catch (MojoExecutionException me) {
            assertTrue(me.getMessage().contains("fabric8-maven-plugin has been removed, see details above"));
        } catch (MojoFailureException mf) {
            assertTrue(mf.getMessage().contains("fabric8-maven-plugin has been removed, see details above"));
        }
    }

    /**
     * @throws Exception if any
     */
    @Test
    public void testMojos() throws Exception
    {
        File pom = new File( "target/test-classes/project-to-test/" );
        assertNotNull( pom );
        assertTrue( pom.exists() );

        ApplyMojo applyMojo = (ApplyMojo) rule.lookupConfiguredMojo( pom, "apply" );
        fabric8Test(applyMojo);

        BuildMojo buildMojo = (BuildMojo) rule.lookupConfiguredMojo( pom, "build" );
        fabric8Test(buildMojo);

        DebugMojo debugMojo = (DebugMojo) rule.lookupConfiguredMojo( pom, "debug" );
        fabric8Test(debugMojo);


        DeployMojo deployMojo = (DeployMojo) rule.lookupConfiguredMojo( pom, "deploy" );
        fabric8Test(deployMojo);

        LogMojo logMojo = (LogMojo) rule.lookupConfiguredMojo( pom, "log" );
        fabric8Test(logMojo);

        RunMojo runMojo = (RunMojo) rule.lookupConfiguredMojo( pom, "run" );
        fabric8Test(runMojo);

        StartMojo startMojo = (StartMojo) rule.lookupConfiguredMojo( pom, "start" );
        fabric8Test(startMojo);

        StopMojo stopMojo = (StopMojo) rule.lookupConfiguredMojo( pom, "stop" );
        fabric8Test(stopMojo);

        UndeployMojo undeployMojo = (UndeployMojo) rule.lookupConfiguredMojo( pom, "undeploy" );
        fabric8Test(undeployMojo);

        WatchMojo watchMojo = (WatchMojo) rule.lookupConfiguredMojo( pom, "watch" );
        fabric8Test(watchMojo);

        HelmMojo helmMojo = (HelmMojo) rule.lookupConfiguredMojo( pom, "helm" );
        fabric8Test(helmMojo);

        PushMojo pushMojo = (PushMojo) rule.lookupConfiguredMojo( pom, "push" );
        fabric8Test(pushMojo);

        ResourceMojo resourceMojo = (ResourceMojo) rule.lookupConfiguredMojo( pom, "resource" );
        fabric8Test(resourceMojo);
    }

}

