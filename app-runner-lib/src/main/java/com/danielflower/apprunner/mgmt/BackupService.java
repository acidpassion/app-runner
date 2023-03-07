package com.danielflower.apprunner.mgmt;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.RemoteSetUrlCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.danielflower.apprunner.FileSandbox.fullPath;

public class BackupService {
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final Git repo;
    public final String remoteUri;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private volatile Exception lastError = null;
    public volatile Instant lastSuccessfulBackupTime;
    private final int backupTimeInMinutes;

    public BackupService(Git dataDirRepo, String remoteUri, int backupTimeInMinutes) {
        this.repo = dataDirRepo;
        this.remoteUri = remoteUri;
        this.backupTimeInMinutes = backupTimeInMinutes;
    }

    public void start() {
        executor.scheduleWithFixedDelay(() -> {
            try {
                log.info("Going to backup data dir");
                backup();
                lastSuccessfulBackupTime = Instant.now();
                lastError = null;
            } catch (Exception e) {
                lastError = e;
                log.error("Unable to back up AppRunner data dir", e);
            }
        }, 0, backupTimeInMinutes, TimeUnit.MINUTES);
    }

    public Optional<Exception> lastRunError() {
        return Optional.ofNullable(lastError);
    }

    public synchronized void backup() throws Exception {
        repo.add().setUpdate(false).addFilepattern(".").call();
        Status status = repo.status().setIgnoreSubmodules(SubmoduleWalk.IgnoreSubmoduleMode.ALL).call();
        log.debug("status.getUncommittedChanges() = " + status.getUncommittedChanges());
        if (!status.getUncommittedChanges().isEmpty()) {
            for (String missingPath : status.getMissing()) {
                repo.rm().addFilepattern(missingPath).call();
            }

            log.info("Changes detected in the following files: " + status.getUncommittedChanges());
            repo.commit()
                .setMessage("Backing up data dir")
                .setAuthor("AppRunner BackupService", "noemail@example.org")
                .call();
            Iterable<PushResult> pushResults = repo.push().call();
            for (PushResult pushResult : pushResults) {
                log.info("Result of pushing to remote: " + pushResult.getRemoteUpdates());
            }
        } else {
            log.info("No changes to back up");
        }

    }

    public void stop() throws InterruptedException {
        executor.shutdown();
        boolean terminated = executor.awaitTermination(5, TimeUnit.MINUTES);
        if (!terminated) {
            log.info("Timed out waiting for backup service to stop.");
        }
    }

    public static BackupService prepare(File localDir, URIish remoteUri, int backupTimeInMinutes) throws GitAPIException, IOException {
        Git local;
        try {
            local = Git.open(localDir);
        } catch (RepositoryNotFoundException e) {
            log.info("Initialising " + fullPath(localDir) + " as a git repo for backup purposes");
            local = Git.init().setDirectory(localDir).setBare(false).call();
        }
        log.info("Setting backup URL to " + remoteUri);
        if (local.remoteList().call().stream().anyMatch(remoteConfig -> remoteConfig.getName().equals("origin"))) {
            RemoteSetUrlCommand remoteSetUrlCommand = local.remoteSetUrl();
            remoteSetUrlCommand.setRemoteName("origin");
            remoteSetUrlCommand.setRemoteUri(remoteUri);
            remoteSetUrlCommand.call();
        } else {
            RemoteAddCommand remoteAddCommand = local.remoteAdd();
            remoteAddCommand.setName("origin");
            remoteAddCommand.setUri(remoteUri);
            remoteAddCommand.call();
        }

        URL inputUrl = BackupService.class.getResource("/dataDirGitIgnore.txt");
        FileUtils.copyURLToFile(inputUrl, new File(localDir, ".gitignore"));

        return new BackupService(local, remoteUri.toString(), backupTimeInMinutes);
    }
}
