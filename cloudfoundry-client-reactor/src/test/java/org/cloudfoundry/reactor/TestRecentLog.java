package org.cloudfoundry.reactor;

import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.applications.GetApplicationRequest;
import org.cloudfoundry.client.v2.applications.GetApplicationResponse;
import org.cloudfoundry.client.v2.applications.UpdateApplicationRequest;
import org.cloudfoundry.doppler.DopplerClient;
import org.cloudfoundry.doppler.Envelope;
import org.cloudfoundry.doppler.RecentLogsRequest;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.cloudfoundry.reactor.doppler.ReactorDopplerClient;
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class TestRecentLog {

    private static final Logger log = LoggerFactory.getLogger("org.cloudfoundry.reactor.TestRecentLog");


    private static final Duration FIRST_TIMEOUT = Duration.ofSeconds(120);

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    public static void main(String[] args) throws IOException{
        Properties testProperties= new Properties();
        testProperties.load(TestRecentLog.class.getResourceAsStream("/recent_logs.properties"));
        final ConnectionContext connectionContext = DefaultConnectionContext.builder()
            .apiHost(testProperties.getProperty("api.host"))
            .skipSslValidation(Boolean.parseBoolean(testProperties.getProperty("api.skip.ssl")))
            .build();
        final TokenProvider tokenProvider = PasswordGrantTokenProvider.builder()
            .username(testProperties.getProperty("user.name"))
            .password(testProperties.getProperty("user.password"))
            .build();
        final CloudFoundryClient client = ReactorCloudFoundryClient.builder()
            .connectionContext(connectionContext)
            .tokenProvider(tokenProvider)
            .build();
        final DopplerClient dopplerClient = ReactorDopplerClient.builder()
            .connectionContext(connectionContext)
            .tokenProvider(tokenProvider)
            .build();
        String applicationId = testProperties.getProperty("application.id");

        int cpt = 0;
        boolean start = true;

        GetApplicationResponse response = client.applicationsV2().get(GetApplicationRequest.builder()
            .applicationId(applicationId)
            .build())
            .block(FIRST_TIMEOUT);
        log.debug("Application {} exists with name {}", applicationId, response.getEntity().getName());
        final Duration sleepBeforeFirstIteration = Duration.ofSeconds(
            Integer.parseInt(testProperties.getProperty("test.sleep.before.first.iteration"))
        );
        final Duration timeOfSleep = Duration.ofSeconds(
            Integer.parseInt(testProperties.getProperty("test.sleep.between.iteration"))
        );
        final boolean alternateState = Boolean.parseBoolean(testProperties.getProperty("alternate.application.state", "false"));
        sleep(sleepBeforeFirstIteration);
        try {
            while (true) {
                if (alternateState && cpt % 10 == 0) {
                    String newState = start ? "STARTED" : "STOPPED";
                    updateApplication(client, applicationId, newState);
                    start = !start;
                    log.debug("State of application moved to {}", newState);
                }
                cpt++;
                try {
                    getRemoteLogs(cpt, dopplerClient, applicationId);
                } catch (Throwable t) {
                    logError(t);
                }
                sleep(timeOfSleep);
            }
        } catch (Throwable t) {
            log.error("Process ended unexpectedly", t);
        }
    }

    private static void updateApplication(CloudFoundryClient client, String applicationId, String state) {
        try {
            client.applicationsV2().update(UpdateApplicationRequest.builder()
                .applicationId(applicationId)
                .state(state)
                .build())
                .block(TIMEOUT);
        } catch (Throwable t) {
            log.error("error while moving application to " + state, t);
        }
    }

    private static void sleep(Duration duration) {
        try {
            log.debug("sleep - sleeping {} seconds", duration.getSeconds());
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException i) {
            log.error("sleep - Interrupted while sleeping");
        }
    }

    private static void getRemoteLogs(int number, DopplerClient dopplerClient, String applicationId) throws Throwable {
        try {
            log.debug("{} - getRemoteLogs - start", number);
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicLong count = new AtomicLong();
            final AtomicReference<Throwable> errorReference = new AtomicReference<>();
            dopplerClient.recentLogs(RecentLogsRequest.builder()
                .applicationId(applicationId)
                .build())
                .subscribe(envelope -> {
                        logEnvelope(envelope);
                        count.incrementAndGet();
                    },
                    throwable -> {
                        errorReference.set(throwable);
                        latch.countDown();
                    },
                    latch::countDown);
            log.debug("{} - getRemoteLogs - waiting", number);
            if (!latch.await(TIMEOUT.getSeconds(), TimeUnit.SECONDS)) {
                throw new IllegalStateException("Subscriber timed out");
            } else if (errorReference.get() != null) {
                throw errorReference.get();
            } else {
                log.debug("{} - getRemoteLogs - got {} envelope(s)", number, count.get());
            }
        } catch (InterruptedException i) {
            log.error("Interrupted while waiting for call result");
        }
    }

    private static void logEnvelope(Envelope envelope) {
        log.debug(envelope.toString());
    }

    private static void logError(Throwable t) {
        log.error("Un-managed error", t);
    }

    private static String getErrorKey(Throwable t) {
        return t.getClass().getName() + " - " + t.getMessage();
    }

    private static void logEnvelopes(List<byte[]> parts, String filename) {
        try(FileOutputStream f = new FileOutputStream(filename)){
            for(Iterator<byte[]> it = parts.iterator(); it.hasNext();){
                byte[] part = it.next();
                if(it.hasNext()){
                    f.write("WELL READ PART\n".getBytes());
                    f.write(part);
                    f.write("\n".getBytes());
                } else {
                    f.write("ERROR PART\n".getBytes());
                    f.write(part);
                    f.write("\n".getBytes());
                }
            }
        }catch(IOException i){
            log.error("Error while opening {}", filename);
        }
    }

}