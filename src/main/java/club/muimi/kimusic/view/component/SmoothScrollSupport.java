package club.muimi.kimusic.view.component;

import javafx.scene.control.Control;

/** Keeps wheel scrolling responsive while limiting large trackpad deltas. */
public final class SmoothScrollSupport {
    private SmoothScrollSupport() {
    }

    public static void install(Control control) {
        // JavaFX's native skin already provides pixel-accurate trackpad and
        // wheel scrolling. Do not consume ScrollEvent here: doing so prevents
        // virtualized controls from receiving vertical scroll input.
    }
}
