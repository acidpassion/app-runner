package scaffolding;

import com.danielflower.apprunner.mgmt.AppDescription;
import com.danielflower.apprunner.mgmt.Availability;
import com.danielflower.apprunner.mgmt.BuildStatus;
import com.danielflower.apprunner.runners.AppRunnerFactoryProvider;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.maven.shared.invoker.InvocationOutputHandler;

import java.io.File;
import java.util.ArrayList;

public class MockAppDescription implements AppDescription {
    private volatile String gitUrl;
    private final  String name;
    public int updateCount = 0;
    private ArrayList<String> contributors;

    public MockAppDescription(String name, String gitUrl) {
        this.gitUrl = gitUrl;
        this.name = name;
        this.contributors = new ArrayList<>();
    }

    public String name() {
        return name;
    }

    public String gitUrl() {
        return gitUrl;
    }

    @Override
    public void gitUrl(String url) {
        this.gitUrl = url;
    }

    public Availability currentAvailability() {
        return Availability.available();
    }

    @Override
    public BuildStatus lastBuildStatus() {
        return BuildStatus.notStarted(gitUrl, null);
    }

    @Override
    public BuildStatus lastSuccessfulBuild() {
        return null;
    }

    public String latestBuildLog() {
        return "";
    }

    public String latestConsoleLog() {
        return "";
    }

    public ArrayList<String> contributors() {
        return contributors;
    }

    @Override
    public File dataDir() {
        throw new NotImplementedException("dataDir");
    }

    public void stopApp() {
    }

    public void update(AppRunnerFactoryProvider runnerProvider, InvocationOutputHandler outputHandler) throws Exception {
        ++updateCount;
    }

    @Override
    public void delete() {

    }
}
