package testingbot;

import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.console.LineTransformationOutputStream;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;

/**
 * Masks the decrypted TestingBot secret in build console logs so that a
 * {@code sh 'env'}, crash dump or similar cannot leak it in clear text.
 *
 * <p>Applies to any build/run that carries a {@link TestingBotBuildAction}
 * (added by the freestyle wrapper and both pipeline steps). The secret is
 * resolved lazily per line, because the credential action is attached slightly
 * after the log stream is opened.</p>
 */
@Extension
public class TestingBotSecretConsoleLogFilter extends ConsoleLogFilter implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String MASK = "****";

    @Override
    public OutputStream decorateLogger(Run build, OutputStream logger) {
        if (build == null) {
            return logger;
        }
        return new MaskingOutputStream(build, logger);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public OutputStream decorateLogger(AbstractBuild build, OutputStream logger) {
        return decorateLogger((Run) build, logger);
    }

    private static final class MaskingOutputStream extends LineTransformationOutputStream {

        private final Run<?, ?> run;
        private final OutputStream delegate;
        private String secret;
        private boolean resolved;

        MaskingOutputStream(Run<?, ?> run, OutputStream delegate) {
            this.run = run;
            this.delegate = delegate;
        }

        @Override
        protected void eol(byte[] b, int len) throws IOException {
            if (!resolved) {
                TestingBotBuildAction action = run.getAction(TestingBotBuildAction.class);
                if (action != null && action.getCredentials() != null) {
                    String s = action.getCredentials().getDecryptedSecret();
                    if (s != null && !s.isEmpty()) {
                        secret = s;
                        resolved = true;
                    }
                }
            }

            if (secret == null) {
                delegate.write(b, 0, len);
                return;
            }

            Charset cs = run.getCharset();
            String line = new String(b, 0, len, cs);
            if (line.contains(secret)) {
                delegate.write(line.replace(secret, MASK).getBytes(cs));
            } else {
                delegate.write(b, 0, len);
            }
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            super.close();
            delegate.close();
        }
    }
}
