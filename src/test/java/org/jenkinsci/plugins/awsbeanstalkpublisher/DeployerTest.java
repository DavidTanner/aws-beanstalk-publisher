package org.jenkinsci.plugins.awsbeanstalkpublisher;


import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DeployerTest {
	
	@Mock
	private AWSEBDeploymentProvider builder;
	
	@Mock
	private FreeStyleBuild build;
	
	@Mock 
	private Launcher launcher;
	
	@Mock
	private BuildListener listener;

	@Test
	public void test() throws Exception {
//		Deployer dep = new Deployer(builder, build, launcher, listener);
		
	}
}
