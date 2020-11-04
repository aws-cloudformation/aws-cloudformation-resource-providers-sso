package software.amazon.sso.assignment;

import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.ThrottlingException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.sso.assignment.actionProxy.AssignmentProxy;

public class ReadHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<SsoAdminClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        AssignmentProxy assignmentProxy = new AssignmentProxy(proxy, proxyClient, logger);

        ResourceModel model = request.getDesiredResourceState();
        try {
            if (!assignmentProxy.checkIfAssignmentAlreadyExist(model.getInstanceArn(), model.getTargetId(), model.getPermissionSetArn(), model.getPrincipalId(), model.getPrincipalType())) {
                return ProgressEvent.defaultFailureHandler(new CfnNotFoundException(ResourceModel.TYPE_NAME, "Assignment not exists for given entity."), HandlerErrorCode.NotFound);
            }
            logger.log(String.format("%s has successfully been read.", ResourceModel.TYPE_NAME));
            return ProgressEvent.success(model, callbackContext);
        } catch (ThrottlingException e) {
            if (callbackContext.getRetryAttempts() == 0) {
                throw e;
            }
            callbackContext.decrementRetryAttempts();
            return ProgressEvent.defaultInProgressHandler(callbackContext, 5, model);
        }
    }
}
