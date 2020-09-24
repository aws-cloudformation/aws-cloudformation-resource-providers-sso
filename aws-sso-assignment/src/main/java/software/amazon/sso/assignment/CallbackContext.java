package software.amazon.sso.assignment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import software.amazon.cloudformation.proxy.StdCallbackContext;

@lombok.Getter
@lombok.Setter
@lombok.ToString
@lombok.EqualsAndHashCode(callSuper = true)
public class CallbackContext extends StdCallbackContext {

    private int retryAttempts;
    private boolean handlerInvoked;

    @JsonIgnore
    public void decrementRetryAttempts() {
        retryAttempts--;
    }
}
