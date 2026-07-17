package testingbot;

import static org.assertj.core.api.Assertions.assertThat;

import com.testingbot.models.TestingbotUser;
import hudson.util.FormValidation;
import org.junit.Test;

/**
 * Covers {@link TestingBotCredentials.DescriptorImpl#verificationResult} — the Test Connection
 * result branches — without touching the network or the Jenkins permission check: a null user
 * fails, and any authenticated user succeeds even when the email, first name or plan are absent.
 */
public class TestingBotCredentialsVerificationTest {

    @Test
    public void nullUserFailsVerification() {
        FormValidation r = TestingBotCredentials.DescriptorImpl.verificationResult(null);
        assertThat(r.kind).isEqualTo(FormValidation.Kind.ERROR);
        assertThat(r.getMessage()).contains("Could not verify");
    }

    @Test
    public void userWithEmailSucceeds() {
        TestingbotUser user = new TestingbotUser();
        user.setEmail("jane@example.com");
        user.setFirstName("Jane");
        user.setPlan("Enterprise Plan");
        FormValidation r = TestingBotCredentials.DescriptorImpl.verificationResult(user);
        assertThat(r.kind).isEqualTo(FormValidation.Kind.OK);
        assertThat(r.getMessage())
                .contains("Connection successful")
                .contains("jane@example.com")
                .contains("Enterprise Plan plan");
    }

    @Test
    public void userWithoutEmailFallsBackToFirstName() {
        TestingbotUser user = new TestingbotUser();
        user.setFirstName("demo");
        user.setPlan("Free");
        FormValidation r = TestingBotCredentials.DescriptorImpl.verificationResult(user);
        assertThat(r.kind).isEqualTo(FormValidation.Kind.OK);
        assertThat(r.getMessage())
                .contains("signed in as demo")
                .contains("Free plan")
                .doesNotContain("@");
    }

    @Test
    public void userMissingIdentityAndPlanStillSucceeds() {
        TestingbotUser user = new TestingbotUser(); // no email, first name or plan
        FormValidation r = TestingBotCredentials.DescriptorImpl.verificationResult(user);
        assertThat(r.kind).isEqualTo(FormValidation.Kind.OK);
        assertThat(r.getMessage()).contains("Connection successful");
        assertThat(r.getMessage()).doesNotContain("signed in as");
        assertThat(r.getMessage()).doesNotContain(" plan)");
    }
}
