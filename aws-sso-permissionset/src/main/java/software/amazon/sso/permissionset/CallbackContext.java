package software.amazon.sso.permissionset;

import com.fasterxml.jackson.annotation.JsonIgnore;
import software.amazon.cloudformation.proxy.StdCallbackContext;

@lombok.Getter
@lombok.Setter
@lombok.ToString
@lombok.EqualsAndHashCode(callSuper = true)
public class CallbackContext extends StdCallbackContext {
    private boolean tagUpdateds;
    private boolean managedPolicyUpdated;
    private boolean inlinePolicyUpdated;
    private int retryAttempts;
    private boolean handlerInvoked;

    @JsonIgnore
    public void decrementRetryAttempts() {
        retryAttempts--;
    }

    @JsonIgnore
    public void resetRetryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
    }
}
