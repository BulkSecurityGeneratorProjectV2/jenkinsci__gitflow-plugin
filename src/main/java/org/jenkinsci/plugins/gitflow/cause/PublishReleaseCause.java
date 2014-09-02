package org.jenkinsci.plugins.gitflow.cause;

import org.jenkinsci.plugins.gitflow.data.RemoteBranch;

/**
 * The {@link hudson.model.Cause Cause} object for the <i>Publish Release</i> action to be executed.
 *
 * @author Marc Rohlfs, Silpion IT-Solutions GmbH - rohlfs@silpion.de
 */
public class PublishReleaseCause extends AbstractReleaseBranchCause {

    private String lastPatchReleaseVersion;
    private String lastPatchReleaseCommit;
    private boolean mergeToDevelop = false;
    private IncludedAction includedAction = IncludedAction.START_HOTFIX;

    /**
     * Creates a cause instance for the <i>Gitflow</i> build.
     *
     * @param releaseBranch the <i>release</i> branch containing base data for the cause.
     */
    public PublishReleaseCause(final RemoteBranch releaseBranch) {
        super(releaseBranch);

        this.lastPatchReleaseVersion = releaseBranch.getLastReleaseVersion();
        this.lastPatchReleaseCommit = releaseBranch.getLastReleaseVersionCommit().getName();
    }

    @Override
    public String getVersionForBadge() {
        return this.lastPatchReleaseVersion;
    }

    public String getLastPatchReleaseVersion() {
        return this.lastPatchReleaseVersion;
    }

    public String getLastPatchReleaseCommit() {
        return this.lastPatchReleaseCommit;
    }

    public boolean isMergeToDevelop() {
        return this.mergeToDevelop;
    }

    public void setMergeToDevelop(final boolean mergeToDevelop) {
        this.mergeToDevelop = mergeToDevelop;
    }

    public IncludedAction getIncludedAction() {
        return this.includedAction;
    }

    public void setIncludedAction(final String includedAction) {
        this.includedAction = IncludedAction.valueOf(includedAction);
    }

    /** The actions that can be included/executed after the main <i>Publish Release</i> action. */
    public static enum IncludedAction {
        NONE,
        FINISH_RELEASE,
        START_HOTFIX
    }
}
