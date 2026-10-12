package com.dogsbay.dogsbayaieditor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where the editor/right-sidebar divider lands on the way up, and what is worth
 * remembering on the way down.
 */
class RightPanelWidthTest {

	private static final int NONE = -1;

	@Test
	@DisplayName("a split that has not been laid out yet is left alone")
	void midLayoutSplitIsLeftAlone() {
		assertThat(RightPanelWidth.dividerFor(0, 6, NONE)).isEqualTo(-1);
		assertThat(RightPanelWidth.dividerFor(120, 6, NONE)).isEqualTo(-1);
	}

	@Test
	@DisplayName("the panel opens wide enough to read")
	void opensWideEnoughToRead() {
		assertThat(RightPanelWidth.widthFor(1845, NONE)).isEqualTo(RightPanelWidth.DEFAULT_WIDTH);
	}

	@Test
	@DisplayName("on a narrow window the panel takes a third, not a fixed width")
	void narrowWindowGetsAThird() {
		assertThat(RightPanelWidth.widthFor(900, NONE)).isEqualTo(300);
	}

	@Test
	@DisplayName("a width dragged in a previous run wins")
	void savedWidthWins() {
		assertThat(RightPanelWidth.widthFor(1845, 700)).isEqualTo(700);
		assertThat(RightPanelWidth.dividerFor(1845, 6, 700)).isEqualTo(1845 - 6 - 700);
	}

	@Test
	@DisplayName("a remembered width too narrow to read is widened, not obeyed")
	void anUnreadableSavedWidthIsWidened() {
		// The state this got stuck in: a sliver, saved on exit, restored forever.
		assertThat(RightPanelWidth.widthFor(1845, 60)).isEqualTo(RightPanelWidth.MINIMUM_WIDTH);
	}

	@Test
	@DisplayName("a remembered width never takes more than half the window")
	void aSavedWidthCannotSwallowTheEditor() {
		assertThat(RightPanelWidth.widthFor(1000, 900)).isEqualTo(500);
	}

	@Test
	@DisplayName("the panel never opens as an unreadable sliver, at any window size")
	void neverASliver() {
		for (int width = RightPanelWidth.LAID_OUT_WIDTH; width < 4000; width += 37) {
			int panel = width - RightPanelWidth.dividerFor(width, 6, NONE) - 6;

			assertThat(panel).as("panel width at split width " + width)
					.isGreaterThanOrEqualTo(RightPanelWidth.MINIMUM_WIDTH);
		}
	}

	@Test
	@DisplayName("what is saved is the panel's width, so a resized window keeps it")
	void savingRecordsTheWidth() {
		assertThat(RightPanelWidth.widthToSave(1845, 6, 1379)).isEqualTo(460);
	}

	@Test
	@DisplayName("a width saved in one window restores the same width in another")
	void aSavedWidthSurvivesAResize() {
		int saved = RightPanelWidth.widthToSave(1845, 6, 1379);

		// The same panel, in a window 500px narrower.
		int divider = RightPanelWidth.dividerFor(1345, 6, saved);

		assertThat(1345 - divider - 6).isEqualTo(460);
	}

	@Test
	@DisplayName("a collapsed panel is not remembered")
	void aCollapsedPanelIsNotRemembered() {
		assertThat(RightPanelWidth.widthToSave(1845, 6, 1845)).isEqualTo(-1);
		assertThat(RightPanelWidth.widthToSave(1845, 6, 1800)).isEqualTo(-1);
		assertThat(RightPanelWidth.widthToSave(1845, 6, 0)).isEqualTo(-1);
	}

	@Test
	@DisplayName("nothing is remembered from a window that never laid out")
	void nothingIsSavedBeforeLayout() {
		assertThat(RightPanelWidth.widthToSave(0, 6, 0)).isEqualTo(-1);
	}
}
