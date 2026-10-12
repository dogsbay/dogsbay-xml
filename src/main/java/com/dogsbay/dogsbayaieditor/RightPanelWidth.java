package com.dogsbay.dogsbayaieditor;

/**
 * How wide the right sidebar opens, and where that puts the divider.
 *
 * <p>
 * Kept apart from the frame so the arithmetic can be read, and tested, without
 * a window. What is remembered between runs is the panel's <em>width</em>, not
 * the divider's position: a position is measured from the left edge, so it only
 * means the same thing in a window of the same size, and restoring one into a
 * narrower window squeezed the panel to a sliver — which the next clean exit
 * then saved, making it permanent.
 *
 * <p>
 * A width somebody chose by dragging wins, within reason. Failing that the
 * panel opens wide enough to read, and never takes more than a third of the
 * window.
 */
public final class RightPanelWidth {

	/** Width the right panel opens at, when nobody has dragged the divider. */
	public static final int DEFAULT_WIDTH = 460;

	/** Narrower than this and the panel cannot be read, whoever asked for it. */
	public static final int MINIMUM_WIDTH = 220;

	/** Below this the split is still mid-layout and its width means nothing. */
	public static final int LAID_OUT_WIDTH = 600;

	private RightPanelWidth() {
	}

	/** True once the split is wide enough for its width to be worth reading. */
	public static boolean laidOut(int splitWidth) {
		return splitWidth >= LAID_OUT_WIDTH;
	}

	/**
	 * How wide the panel should open.
	 *
	 * @param splitWidth the split pane's current width
	 * @param saved      a width remembered from a previous run, or -1 for none
	 */
	public static int widthFor(int splitWidth, int saved) {
		int wanted = saved > 0 ? saved : Math.min(DEFAULT_WIDTH, splitWidth / 3);
		// Whatever was asked for, the panel stays readable and leaves the editor
		// the larger half.
		return Math.max(MINIMUM_WIDTH, Math.min(wanted, splitWidth / 2));
	}

	/**
	 * Where to put the divider, or -1 to leave it alone because the split has
	 * not been laid out yet.
	 *
	 * @param splitWidth  the split pane's current width
	 * @param dividerSize the width of the divider itself
	 * @param saved       a width remembered from a previous run, or -1 for none
	 */
	public static int dividerFor(int splitWidth, int dividerSize, int saved) {
		if (!laidOut(splitWidth)) {
			return -1;
		}
		return splitWidth - dividerSize - widthFor(splitWidth, saved);
	}

	/**
	 * The width to remember, given where the divider ended up, or -1 when there
	 * is nothing worth remembering (the split was never laid out, or the panel
	 * was collapsed — saving that would reopen it collapsed forever).
	 */
	public static int widthToSave(int splitWidth, int dividerSize, int dividerLocation) {
		if (!laidOut(splitWidth) || dividerLocation <= 0) {
			return -1;
		}
		int width = splitWidth - dividerSize - dividerLocation;
		return width < MINIMUM_WIDTH ? -1 : width;
	}
}
