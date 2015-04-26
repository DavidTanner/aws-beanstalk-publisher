package org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.envlookup;

import hudson.Extension;
import hudson.model.AbstractBuild;

import java.util.ArrayList;
import java.util.List;

import org.jenkinsci.plugins.awsbeanstalkpublisher.AWSEBUtils;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBSetup;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.AWSEBSetupDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsResult;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;

public class ByUrl extends AWSEBSetup implements EnvLookup {

    private final List<String> urlList;
    
    @DataBoundConstructor
    public ByUrl(String urlList) {
        this.urlList = new ArrayList<String> ();
        for (String next : urlList.split("\n")) {
            this.urlList.add(next);
        }
    }
    
    public List<String> getUrlList() {
        return urlList == null ? new ArrayList<String>() : urlList;
    }
    
    
    @Override
    public List<EnvironmentDescription> getEnvironments(AbstractBuild<?, ?> build, AWSElasticBeanstalk awseb, String applicationName) {
        DescribeEnvironmentsRequest request = new DescribeEnvironmentsRequest();
        request.setApplicationName(applicationName);
        request.setIncludeDeleted(false);
        
        DescribeEnvironmentsResult result = awseb.describeEnvironments(request);
        
        List<EnvironmentDescription> environments = new ArrayList<EnvironmentDescription>();
        
        List<String> resolvedUrls = new ArrayList<String>(urlList.size());
        
        for(String url : urlList) {
            resolvedUrls.add(AWSEBUtils.replaceMacros(build, url));
        }
        
        for(EnvironmentDescription environment : result.getEnvironments()){
            String envUrl = environment.getEndpointURL();
            if (urlList.contains(envUrl)) {
                environments.add(environment);
            }
        }
        
        return environments;
    }
    

    @Extension
    public static class DescriptorImpl extends AWSEBSetupDescriptor {
        @Override
        public String getDisplayName() {
            return "Get Environments by Url";
        }

    }
    
}
