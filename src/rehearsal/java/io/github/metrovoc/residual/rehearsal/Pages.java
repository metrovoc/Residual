package io.github.metrovoc.residual.rehearsal;

import com.mojang.logging.LogUtils;
import guideme.Guide;
import guideme.Guides;
import guideme.color.SymbolicColor;
import guideme.compiler.PageCompiler;
import guideme.compiler.ParsedGuidePage;
import guideme.document.block.LytDocument;
import guideme.document.block.LytVisitor;
import guideme.document.flow.LytFlowContent;
import guideme.document.flow.LytFlowSpan;
import io.github.metrovoc.residual.Residual;
import org.slf4j.Logger;

/** Compiles every page of the field notes in the game's language and counts what comes out as a page's red error text. */
final class Pages {
    private static final Logger LOG = LogUtils.getLogger();

    private Pages() {
    }

    static int errors() {
        Guide guide = Guides.getById(Residual.GUIDE);
        int[] errors = {0};
        for (ParsedGuidePage parsed : guide.getPages()) {
            LytDocument document = PageCompiler.compile(guide, guide.getExtensions(), parsed).document();
            // a page that does not parse is one heading and the parser's complaint, in plain text
            if (document.getTextContent().startsWith("PARSING ERROR")) {
                errors[0]++;
                LOG.error("[rehearsal] page {} ({}) does not parse: {}", parsed.getId(), parsed.getLanguage(), document.getTextContent());
            }
            document.visit(new LytVisitor() {
                @Override
                public Result beforeFlowContent(LytFlowContent content) {
                    if (content instanceof LytFlowSpan && content.getStyle().color() == SymbolicColor.ERROR_TEXT) {
                        errors[0]++;
                        LOG.error("[rehearsal] an error on page {} ({})", parsed.getId(), parsed.getLanguage());
                    }
                    return Result.CONTINUE;
                }
            }, true);
        }
        LOG.info("[rehearsal] {} pages compiled, {} errors", guide.getPages().size(), errors[0]);
        return errors[0];
    }
}
