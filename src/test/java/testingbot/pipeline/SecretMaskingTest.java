package testingbot.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import hudson.model.Run;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * Pure unit tests for the body-scoped {@link SecretMaskingConsoleLogFilter} masking logic.
 */
public class SecretMaskingTest {

    private String filter(String secret, String input) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStream masked = new SecretMaskingConsoleLogFilter(secret).decorateLogger((Run) null, baos);
        masked.write(input.getBytes(StandardCharsets.UTF_8));
        masked.close();
        return baos.toString("UTF-8");
    }

    @Test
    public void masksSecretInLine() throws Exception {
        assertThat(filter("s3cr3t", "token=s3cr3t done\n")).isEqualTo("token=**** done\n");
    }

    @Test
    public void masksMultipleOccurrences() throws Exception {
        assertThat(filter("aa", "aa bb aa\n")).isEqualTo("**** bb ****\n");
    }

    @Test
    public void leavesNonMatchingLinesUnchanged() throws Exception {
        assertThat(filter("s3cr3t", "nothing here\n")).isEqualTo("nothing here\n");
    }

    @Test
    public void nullSecretIsPassthrough() throws Exception {
        assertThat(filter(null, "token=abc\n")).isEqualTo("token=abc\n");
    }
}
