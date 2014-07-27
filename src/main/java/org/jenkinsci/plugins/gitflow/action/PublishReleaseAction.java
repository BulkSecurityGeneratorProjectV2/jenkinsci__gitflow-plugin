package org.jenkinsci.plugins.gitflow.action;

import static hudson.model.Result.SUCCESS;
import static org.eclipse.jgit.api.MergeCommand.FastForwardMode.NO_FF;
import static org.jenkinsci.plugins.gitclient.MergeCommand.Strategy.RECURSIVE;
import static org.jenkinsci.plugins.gitflow.cause.PublishReleaseCause.IncludedAction.FINISH_RELEASE;
import static org.jenkinsci.plugins.gitflow.cause.PublishReleaseCause.IncludedAction.NONE;
import static org.jenkinsci.plugins.gitflow.cause.PublishReleaseCause.IncludedAction.START_HOTFIX;
import static org.jenkinsci.plugins.gitflow.gitclient.merge.GenericMergeCommand.StrategyOption.OURS;
import static org.jenkinsci.plugins.gitflow.gitclient.merge.GenericMergeCommand.StrategyOption.THEIRS;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.gitflow.GitflowBuildWrapper;
import org.jenkinsci.plugins.gitflow.cause.PublishReleaseCause;
import org.jenkinsci.plugins.gitflow.data.RemoteBranch;
import org.jenkinsci.plugins.gitflow.gitclient.GitClientDelegate;
import org.jenkinsci.plugins.gitflow.gitclient.merge.GenericMergeCommand.StrategyOption;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildData;

/**
 * This class executes the required steps for the Gitflow action <i>Publish Release</i>.
 *
 * @param <B> the build in progress.
 * @author Marc Rohlfs, Silpion IT-Solutions GmbH - rohlfs@silpion.de
 */
public class PublishReleaseAction<B extends AbstractBuild<?, ?>> extends AbstractGitflowAction<B, PublishReleaseCause> {

    private static final String ACTION_NAME = "Publish Release";
    private static final String MSG_PREFIX = "Gitflow - " + ACTION_NAME + ": ";

    private static final MessageFormat MSG_PATTERN_CHECKOUT_BRANCH = new MessageFormat("Gitflow - {0}: Checked out branch {1}");
    private static final MessageFormat MSG_PATTERN_MERGED_LAST_FIXES_RELEASE = new MessageFormat("Gitflow - {0}: Merged last fixes release {1} to branch {2}");

    /**
     * Initialises a new <i>Publish Release</i> action.
     *
     * @param build the <i>Publish Release</i> build that is in progress.
     * @param launcher can be used to launch processes for this build - even if the build runs remotely.
     * @param listener can be used to send any message.
     * @param git the Git client used to execute commands for the Gitflow actions.
     * @param gitflowCause the cause for the new action.
     * @throws IOException if an error occurs that causes/should cause the build to fail.
     * @throws InterruptedException if the build is interrupted during execution.
     */
    public <BC extends B> PublishReleaseAction(final BC build, final Launcher launcher, final BuildListener listener, final GitClientDelegate git, final PublishReleaseCause gitflowCause)
            throws IOException, InterruptedException {
        super(build, launcher, listener, git, gitflowCause);
    }

    /** {@inheritDoc} */
    @Override
    protected String getConsoleMessagePrefix() {
        return MSG_PREFIX;
    }

    /** {@inheritDoc} */
    @Override
    protected String getActionName() {
        return ACTION_NAME;
    }

