package org.jenkinsci.plugins.awsbeanstalkpublisher;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsResult;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEventsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEventsResult;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;
import com.amazonaws.services.elasticbeanstalk.model.EventDescription;
import com.amazonaws.services.elasticbeanstalk.model.UpdateEnvironmentRequest;

public class AWSEBEnvironmentUpdaterThread implements Callable<AWSEBEnvironmentUpdaterThread> {
    private static final int MAX_ATTEMPTS = 15;
    private static final int WAIT_TIME_SECONDS = 30;
    private static final long WAIT_TIME_MILLISECONDS = TimeUnit.SECONDS.toMillis(WAIT_TIME_SECONDS);
    
    private final EnvironmentDescription envd;
    private final AWSElasticBeanstalk awseb;
    private final DescribeEnvironmentsRequest envRequest;
    private final DescribeEventsRequest eventRequest;
    private final String environmentId;
    private final PrintStream logger;
    private final String versionLabel;

    private boolean isUpdated = false;
    private boolean isComplete = false;
    private boolean success = false;
    private int nAttempt;

    public AWSEBEnvironmentUpdaterThread(AWSElasticBeanstalk awseb, EnvironmentDescription envd, PrintStream logger, String versionLabel) {
        this.awseb = awseb;
        this.envd = envd;
        this.logger = logger;
        this.versionLabel = versionLabel;
        envRequest = new DescribeEnvironmentsRequest().withEnvironmentIds(envd.getEnvironmentId());
        eventRequest = new DescribeEventsRequest().withEnvironmentId(envd.getEnvironmentId());
        
        this.environmentId = envd.getEnvironmentId();
        nAttempt = 0;

    }

    private void log(String mask, Object... args) {
        logger.println(String.format(mask, args));
    }

    private void updateEnv() {
        lastEventDate = new Date();
        
        log("'%s': Attempt %d/%d", envd.getEnvironmentName(), nAttempt, MAX_ATTEMPTS);
        
        
        UpdateEnvironmentRequest uavReq = new UpdateEnvironmentRequest().withEnvironmentId(environmentId).withVersionLabel(versionLabel);
        nAttempt = 0;
        isUpdated = true;
        

        try {
            awseb.updateEnvironment(uavReq);
            isReady();
            
        } catch (Exception e) {
            log("'%s': Problem:", envd.getEnvironmentName());
            e.printStackTrace(logger);

            if (nAttempt >= MAX_ATTEMPTS) {
                log("'%s': Unable to update environment!", envd.getEnvironmentName());
                isComplete = true;
            }

        }
    }
    
    private Date lastEventDate;

    private void isReady() {
        try {
            String envName = envd.getEnvironmentName();
            
            try {
                DescribeEventsResult eventResult = awseb.describeEvents(eventRequest);
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zZ");
                
                for (EventDescription event : eventResult.getEvents()) {
                    Date eventDate = event.getEventDate();
                    if (eventDate.after(lastEventDate)){
                        lastEventDate = eventDate;
                        // 2015-04-13 20:12:44 UTC-0600
                        String eventDateString = dateFormat.format(eventDate);
                        log("'%s' event: [%s] (%s) %s", envName, eventDateString, event.getSeverity(), event.getMessage());
                    }
                }
            } catch (Exception e) {
                log("'%s': Unable to process events %s", envName, e.getMessage());
            }

            DescribeEnvironmentsResult result = awseb.describeEnvironments(envRequest);
            EnvironmentDescription lastEnv = null;
            for (EnvironmentDescription env : result.getEnvironments()) {
                if (env.getEnvironmentId().equals(envd.getEnvironmentId())){
                    lastEnv = env;
                    break;
                }
            }
            if (lastEnv == null) {
                isComplete = true;
                log("'%s' is no longer found in ElasticBeanstalk!!!!", envName);
                return;
            }
            if (lastEnv.getStatus().equals("Ready")) {
                isComplete = true;
                
                log("'%s': Updated!", envName);
                log("'%s': Current version is:'%s'", envName, lastEnv.getVersionLabel());
                
                if (lastEnv.getVersionLabel().equals(versionLabel)) {
                    success = true;
                    log("'%s': Update was successful", envName);
                } else {
                    success = false;
                    log("'%s': Update failed, please check the recent events on the AWS console!!!!", envName);
                }
            } else {
                log("'%s': Waiting for update to finish. Status: %s", envName, lastEnv.getStatus());
            }
        } catch (Exception e) {

            log("Problem: " + e.getMessage());

            if (nAttempt >= MAX_ATTEMPTS) {
                log("'%s': unable to get environment status.", envd.getEnvironmentName());
                isComplete = true;
            }
        }
    }

    @Override
    public AWSEBEnvironmentUpdaterThread call() throws Exception {
        run();
        return this;
    }
    
    public void printResults() {
        StringBuilder status = new StringBuilder();
        status.append("'");
        status.append(envd.getEnvironmentName());
        status.append("': ");
        if (success) {
            status.append("Completed successfully.");
        } else {
            if (isUpdated) {
                status.append("Was updated, but couldn't be verified!");
            } else {
                status.append("Failed to be updated!!");
            }
        }
        log(status.toString());
    }
    
    public boolean isSuccessfull() {
        return success;
    }

    public void run() {
        while (!isComplete) {
            if (isUpdated) {
                isReady();
            } else {
                updateEnv();
            }
            if (!isComplete){
                try {
                    log("'%s': Pausing update for %d seconds", envd.getEnvironmentName(), WAIT_TIME_SECONDS);
                    Thread.sleep(WAIT_TIME_MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace(logger);
                }
            }
        }
    }
}
