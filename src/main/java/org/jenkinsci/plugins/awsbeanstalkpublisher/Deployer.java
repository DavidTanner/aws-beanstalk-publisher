package org.jenkinsci.plugins.awsbeanstalkpublisher;


import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.util.DirScanner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.regions.Region;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClient;
import com.amazonaws.services.elasticbeanstalk.model.CreateApplicationVersionRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsResult;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;
import com.amazonaws.services.elasticbeanstalk.model.S3Location;
import com.amazonaws.services.elasticbeanstalk.model.UpdateEnvironmentRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.google.common.annotations.VisibleForTesting;

public class Deployer {
	private static final int MAX_ATTEMPTS = 15;

	private AWSEBDeploymentProvider context;
	
	private PrintStream logger;

	private AmazonS3 s3;

	private AWSElasticBeanstalk awseb;

	private File localArchive;

	private FilePath rootFileObject;

	private String keyPrefix;

	private String bucketName;

	private String applicationName;

	private String versionLabel;

	private String objectKey;

	private String s3ObjectPath;

	private EnvVars env;

	private List<String> environmentNames;

	private BuildListener listener;

	public Deployer(AWSEBDeploymentProvider builder,
			AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
		this.context = builder;
		this.logger = listener.getLogger();
		this.env = build.getEnvironment(listener);
		this.listener = listener;
		
		this.rootFileObject = new FilePath(build.getWorkspace(),
				getValue(context.getRootObject()));
	}

	public void perform() throws Exception {
		initAWS();
		
		log("Running AWS Elastic Beanstalk deploy for %s, on %s", applicationName, environmentNames);

		localArchive = getLocalFileObject(rootFileObject);

		uploadArchive();

		createApplicationVersion();
		

		updateEnvironments();

		listener.finished(Result.SUCCESS);

	}
	
	
	public void updateEnvironments() throws Exception{
		DescribeEnvironmentsResult environments;
		if (environmentNames != null && !environmentNames.isEmpty()) { 
			environments = awseb.describeEnvironments(
					new DescribeEnvironmentsRequest()
						.withApplicationName(applicationName)
						.withEnvironmentNames(environmentNames)
				);
		} else {
			environments = awseb.describeEnvironments(
					new DescribeEnvironmentsRequest()
						.withApplicationName(applicationName)
				);
		}
		updateEnvironments(environments);
	}

	public void updateEnvironments(DescribeEnvironmentsResult environments) throws Exception {
		
		List<EnvironmentDescription> envList = environments.getEnvironments();
		

		if (envList.size() <= 0) {
			log("No environments found matching applicationName:%s with environments:%s", 
					applicationName, environmentNames);
		}

		for (EnvironmentDescription envd : envList) { // TODO: This should get threaded out if possible for speed.

			String environmentId = envd.getEnvironmentId();

			log("Environment found (environment id=%s, name=%s). Attempting to update environment to version label %s",
					environmentId, envd.getEnvironmentName(), versionLabel);
			
			for (int nAttempt = 1; nAttempt <= MAX_ATTEMPTS; nAttempt++) {

				log("Attempt %d/%d", nAttempt, MAX_ATTEMPTS);
				
				UpdateEnvironmentRequest uavReq = new UpdateEnvironmentRequest()
						.withEnvironmentId(environmentId)
						.withVersionLabel(versionLabel);

				try {
					awseb.updateEnvironment(uavReq);

					log("Environment Updated.!");
					
					break;
				} catch (Exception exc) {
					log("Problem: " + exc.getMessage());

					if (nAttempt >= MAX_ATTEMPTS) {
						log("Unable to update environment.");

						throw exc;
					}

					log("Reattempting in 90s");

					Thread.sleep(TimeUnit.SECONDS.toMillis(90));
				}
			}
		}
	}

	private void createApplicationVersion() {
		log("Creating application version %s for application %s for path %s",
				versionLabel, applicationName, s3ObjectPath);

		CreateApplicationVersionRequest cavRequest = new CreateApplicationVersionRequest()
				.withApplicationName(applicationName)
				.withAutoCreateApplication(true)
				.withSourceBundle(new S3Location(bucketName, objectKey))
				.withVersionLabel(versionLabel);

		awseb.createApplicationVersion(cavRequest);
	}

