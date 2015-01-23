package org.jenkinsci.plugins.awsbeanstalkpublisher;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.internal.StaticCredentialsProvider;

import hudson.model.ModelObject;

public class AWSEBCredentials implements ModelObject {

    private final String name;
    private final String awsAccessKeyId;
    private final String awsSecretSharedKey;

    private final static Set<AWSEBCredentials> credentials = new HashSet<AWSEBCredentials>();

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
        return name + " : " + awsAccessKeyId;
    }

    public AWSCredentialsProvider getAwsCredentials() {
        AWSCredentialsProvider credentials = new AWSCredentialsProviderChain(new StaticCredentialsProvider(new BasicAWSCredentials(getAwsAccessKeyId(), getAwsSecretSharedKey())));
        return credentials;
    }

    public static void configureCredentials(Collection<AWSEBCredentials> toAdd) {
        credentials.clear();
        credentials.addAll(toAdd);
    }

    public static Set<AWSEBCredentials> getCredentials() {
        return credentials;
    }

    public static AWSEBCredentials getCredentialsByString(String credentialsString) {
        Set<AWSEBCredentials> credentials = getCredentials();

        for (AWSEBCredentials credential : credentials) {
            if (credential.toString().equals(credentialsString)) {
                return credential;
            }
        }

        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AWSEBCredentials)) {
            return false;
        }
        AWSEBCredentials creds = (AWSEBCredentials) o;
        boolean isSame = this.awsAccessKeyId.equals(creds.awsAccessKeyId);
        isSame &= this.name.equals(creds.name);
        return isSame;
    }

    @Override
    public String toString() {
        return name + " : " + awsAccessKeyId;
    }

    @Override
    public int hashCode() {
        return (awsAccessKeyId).hashCode();
    }
}
