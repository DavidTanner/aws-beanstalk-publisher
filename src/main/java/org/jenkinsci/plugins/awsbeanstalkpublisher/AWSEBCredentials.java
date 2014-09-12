package org.jenkinsci.plugins.awsbeanstalkpublisher;

import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.internal.StaticCredentialsProvider;

import hudson.model.ModelObject;
import hudson.util.CopyOnWriteList;

public class AWSEBCredentials implements ModelObject {

    private final String name;
    private final String awsAccessKeyId;
    private final String awsSecretSharedKey;
    
    private final static CopyOnWriteList<AWSEBCredentials> credentials = new CopyOnWriteList<AWSEBCredentials>();

    public String getName() {
        return name;
    }

    public String getAwsAccessKeyId() {
        return awsAccessKeyId;
    }

    public String getAwsSecretSharedKey() {
        return awsSecretSharedKey;
    }

    public AWSEBCredentials() {
        name = null;
        awsAccessKeyId = null;
        awsSecretSharedKey = null;
    }

    @DataBoundConstructor
    public AWSEBCredentials(String name, String awsAccessKeyId, String awsSecretSharedKey) {
        this.name = name;
        this.awsAccessKeyId = awsAccessKeyId;
        this.awsSecretSharedKey = awsSecretSharedKey;
    }

	public String getDisplayName() {
		return getName();
	}
	
	public AWSCredentialsProvider getAwsCredentials() {
		AWSCredentialsProvider credentials = new AWSCredentialsProviderChain(
				new StaticCredentialsProvider(new BasicAWSCredentials(
						getAwsAccessKeyId(),
						getAwsSecretSharedKey())));
		return credentials;
	}
	
	public static void configureCredentials(List<AWSEBCredentials> toAdd) {
		credentials.replaceBy(toAdd);
	}
	
    public static AWSEBCredentials[] getCredentials() {
        return credentials.toArray(new AWSEBCredentials[0]);
    }
    
    public static AWSEBCredentials getCredentialsByName(String credentialsName) {
    	AWSEBCredentials[] credentials = getCredentials();

        if (credentialsName == null && credentials.length > 0)
            // default
            return credentials[0];

        for (AWSEBCredentials credential : credentials) {
            if (credential.getName().equals(credentialsName))
                return credential;
        }
        
        return null;
    }
}
