package org.jenkinsci.plugins.awsbeanstalkpublisher.extensions;

import java.util.List;

import hudson.Launcher;
import hudson.model.AbstractDescribableImpl;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;

public abstract class AWSEBSetup extends AbstractDescribableImpl<AWSEBSetup> {

    public abstract void perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws Exception;

    public static boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, List<AWSEBSetup> extensions){
        try {
            for (AWSEBSetup eb : extensions) {
                eb.perform(build, launcher, listener);
            }
            return true;
        } catch (Exception exc) {
            throw new RuntimeException(exc);
        }
    }
}
