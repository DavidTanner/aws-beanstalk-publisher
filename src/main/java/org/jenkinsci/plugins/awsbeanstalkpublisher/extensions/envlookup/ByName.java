package org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.envlookup;

import hudson.Extension;
import hudson.model.AbstractBuild;

import java.util.ArrayList;
import java.util.List;

import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBS3Setup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBSetup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBSetupDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;

public class ByName extends AWSEBSetup implements EnvLookup {
    
    
    private final List<String> envNameList;
    
    @DataBoundConstructor
    public ByName(String envNameList) {
        this.envNameList = new ArrayList<String> ();
        for (String next : envNameList.split("\n")) {
            this.envNameList.add(next);
        }
    }
    
    public List<String> getEnvNameList() {
        return envNameList == null ? new ArrayList<String>() : envNameList;
    }
    
    @Override
    public List<EnvironmentDescription> getEnvironments(AbstractBuild<?, ?> build, AWSElasticBeanstalk awseb, String applicationName) {
        DescribeEnvironmentsRequest request = new DescribeEnvironmentsRequest();
        request.setApplicationName(applicationName);
        request.setIncludeDeleted(false);
        
        request.setEnvironmentNames(envNameList);

        return awseb.describeEnvironments(request).getEnvironments();
    }
    

    @Extension
    public static class DescriptorImpl extends AWSEBSetupDescriptor {
        @Override
        public String getDisplayName() {
            return "Get Environments By Name";
        }
    }

}
