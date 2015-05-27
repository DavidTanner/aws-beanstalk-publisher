package org.jenkinsci.plugins.awsbeanstalkpublisher.extensions;

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Saveable;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jenkinsci.plugins.awsbeanstalkpublisher.AWSEBCredentials;
import org.jenkinsci.plugins.awsbeanstalkpublisher.AWSEBEnvironmentUpdater;
import org.jenkinsci.plugins.awsbeanstalkpublisher.AWSEBUtils;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.envlookup.ByName;
import org.jenkinsci.plugins.awsbeanstalkpublisher.extensions.envlookup.ByUrl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.amazonaws.regions.Regions;

public class AWSEBElasticBeanstalkSetup extends AWSEBSetup {

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private final AWSEBCredentials credentials;
    private final Regions awsRegion;
    private final String applicationName;
    private final String versionLabelFormat;
    private final Boolean failOnError;

    @Deprecated
    private transient List<String> environments;

    private final String awsRegionText;

    private DescribableList<AWSEBSetup, AWSEBSetupDescriptor> extensions;
    private DescribableList<AWSEBSetup, AWSEBSetupDescriptor> envLookup;

    @DataBoundConstructor
    public AWSEBElasticBeanstalkSetup(
            Regions awsRegion, 
            String awsRegionText, 
            String credentials, 
            String applicationName, 
            String versionLabelFormat, 
            Boolean failOnError,
            List<AWSEBSetup> extensions,
            List<AWSEBSetup> envLookup) {
        
        this.awsRegion = awsRegion;
        this.awsRegionText = awsRegionText;
        this.credentials = AWSEBCredentials.getCredentialsByString(credentials);
        this.applicationName = applicationName;

        this.versionLabelFormat = versionLabelFormat;
        this.failOnError = failOnError;
        this.extensions = new DescribableList<AWSEBSetup, AWSEBSetupDescriptor>(Saveable.NOOP, Util.fixNull(extensions));
        
        this.envLookup = new DescribableList<AWSEBSetup, AWSEBSetupDescriptor>(Saveable.NOOP, Util.fixNull(envLookup));
        if (this.envLookup.size() == 0){
            this.envLookup.add(new ByName(""));
        }
    }

    public DescribableList<AWSEBSetup, AWSEBSetupDescriptor> getExtensions() {
        if (extensions == null) {
            extensions = new DescribableList<AWSEBSetup, AWSEBSetupDescriptor>(Saveable.NOOP, Util.fixNull(extensions));
        }
        return extensions;
    }

    public Object readResolve() {
        if (environments != null && !environments.isEmpty()) {
            try {
                addIfMissing(new ByName(Arrays.toString(environments.toArray(new String[] {}))));
                environments = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return this;
    }

    protected void addIfMissing(AWSEBSetup ext) throws IOException {
        if (getExtensions().get(ext.getClass()) == null) {
            getExtensions().add(ext);
        }
    }

    public String getAwsRegionText() {
        return awsRegionText;
    }

    public Regions getAwsRegion(AbstractBuild<?, ?> build) {
        String regionName = AWSEBUtils.getValue(build, awsRegionText);
        try {
            return Regions.fromName(regionName);
        } catch (Exception e) {
            return getAwsRegion();
        }
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
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws Exception {
        AWSEBEnvironmentUpdater updater = new AWSEBEnvironmentUpdater(build, launcher, listener, this);
        return updater.perform();
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
                    items.get(items.size() - 1).selected = true;
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

        public List<AWSEBSetupDescriptor> getExtensionDescriptors() {
            List<AWSEBSetupDescriptor> extensions = new ArrayList<AWSEBSetupDescriptor>(1);
            extensions.add(AWSEBS3Setup.getDesc());
            return extensions;
        }
        
        public List<AWSEBSetup> getEnvLookup(List<AWSEBSetup> envLookup) {
            if (envLookup != null && envLookup.size() > 0) {
                return envLookup;
            }
            List<AWSEBSetup> lookup = new ArrayList<AWSEBSetup>(1);
            lookup.add(new ByName(""));
            return lookup;
        }
        
        public List<AWSEBSetupDescriptor> getEnvironmentLookupDescriptors() {
            List<AWSEBSetupDescriptor> extensions = new ArrayList<AWSEBSetupDescriptor>(1);
            return extensions;
        }
    }

}
