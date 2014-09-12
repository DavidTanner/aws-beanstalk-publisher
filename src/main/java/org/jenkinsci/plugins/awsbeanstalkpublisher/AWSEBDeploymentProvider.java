package org.jenkinsci.plugins.awsbeanstalkpublisher;

import java.util.List;

import com.amazonaws.regions.Regions;

public interface AWSEBDeploymentProvider {

	public String getRootObject();

	public String getKeyPrefix();

	public String getBucketName();

	public String getApplicationName();

	public String getVersionLabelFormat();

	public List<String> getEnvironments();

	public AWSEBCredentials getCredentials();

	public Regions getAwsRegion();

	public String getExcludes();

	public String getIncludes();
	
	public boolean isOverwriteExistingFile();

}
