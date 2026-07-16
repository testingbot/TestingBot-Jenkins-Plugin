package testingbot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Pure unit tests (no Jenkins) for the session-id regex and tunnel option tokenizer.
 */
public class TunnelParsingTest {

    private List<String> collect(String text) {
        List<String> out = new ArrayList<>();
        TestingBotReportFactory.collectSessionIDs(text, out);
        return out;
    }

    @Test
    public void findsSingleSessionId() {
        assertThat(collect("TestingBotSessionID=abc123")).containsExactly("abc123");
    }

    @Test
    public void ignoresTrailingWhitespaceAndCarriageReturn() {
        assertThat(collect("before TestingBotSessionID=abc123\r\nafter")).containsExactly("abc123");
    }

    @Test
    public void findsMultipleSessionIds() {
        assertThat(collect("TestingBotSessionID=aaa\nTestingBotSessionID=bbb")).containsExactly("aaa", "bbb");
    }

    @Test
    public void returnsEmptyWhenNoMatch() {
        assertThat(collect("nothing to see here")).isEmpty();
    }

    @Test
    public void isNullSafe() {
        assertThat(collect(null)).isEmpty();
    }

    @Test
    public void tokenizeRespectsQuotes() {
        String[] tokens = TunnelManager.tokenize("-d --proxy \"host with space:8080\" -i myid");
        assertThat(tokens).containsExactly("-d", "--proxy", "host with space:8080", "-i", "myid");
    }

    @Test
    public void tokenizeEmptyString() {
        assertThat(TunnelManager.tokenize("")).isEmpty();
    }
}
