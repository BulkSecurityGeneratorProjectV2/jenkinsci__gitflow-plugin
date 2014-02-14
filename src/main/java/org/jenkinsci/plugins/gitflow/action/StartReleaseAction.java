package org.jenkinsci.plugins.gitflow.action;

import java.io.IOException;
import java.util.Map;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;

/**
 * This class executes the required steps for the Gitflow action <i>Start Release</i>.
 *
 * @author Marc Rohlfs, Silpion IT-Solutions GmbH - rohlfs@silpion.de
 */
public class StartReleaseAction extends AbstractGitflowAction {

    private static final String DEVELOP_BRANCH = "develop";

    private static final String PARAM_RELEASE_VERSION = "releaseVersion";
    private static final String PARAM_NEXT_DEVELOPMENT_VERSION = "nextDevelopmentVersion";
    private static final String PARAM_RELEASE_NEXT_DEVELOPMENT_VERSION = "releaseNextDevelopmentVersion";

    private final String releaseBranch;
    private final String releaseVersion;
    private final String releaseNextDevelopmentVersion;
    private final String nextDevelopmentVersion;

    /**
     * Initialises a new <i>Start Release</i> action.
     *
     * @param params   the required parameters for the <i>Start Release</i> action.
     * @param build    the <i>Start Release</i> build that is in progress.
     * @param launcher can be used to launch processes for this build - even if the build runs remotely.
     * @param listener can be used to send any message.
     * @throws IOException          if an error occurs that causes/should cause the build to fail.
     * @throws InterruptedException if the build is interrupted during execution.
     */
    public StartReleaseAction(final Map<String, String> params, final AbstractBuild build, final Launcher launcher, final BuildListener listener)
            throws IOException, InterruptedException {
        super(build, launcher, listener);

        this.releaseVersion = getParameterValueAssertNotBlank(params, PARAM_RELEASE_VERSION);
        this.releaseNextDevelopmentVersion = getParameterValueAssertNotBlank(params, PARAM_RELEASE_NEXT_DEVELOPMENT_VERSION);
        this.nextDevelopmentVersion = getParameterValueAssertNotBlank(params, PARAM_NEXT_DEVELOPMENT_VERSION);

        this.releaseBranch = "release/" + this.releaseVersion;
    }

    @Override
    public void beforeMainBuild() throws IOException, InterruptedException {

        // Ensure that there are no modified files in the working directory.
        // TODO Move to base class.
        this.consoleLogger.println("Gitflow: Ensuring clean working/checkout directory");
        this.git.clean();

        // Create a new release branch based on the develop branch.
        this.consoleLogger.println("Gitflow - Start Release: Creating release branch " + this.releaseBranch);
        this.git.checkoutBranch(this.releaseBranch, "origin/" + DEVELOP_BRANCH);

        // Update the version numbers in the project files to the release version.
        this.consoleLogger.println("Gitflow - Start Release: Updating project files to release version " + this.releaseVersion);
        this.addFilesToGitStage(this.buildTypeAction.updateVersion(this.releaseVersion));
        this.git.commit("Gitflow - Start Release: Updated project files to release version " + this.releaseVersion);
    }

    @Override
    public void afterMainBuild() throws IOException, InterruptedException {

        // Push the new release branch to the remote repo.
        this.consoleLogger.println("Gitflow - Start Release: Pushing release branch " + this.releaseBranch);
        this.git.push("origin", "refs/heads/" + this.releaseBranch + ":refs/heads/" + this.releaseBranch);

        // Create a tag for the release version.
        final String tagName = "version/" + this.releaseVersion;
        this.consoleLogger.println("Gitflow - Start Release: Creating release version tag " + tagName);
        this.git.tag(tagName, "Gitflow - Start Release: Created release version tag " + tagName);

        // Push the tag for the release version.
        this.consoleLogger.println("Gitflow - Start Release: Pushing release tag " + tagName);
        this.git.push("origin", "refs/tags/" + tagName + ":refs/tags/" + tagName);

        // Update the project files to the development version for the release fixes.
        this.consoleLogger.println("Gitflow - Start Release: Updating project files to fixes development version " + this.releaseNextDevelopmentVersion);
        this.addFilesToGitStage(this.buildTypeAction.updateVersion(this.releaseNextDevelopmentVersion));
        this.git.commit("Gitflow - Start Release: Updated project files to fixes development version " + this.releaseNextDevelopmentVersion);

        // Push the project files with the development version for the release fixes.
        this.consoleLogger.println("Gitflow - Start Release: Pushing project files with fixes development version " + this.releaseNextDevelopmentVersion);
        this.git.push("origin", "refs/heads/" + this.releaseBranch + ":refs/heads/" + this.releaseBranch);

        // Update the project files in the develop branch to the development version for the next release.
        this.consoleLogger.println("Gitflow - Start Release: Updating project files on " + DEVELOP_BRANCH +
                " branch to next development version " + this.nextDevelopmentVersion);
        this.git.checkoutBranch(DEVELOP_BRANCH, "origin/" + DEVELOP_BRANCH);
        this.addFilesToGitStage(this.buildTypeAction.updateVersion(this.nextDevelopmentVersion));
        this.git.commit("Gitflow - Start Release: Updated project files on " + DEVELOP_BRANCH +
                " branch to next development version " + this.nextDevelopmentVersion);

        // Push the project files in the develop branch with the development version for the next release.
        this.consoleLogger.println("Gitflow - Start Release: Pushing project files on " + DEVELOP_BRANCH +
                " branch with next development version " + this.nextDevelopmentVersion);
        this.git.push("origin", "refs/heads/" + DEVELOP_BRANCH + ":refs/heads/" + DEVELOP_BRANCH);

        // TODO Might configure further branches to merge to.
    }
}