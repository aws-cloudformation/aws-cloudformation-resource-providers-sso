package software.amazon.sso.assignment;

import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.InternalServerException;
import software.amazon.awssdk.services.ssoadmin.model.ThrottlingException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.sso.assignment.actionProxy.AssignmentProxy;

import static software.amazon.sso.assignment.Constants.RETRY_ATTEMPTS;
import static software.amazon.sso.assignment.Constants.RETRY_ATTEMPTS_ZERO;

public class ReadHandler extends BaseHandlerStd {
    private Logger logger;
    private static final String THROTTLE_MESSAGE = "Read request got throttled. Please add DependsOn attribute if you have large number of AWS SSO owned resources";
    private static final String ISE_MESSAGE = "Something went wrong while performing READ call";
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<SsoAdminClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        AssignmentProxy assignmentProxy = new AssignmentProxy(proxy, proxyClient, logger);

        ResourceModel model = request.getDesiredResourceState();

        int retryThrottlingAttempts = Integer.valueOf(RETRY_ATTEMPTS);
        int retryISEAttempts = Integer.valueOf(RETRY_ATTEMPTS);
        while (retryISEAttempts > RETRY_ATTEMPTS_ZERO && retryThrottlingAttempts > RETRY_ATTEMPTS_ZERO) {
            try {
                if (!assignmentProxy.checkIfAssignmentAlreadyExist(model.getInstanceArn(), model.getTargetId(), model.getPermissionSetArn(), model.getPrincipalId(), model.getPrincipalType())) {
                    return ProgressEvent.defaultFailureHandler(new CfnNotFoundException(ResourceModel.TYPE_NAME, "Assignment not exists for given entity."), HandlerErrorCode.NotFound);
                }
                logger.log(String.format("%s has successfully been read.", ResourceModel.TYPE_NAME));
                break;
            } catch (ThrottlingException te) {
                retryThrottlingAttempts = decrementAndWait(retryThrottlingAttempts);
                continue;
            } catch (InternalServerException ise) {
                retryISEAttempts = decrementAndWait(retryISEAttempts);
                continue;
            }
        }
        if(retryISEAttempts > RETRY_ATTEMPTS_ZERO && retryThrottlingAttempts > RETRY_ATTEMPTS_ZERO) {
            return ProgressEvent.success(model, callbackContext);
        } else if (retryThrottlingAttempts == RETRY_ATTEMPTS_ZERO) {
            return ProgressEvent.defaultFailureHandler(ThrottlingException.builder().message(THROTTLE_MESSAGE).build(), HandlerErrorCode.Throttling);
        } else {
            return ProgressEvent.defaultFailureHandler(InternalServerException.builder().message(ISE_MESSAGE).build(), HandlerErrorCode.InternalFailure);
        }
    }

    /**
     * Decrement context and wait for some time
     * Should wait between 1 and 5 second in case got throttled.
     */
    private int decrementAndWait(int attempts) {
        int timeToWait = SECURE_RANDOM.ints(5000, 10000).findFirst().getAsInt();
        try {
            Thread.sleep(timeToWait);
        } catch (InterruptedException e) {
            // Ignore if fail sleep
        }
        return attempts - 1;
    }
}
