package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.net.ProgramSync;
import java.util.List;

/** What a running program lets its debugger see. */
public interface ProgramView {
    List<ProgramSync.Value> seen();

    List<ProgramSync.Value> watched();
}
