package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.net.AgentSync;
import io.github.metrovoc.residual.net.ProgramSync;
import io.github.metrovoc.residual.net.TerminalAnswer;

/** What the server last told this client about the agent screen and the terminal. */
public final class AgentView {
    public static AgentSync sync;
    /** What the debugger of the program last asked about shows. */
    public static ProgramSync program;
    public static TerminalAnswer answer;
    public static boolean asking;

    private AgentView() {
    }
}
