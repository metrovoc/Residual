package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.net.ApprenticeSync;
import io.github.metrovoc.residual.net.TerminalAnswer;

/** What the server last told this client about the apprentice screen and the terminal. */
public final class ApprenticeView {
    public static ApprenticeSync sync;
    public static TerminalAnswer answer;
    public static boolean asking;

    private ApprenticeView() {
    }
}
