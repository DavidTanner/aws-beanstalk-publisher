package org.jenkinsci.plugins.awsbeanstalkpublisher.extensions;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.util.ArrayList;
import java.util.List;

import org.jenkinsci.plugins.awsbeanstalkpublisher.AWSEBCredentials;
import org.jenkinsci.plugins.awsbeanstalkpublisher.AWSEBEnvironmentUpdater;
import org.jenkinsci.plugins.awsbeanstalkpublisher.AWSEBUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.amazonaws.regions.Regions;
import com.google.common.base.Joiner;

public class AWSEBElasticBeanstalkSetup extends AWSEBSetup {

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private final AWSEBCredentials credentials;
    private final Regions awsRegion;
    private final String applicationName;
    private final String versionLabelFormat;
    private final Boolean failOnError;
    private final List<String> environments;
    private List<AWSEBSetup> extensions;
    
    
    @DataBoundConstructor
    public AWSEBElasticBeanstalkSetup(Regions awsRegion, String credentials, String applicationName, 
            String environmentList, String versionLabelFormat, Boolean failOnError,
            List<AWSEBSetup> extensions) {
        this.awsRegion = awsRegion;
        this.credentials = AWSEBCredentials.getCredentialsByString(credentials);
        this.applicationName = applicationName;
        this.environments = new ArrayList<String>();
        for (String next : environmentList.split("\n")) {
            this.environments.add(next);
        }
        this.versionLabelFormat = versionLabelFormat;
        this.failOnError = failOnError;
        this.extensions = extensions;
    }

    
    
    public List<AWSEBSetup> getExtensions() {
        return extensions == null ? new ArrayList<AWSEBSetup>(0) : extensions;
    }

    public String getEnvironmentList() {
        return Joiner.on("\n").join(environments);
    }
    

    public List<String> getEnvironments() {
        return environments;
    }

    public Regions getAwsRegion() {
        return awsRegion == null ? Regions.US_WEST_1 : awsRegion;
    }

    public String getApplicationName() {
        return applicationName == null ? "" : applicationName;
    }

    public String getVersionLabelFormat() {
        return versionLabelFormat == null ? "" : versionLabelFormat;
    }
    

    public Boolean getFailOnError() {
        return failOnError == null ? false : failOnError;
    }
    
    public AWSEBCredentials getCredentials() {
        return credentials;
    }
    


    @Override
    public void perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws Exception {
        AWSEBEnvironmentUpdater updater = new AWSEBEnvironmentUpdater(build, launcher, listener, this);
        updater.perform();
    }
    
    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }
    
    public static DescriptorImpl getDesc() {
        return DESCRIPTOR;
    }
    
    
    @Extension
    public static class DescriptorImpl extends AWSEBSetupDescriptor {
        @Override
        public String getDisplayName() {
            return "Elastic Beanstalk Application";
        }
        

        public ListBoxModel doFillCredentialsItems(@QueryParameter String credentials) {
            ListBoxModel items = new ListBoxModel();
            for (AWSEBCredentials creds : AWSEBCredentials.getCredentials()) {

                items.add(creds, creds.toString());
                if (creds.toString().equals(credentials)) {
                    items.get(items.size()-1).selected = true;
                } 
            }

            return items;
        }

        public FormValidation doCheckEnvironmentList(@QueryParameter String environmentList) {
            List<String> badEnv = AWSEBUtils.getBadEnvironmentNames(environmentList);
            if (badEnv.size() > 0) {
                return FormValidation.error("Bad environment names: %s", badEnv.toString());
            } else {
                return FormValidation.ok();
            }
            
        }

        public FormValidation doLoadApplications(@QueryParameter("credentials") String credentialsString, @QueryParameter("awsRegion") String regionString) {
            AWSEBCredentials credentials = AWSEBCredentials.getCredentialsByString(credentialsString);
            if (credentials == null) {
                return FormValidation.error("Missing valid credentials");
            }
            Regions region = Enum.valueOf(Regions.class, regionString);
            if (region == null) {
                return FormValidation.error("Missing valid Region");
            }
            
            return FormValidation.ok(AWSEBUtils.getApplicationListAsString(credentials, region));
        }
        
        public FormValidation doLoadEnvironments(@QueryParameter("credentials") String credentialsString, @QueryParameter("awsRegion") String regionString, @QueryParameter("applicationName") String appName) {
            AWSEBCredentials credentials = AWSEBCredentials.getCredentialsByString(credentialsString);
            if (credentials == null) {
                return FormValidation.error("Missing valid credentials");
            }
            Regions region = Enum.valueOf(Regions.class, regionString);
            if (region == null) {
                return FormValidation.error("Missing valid Region");
            }
            
            if (appName == null) {
                return FormValidation.error("Missing an application name");
            }
            
           
            return FormValidation.ok(AWSEBUtils.getEnvironmentsListAsString(credentials, region, appName));
        }
        

        public List<AWSEBSetupDescriptor> getExtensionDescriptors() {
            List<AWSEBSetupDescriptor> extensions = new ArrayList<AWSEBSetupDescriptor>(1);
            extensions.add(AWSEBS3Setup.getDesc());
            return extensions;
        }
    }


}
