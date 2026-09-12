/*
 * Copyright (C) 2002-2026 DogsBay Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dogsbay.dogsbayaieditor.git;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Merging and deleting local branches, kept apart from the menus that call it
 * so both can be tested against a real repository without a window.
 *
 * <p>
 * Each operation is in two halves: a blocker check that says in plain words why
 * it cannot proceed, and the operation itself. The check exists so the editor
 * can refuse before touching the repository — merging over uncommitted work, or
 * deleting a branch whose commits are nowhere else, is how people lose things.
 */
public final class BranchOps {

	/** How a merge ended. */
	public enum MergeOutcome {
		/** The branch was already contained in this one; nothing to do. */
		ALREADY_UP_TO_DATE,
		/** This branch simply moved forward to the other one. */
		FAST_FORWARD,
		/** The histories were joined by a merge commit. */
		MERGED,
		/** The merge stopped with files in conflict; the working tree holds the markers. */
		CONFLICTS,
		/** Git refused the merge and changed nothing. */
		FAILED
	}

	/**
	 * What a merge did.
	 *
	 * @param conflicts the paths left in conflict, empty unless {@link MergeOutcome#CONFLICTS}
	 * @param detail    JGit's own word for the result, for the failure cases
	 */
	public record MergeReport(MergeOutcome outcome, String branch, String into, List<String> conflicts,
			String detail) {
		public MergeReport {
			conflicts = List.copyOf(conflicts);
		}
	}

	private BranchOps() {
	}

	/** The local branches, in the order git lists them, with no {@code refs/heads/}. */
	public static List<String> localBranches(Git git) throws GitAPIException {
		List<String> names = new ArrayList<>();
		for (Ref ref : git.branchList().call()) {
			names.add(shortName(ref.getName()));
		}
		return names;
	}

	private static String shortName(String ref) {
		return ref.replaceFirst("^refs/heads/", "");
	}

	/**
	 * The branch by that name, or null.
	 *
	 * <p>
	 * Fully qualified deliberately: {@code findRef} walks a search path that
	 * reaches {@code refs/tags/} before {@code refs/heads/}, so a repository
	 * with a tag and a branch of the same name would merge the tag while
	 * reporting the branch.
	 */
	private static Ref branchRef(Git git, String branch) throws IOException {
		return git.getRepository().findRef(Constants.R_HEADS + branch);
	}

	/**
	 * Why {@code branch} cannot be merged into the current one, or null when it
	 * can. Checked before anything is touched.
	 */
	public static String mergeBlocker(Git git, String branch) throws GitAPIException, IOException {
		String current = git.getRepository().getBranch();
		if (branch == null || branch.isBlank()) {
			return "No branch was chosen.";
		}
		if (branch.equals(current)) {
			return "'" + branch + "' is the branch you are on; a branch cannot be merged into itself.";
		}
		if (branchRef(git, branch) == null) {
			return "There is no branch called '" + branch + "'.";
		}
		// hasUncommittedChanges, not isClean: isClean also counts untracked
		// files, and refusing over an untracked note with "commit or stash them
		// first" is advice that cannot work — stash leaves untracked files alone.
		// Git itself merges happily in that state.
		if (git.status().call().hasUncommittedChanges()) {
			return "There are uncommitted changes in the working tree. Commit or stash them first: "
					+ "a merge rewrites the files it brings in, and would take those changes with it.";
		}
		return null;
	}

	/**
	 * Merge {@code branch} into the branch currently checked out. The caller is
	 * expected to have run {@link #mergeBlocker} first.
	 */
	public static MergeReport merge(Git git, String branch) throws GitAPIException, IOException {
		String into = git.getRepository().getBranch();
		Ref ref = branchRef(git, branch);
		MergeResult result = git.merge()
				.include(ref)
				.setMessage("Merge branch '" + branch + "' into " + into)
				.call();

		MergeResult.MergeStatus status = result.getMergeStatus();
		List<String> conflicts = result.getConflicts() == null ? List.of()
				: new ArrayList<>(result.getConflicts().keySet());
		MergeOutcome outcome = switch (status) {
			case ALREADY_UP_TO_DATE -> MergeOutcome.ALREADY_UP_TO_DATE;
			case FAST_FORWARD, FAST_FORWARD_SQUASHED -> MergeOutcome.FAST_FORWARD;
			case MERGED, MERGED_SQUASHED, MERGED_NOT_COMMITTED, MERGED_SQUASHED_NOT_COMMITTED ->
					MergeOutcome.MERGED;
			case CONFLICTING, CHECKOUT_CONFLICT -> MergeOutcome.CONFLICTS;
			default -> MergeOutcome.FAILED;
		};
		return new MergeReport(outcome, branch, into, conflicts, status.toString());
	}

	/**
	 * True when every commit on {@code branch} is already in the current branch,
	 * so deleting it loses nothing.
	 */
	public static boolean isMerged(Git git, String branch) throws IOException {
		Ref ref = branchRef(git, branch);
		Ref head = git.getRepository().findRef("HEAD");
		if (ref == null || head == null || head.getObjectId() == null) {
			return false;
		}
		try (RevWalk walk = new RevWalk(git.getRepository())) {
			RevCommit tip = walk.parseCommit(ref.getObjectId());
			RevCommit current = walk.parseCommit(head.getObjectId());
			return walk.isMergedInto(tip, current);
		}
	}

	/**
	 * Why {@code branch} cannot be deleted, or null when it can. Being unmerged
	 * is not a blocker — it is a warning the caller should put to the user,
	 * because deleting then needs {@code force}.
	 */
	public static String deleteBlocker(Git git, String branch) throws IOException {
		if (branch == null || branch.isBlank()) {
			return "No branch was chosen.";
		}
		if (branch.equals(git.getRepository().getBranch())) {
			return "'" + branch + "' is the branch you are on. Switch to another branch first.";
		}
		if (branchRef(git, branch) == null) {
			return "There is no branch called '" + branch + "'.";
		}
		return null;
	}

	/**
	 * Delete {@code branch}. Without {@code force}, git refuses a branch whose
	 * commits are not already somewhere else.
	 */
	public static void delete(Git git, String branch, boolean force) throws GitAPIException {
		git.branchDelete().setBranchNames(branch).setForce(force).call();
	}
}
