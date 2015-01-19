package org.jenkinsci.plugins.awsbeanstalkpublisher.extensions;

import hudson.Launcher;
import hudson.model.AbstractDescribableImpl;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;

public abstract class AWSEBSetup  extends AbstractDescribableImpl<AWSEBSetup>{

    public abstract void perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws Exception;

}
