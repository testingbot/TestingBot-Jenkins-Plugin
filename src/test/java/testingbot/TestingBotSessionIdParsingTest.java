package testingbot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Verifies the {@code TestingBotSessionID=} token parser accepts the full session
 * id, including the hyphens present in modern WebDriver session ids. A truncated id
 * would 404 against the TestingBot REST API and break every embedded report.
 */
public class TestingBotSessionIdParsingTest {

    @Test
    public void capturesHyphenatedSessionId() {
        String sid = "b0dbacacfa63-342d529ca760-2f981532f469-178429199039-61532530";
        List<String> found = new ArrayList<>();
        TestingBotReportFactory.collectSessionIDs("TestingBotSessionID=" + sid, found);
        assertThat(found).containsExactly(sid);
    }

    @Test
    public void capturesPlainHexSessionId() {
        List<String> found = new ArrayList<>();
        TestingBotReportFactory.collectSessionIDs("log TestingBotSessionID=abc123DEF more", found);
        assertThat(found).containsExactly("abc123DEF");
    }

    @Test
    public void capturesMultipleSessionIds() {
        List<String> found = new ArrayList<>();
        TestingBotReportFactory.collectSessionIDs(
                "TestingBotSessionID=aaa-111\nTestingBotSessionID=bbb-222", found);
        assertThat(found).containsExactly("aaa-111", "bbb-222");
    }

    @Test
    public void ignoresTextWithoutToken() {
        List<String> found = new ArrayList<>();
        TestingBotReportFactory.collectSessionIDs("no session here", found);
        assertThat(found).isEmpty();
    }
}