    /** {@inheritDoc} */
    @Override
    protected void beforeMainBuildInternal() throws IOException, InterruptedException {

        // Merge the last fixes release to the master branch.
        final GitflowBuildWrapper.DescriptorImpl buildWrapperDescriptor = getBuildWrapperDescriptor();
        final String masterBranch = buildWrapperDescriptor.getMasterBranch();
        this.mergeLastFixesRelease(masterBranch, THEIRS);

        // Record the version that have been merged to the master branch.
        final RemoteBranch remoteBranchMaster = this.gitflowPluginData.getOrAddRemoteBranch("origin", masterBranch);
        remoteBranchMaster.setLastBuildResult(SUCCESS);
        final String lastFixesReleaseVersion = this.gitflowCause.getLastFixesReleaseVersion();
        remoteBranchMaster.setLastBuildVersion(lastFixesReleaseVersion);
        remoteBranchMaster.setLastReleaseVersion(lastFixesReleaseVersion);
        remoteBranchMaster.setLastReleaseVersionCommit(ObjectId.fromString(this.gitflowCause.getLastFixesReleaseCommit()));

        // Set the build data with the merge commit on the master branch, so that it won't be scheduled for a new build.
        // Otherwise Jenkins might try to rebuild an already existing release and deploy it to the (Maven) repository manager.
        final ObjectId masterMergeCommit = this.git.getHeadRev(this.git.getRemoteUrl("origin"), masterBranch);
        final String remoteBranchNameMaster = remoteBranchMaster.toString();
        final List<Branch> branches = Collections.singletonList(new Branch(remoteBranchNameMaster, masterMergeCommit));
        final Build masterBuild = new Build(new Revision(masterMergeCommit, branches), this.build.getNumber(), SUCCESS);
        this.build.getAction(BuildData.class).getBuildsByBranchName().put(remoteBranchNameMaster, masterBuild);

        // Merge the last fixes release to the develop branch (if intended).
        // TODO Only offer merge if there have not been commits after the last snapshot version commit.
        final boolean mergeToDevelop = this.gitflowCause.isMergeToDevelop();
        if (mergeToDevelop) {
            this.mergeLastFixesRelease(buildWrapperDescriptor.getDevelopBranch(), OURS);
        }

        // Execute the included action(s).
        final PublishReleaseCause.IncludedAction includedAction = this.gitflowCause.getIncludedAction();
        if (includedAction != NONE) {
            final String releaseBranch = this.gitflowCause.getReleaseBranch();

            // Include action(s).
            if (includedAction == START_HOTFIX) {
                final String releaseBranchPrefix = buildWrapperDescriptor.getReleaseBranchPrefix();
                final String hotfixBranchPrefix = buildWrapperDescriptor.getHotfixBranchPrefix();
                final String hotfixBranch = hotfixBranchPrefix + StringUtils.removeStart(releaseBranch, releaseBranchPrefix);
                this.createBranch(hotfixBranch, releaseBranch);
                this.deleteBranch(releaseBranch);
            } else if (includedAction == FINISH_RELEASE && mergeToDevelop) {
                this.deleteBranch(releaseBranch);
            }
        }

        // Abort the job, because there's no need to execute the main build.
        this.omitMainBuild();
    }

    private void mergeLastFixesRelease(final String targetBranch, final StrategyOption recursiveMergeStrategyOption) throws InterruptedException {

        // Checkout the target branch.
        final ObjectId targetBranchRev = this.git.getHeadRev(this.git.getRemoteUrl("origin"), targetBranch);
        this.git.checkoutBranch(targetBranch, targetBranchRev.getName());
        this.consoleLogger.println(formatPattern(MSG_PATTERN_CHECKOUT_BRANCH, ACTION_NAME, targetBranch));

        // Merge the last fixes release (from the release branch) to the target branch.
        final ObjectId lastFixesReleaseCommit = ObjectId.fromString(this.gitflowCause.getLastFixesReleaseCommit());
        this.git.merge(lastFixesReleaseCommit, NO_FF, RECURSIVE, recursiveMergeStrategyOption, false);
        final String lastFixesReleaseVersion = this.gitflowCause.getLastFixesReleaseVersion();
        final String msgMergedLastFixesRelease = formatPattern(MSG_PATTERN_MERGED_LAST_FIXES_RELEASE, ACTION_NAME, lastFixesReleaseVersion, targetBranch);
        this.git.commit(msgMergedLastFixesRelease);
        this.consoleLogger.println(msgMergedLastFixesRelease);

        // Push the master branch with the new merge commit.
        this.git.push().to(this.remoteUrl).ref("refs/heads/" + targetBranch + ":refs/heads/" + targetBranch).execute();
    }

    /** {@inheritDoc} */
    @Override
    protected void afterMainBuildInternal() throws IOException, InterruptedException {
        // Nothing to do.
    }
}
