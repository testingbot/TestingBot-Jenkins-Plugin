package testingbot.pipeline;

import hudson.console.ConsoleLogFilter;
import hudson.console.LineTransformationOutputStream;
import hudson.model.Run;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Body-scoped console log filter that masks a known secret in the output of a Pipeline block.
 *
 * <p>The global {@link testingbot.TestingBotSecretConsoleLogFilter} covers freestyle builds; this
 * filter is merged into the body context of the {@code testingbot} / {@code testingbotTunnel} steps
 * so that {@code TESTINGBOT_SECRET} / {@code TB_SECRET} are masked in step (e.g. {@code sh}) output
 * inside the block.</p>
 */
class SecretMaskingConsoleLogFilter extends ConsoleLogFilter implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String MASK = "****";

    private final String secret;

    SecretMaskingConsoleLogFilter(String secret) {
        this.secret = secret;
    }

    @Override
    public OutputStream decorateLogger(Run build, OutputStream logger) {
        if (secret == null || secret.isEmpty()) {
            return logger;
        }
        final Charset charset = build != null ? build.getCharset() : StandardCharsets.UTF_8;
        return new LineTransformationOutputStream() {
            @Override
            protected void eol(byte[] b, int len) throws IOException {
                String line = new String(b, 0, len, charset);
                if (line.contains(secret)) {
                    logger.write(line.replace(secret, MASK).getBytes(charset));
                } else {
                    logger.write(b, 0, len);
                }
            }

            @Override
            public void flush() throws IOException {
                logger.flush();
            }

            @Override
            public void close() throws IOException {
                super.close();
                logger.close();
            }
        };
    }
}
