package org.jenkinsci.plugins.awsbeanstalkpublisher;



import java.util.List;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;

import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.regions.Regions;

/**
 * AWS Elastic Beanstalk Deployment
 */
public class AWSEBDeploymentBuilder extends Builder implements BuildStep, AWSEBDeploymentProvider {
	@DataBoundConstructor
	public AWSEBDeploymentBuilder(Regions awsRegion,
			String applicationName, List<String> environments, String bucketName,
			String keyPrefix, String versionLabelFormat, String rootObject,
			String includes, String excludes, String credentials, Boolean overwriteExistingFile) {
		super();
		this.awsRegion = awsRegion;
		this.applicationName = applicationName;
		this.environments = environments;
		this.bucketName = bucketName;
		this.keyPrefix = keyPrefix;
		this.versionLabelFormat = versionLabelFormat;
		this.rootObject = rootObject;
		this.includes = includes;
		this.excludes = excludes;
		this.credentials = AWSEBCredentials.getCredentialsByName(credentials);
		this.overwriteExistingFile = overwriteExistingFile;
	}

	/**
	 * AWS Region
	 */
	private final Regions awsRegion;

	public Regions getAwsRegion() {
		return awsRegion;
	}

	
	/**
	 * Application Name
	 */
	private final String applicationName;

	public String getApplicationName() {
		return applicationName;
	}


	/**
	 * Environment Name
	 */
	private final List<String> environments;

	public List<String> getEnvironments() {
		return environments;
	}


	/**
	 * Bucket Name
	 */
	private final String bucketName;

	public String getBucketName() {
		return bucketName;
	}


	/**
	 * Key Format
	 */
	private final String keyPrefix;

	public String getKeyPrefix() {
		return keyPrefix;
	}


	private final String versionLabelFormat;

	public String getVersionLabelFormat() {
		return versionLabelFormat;
	}


	private final String rootObject;

	public String getRootObject() {
		return rootObject;
	}


	private final String includes;

	public String getIncludes() {
		return includes;
	}


	private final String excludes;

	public String getExcludes() {
		return excludes;
	}
	
	private final Boolean overwriteExistingFile;

	@Override
	public boolean isOverwriteExistingFile() {
		return (overwriteExistingFile != null ? true : overwriteExistingFile);
	}

	
	/**
	 * Credentials Name from the global config
	 */
	private final AWSEBCredentials credentials;
	
	public AWSEBCredentials getCredentials() {
		return credentials;
	}
	
    public ListBoxModel doFillCredentialsItems() {
    	ListBoxModel items = new ListBoxModel();
    	for (AWSEBCredentials credentials : AWSEBCredentials.getCredentials()) {
    		items.add(credentials, credentials.getName());
    	}

        return items;
    }
	
	
	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) {
		try {
			Deployer deployer = new Deployer(this, build, launcher, listener);

			deployer.perform();

			return true;
		} catch (Exception exc) {
			throw new RuntimeException(exc);
		}
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
	@Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link HelloWorldBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @SuppressWarnings("rawtypes")
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Deploy into AWS Elastic Beanstalk";
        }
        

        public ListBoxModel doFillCredentialsItems() {
        	ListBoxModel items = new ListBoxModel();
        	for (AWSEBCredentials credentials : AWSEBCredentials.getCredentials()) {
        		items.add(credentials, credentials.getName());
        	}

            return items;
        }
    	
    }
}