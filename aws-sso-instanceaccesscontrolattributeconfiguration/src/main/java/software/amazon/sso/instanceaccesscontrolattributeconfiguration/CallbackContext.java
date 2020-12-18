package software.amazon.sso.instanceaccesscontrolattributeconfiguration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import software.amazon.cloudformation.proxy.StdCallbackContext;

/**
 * Context that will be passed to every handler
 */
@lombok.Getter
@lombok.Setter
@lombok.ToString
@lombok.EqualsAndHashCode(callSuper = true)
public class CallbackContext extends StdCallbackContext {
    public final static int RETRY_ATTEMPTS = 5;
    private int retryAttempts;

    public CallbackContext(int retryAttempts) {
        this.retryAttempts = retryAttempts;
    }

    public CallbackContext() {
        this(RETRY_ATTEMPTS);
    }

    @JsonIgnore
    public void decrementRetryAttempts() {
        retryAttempts--;
    }
}
