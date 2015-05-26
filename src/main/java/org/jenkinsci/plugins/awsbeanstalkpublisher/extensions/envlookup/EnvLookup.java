package org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.envlookup;

import java.util.List;

import hudson.model.AbstractBuild;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;

public interface EnvLookup {
    
    public List<EnvironmentDescription> getEnvironments(AbstractBuild<?, ?> build, AWSElasticBeanstalk awseb, String applicationName);

}
