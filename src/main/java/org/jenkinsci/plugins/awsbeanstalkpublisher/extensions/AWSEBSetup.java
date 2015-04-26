package org.jenkinsci.plugins.awsbeanstalkpublisher.extensions;

import java.util.List;

import hudson.Launcher;
import hudson.model.AbstractDescribableImpl;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;

public abstract class AWSEBSetup extends AbstractDescribableImpl<AWSEBSetup> {

    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws Exception{
        return true;
    }

    public static boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, List<AWSEBSetup> extensions){
        boolean status = true;
        try {
            for (AWSEBSetup eb : extensions) {
                status &= eb.perform(build, launcher, listener);
            }
            return status;
        } catch (Exception exc) {
            throw new RuntimeException(exc);
        }
    }
}