	// TODO: Probably improve this method so that when the object exists we check the MD5 and
	//       report in the log our findings.
	private void uploadArchive() {
		this.keyPrefix = getValue(context.getKeyPrefix());
		this.bucketName = getValue(context.getBucketName());
		this.applicationName = getValue(context.getApplicationName());
		this.versionLabel = getValue(context.getVersionLabelFormat());
		this.environmentNames = getValue(context.getEnvironments());

		objectKey = formatPath("%s/%s-%s.zip", keyPrefix, applicationName,
				versionLabel);

		s3ObjectPath = "s3://" + formatPath("%s/%s", bucketName, objectKey);

		log("Uploading file %s as %s", localArchive.getName(), s3ObjectPath);
		
		boolean uploadFile = true;
	
		try {
			ObjectMetadata meta = s3.getObjectMetadata(bucketName, objectKey);
		} catch (AmazonS3Exception s3e) {
			if (s3e.getStatusCode() == 404) {
		        // i.e. 404: NoSuchKey - The specified key does not exist
				uploadFile = context.isOverwriteExistingFile();
			} else {
				throw s3e;
			}
		}
		
		if (uploadFile) {
			uploadFile(bucketName, objectKey, localArchive);
		}
		
	}
	
	private void uploadFile(String bucketName, String objectKey, File file) {
		// Each instance of TransferManager maintains its own thread pool
		// where transfers are processed, so share an instance when possible
		TransferManager tx = new TransferManager(context.getCredentials().getAwsCredentials());

		// The upload and download methods return immediately, while
		// TransferManager processes the transfer in the background thread pool
		final Upload upload = tx.upload(bucketName, objectKey, file);

		// You can set a progress listener directly on a transfer, or you can pass one into
		// the upload object to have it attached to the transfer as soon as it starts
		upload.addProgressListener(new ProgressListener() {
		    // This method is called periodically as your transfer progresses
		    public void progressChanged(ProgressEvent progressEvent) {
		        logger.println(upload.getProgress().getPercentTransferred() + "%");
		 
		        if (progressEvent.getEventType() == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
		            logger.println("Upload complete!!!");
		        }
		    }
		});
		
		// waitForCompletion blocks the current thread until the transfer completes
		// and will throw an AmazonClientException or AmazonServiceException if
		// anything went wrong.
		try {
			upload.waitForCompletion();
		} catch (AmazonServiceException e) {
			e.printStackTrace(logger);
		} catch (AmazonClientException e) {
			e.printStackTrace(logger);
		} catch (InterruptedException e) {
			e.printStackTrace(logger);
		}
	}

	private void initAWS() {
		log("Creating S3 and AWSEB Client (AWS Access Key Id: %s, region: %s)",
				context.getCredentials().getAwsAccessKeyId(),
				context.getAwsRegion());

		AWSCredentialsProvider credentials = context.getCredentials().getAwsCredentials();
		Region region = Region.getRegion(context.getAwsRegion());
		ClientConfiguration clientConfig = new ClientConfiguration();

		clientConfig.setUserAgent(ClientConfiguration.DEFAULT_USER_AGENT);

		s3 = region.createClient(AmazonS3Client.class, credentials,
				clientConfig);
		awseb = region.createClient(AWSElasticBeanstalkClient.class,
				credentials, clientConfig);
	}
	
	@VisibleForTesting
	void setAsweb(AWSElasticBeanstalk awseb) {
		this.awseb = awseb;
	}
	
	@VisibleForTesting
	void setS3(AmazonS3 s3) {
		this.s3 = s3;
	}
	

	void log(String mask, Object... args) {
		logger.println(String.format(mask, args));
	}

	private File getLocalFileObject(FilePath rootFileObject) throws Exception {
		File resultFile = File.createTempFile("awseb-", ".zip");

		if (!rootFileObject.isDirectory()) {
			log("Root File Object is a file. We assume its a zip file, which is okay.");

			rootFileObject.copyTo(new FileOutputStream(resultFile));
		} else {
			log("Zipping contents of Root File Object (%s) into tmp file %s (includes=%s, excludes=%s)",
					rootFileObject.getName(), resultFile.getName(),
					context.getIncludes(), context.getExcludes());

			rootFileObject.zip(new FileOutputStream(resultFile),
					new DirScanner.Glob(context.getIncludes(),
							context.getExcludes()));
		}

		return resultFile;
	}

	private String formatPath(String mask, Object... args) {
		return strip(String.format(mask, args).replaceAll("/{2,}", ""));
	}
	
	private List<String> getValue(List<String> values) {
		List<String> newValues = new ArrayList<String>(values.size());
		for (String value : values) {
			newValues.add(getValue(value));
		}
		return newValues;
	}

	private String getValue(String value) {
		return strip(Util.replaceMacro(value, env));
	}

	private static String strip(String str) {
		return StringUtils.strip(str, "/ ");
	}
}
