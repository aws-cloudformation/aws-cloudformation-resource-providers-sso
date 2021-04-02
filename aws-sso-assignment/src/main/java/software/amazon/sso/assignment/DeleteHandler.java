package software.amazon.sso.assignment;

import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.AccountAssignmentOperationStatus;
import software.amazon.awssdk.services.ssoadmin.model.ConflictException;
import software.amazon.awssdk.services.ssoadmin.model.DeleteAccountAssignmentResponse;
import software.amazon.awssdk.services.ssoadmin.model.DescribeAccountAssignmentDeletionStatusResponse;
import software.amazon.awssdk.services.ssoadmin.model.InternalServerException;
import software.amazon.awssdk.services.ssoadmin.model.StatusValues;
import software.amazon.awssdk.services.ssoadmin.model.ThrottlingException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.sso.assignment.actionProxy.AssignmentProxy;

import static software.amazon.sso.assignment.Translator.translateToDescribeDeletionStatusRequest;
import static software.amazon.sso.assignment.Constants.FAILED_WORKFLOW_REQUEST;
import static software.amazon.sso.assignment.Constants.RETRY_ATTEMPTS;
import static software.amazon.sso.assignment.Constants.RETRY_ATTEMPTS_ZERO;

public class DeleteHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<SsoAdminClient> proxyClient,
            final Logger logger) {

        this.logger = logger;

        if (!callbackContext.isHandlerInvoked()) {
            callbackContext.setHandlerInvoked(true);
            callbackContext.setRetryAttempts(RETRY_ATTEMPTS);
        }

        AssignmentProxy assignmentProxy = new AssignmentProxy(proxy, proxyClient, logger);

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
                .then(progress -> proxy.initiate("sso::assignment-delete", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                        .translateToServiceRequest(Translator::translateToDeleteRequest)
                        .makeServiceCall((modelRequest, client) -> {
                            if (!assignmentProxy.checkIfAssignmentAlreadyExist(modelRequest.instanceArn(), modelRequest.targetId(), modelRequest.permissionSetArn(), modelRequest.principalId(), modelRequest.principalTypeAsString())) {
                                throw new CfnNotFoundException(ResourceModel.TYPE_NAME, "Assignment not exist any more. Can't process deletion.");
                            }
                            logger.log("Assignment pre-existence check complete.");

                            DeleteAccountAssignmentResponse response = proxy.injectCredentialsAndInvokeV2(modelRequest, client.client()::deleteAccountAssignment);
                            logger.log(String.format("%s is in deleting process.", ResourceModel.TYPE_NAME));
                            return response;
                        })
                        .stabilize((modelRequest, response, client, model, context) -> {
                            String statusTrackId = response.accountAssignmentDeletionStatus().requestId();
                            DescribeAccountAssignmentDeletionStatusResponse checkStatusResponse = proxy.injectCredentialsAndInvokeV2(translateToDescribeDeletionStatusRequest(model.getInstanceArn(), statusTrackId),
                                    proxyClient.client()::describeAccountAssignmentDeletionStatus);
                            AccountAssignmentOperationStatus deletionStatus = checkStatusResponse.accountAssignmentDeletionStatus();
                            if (deletionStatus.status().equals(StatusValues.SUCCEEDED)) {
                                logger.log(String.format("%s [%s] has been stabilized.", ResourceModel.TYPE_NAME, model.getPrimaryIdentifier()));
                                return true;
                            } else if (deletionStatus.status().equals(StatusValues.FAILED)) {
                                throw new CfnGeneralServiceException(String.format(FAILED_WORKFLOW_REQUEST, statusTrackId, deletionStatus.failureReason()));
                            }
                            return false;
                        })
                        .handleError((awsRequest, exception, client, resourceModel, context) -> {
                            if (exception instanceof ConflictException || exception instanceof ThrottlingException) {
                                return ProgressEvent.defaultInProgressHandler(callbackContext, getRetryTime(exception), resourceModel);
                            } else if (exception instanceof InternalServerException) {
                                if (context.getRetryAttempts() == RETRY_ATTEMPTS_ZERO) {
                                    return ProgressEvent.defaultFailureHandler(exception, mapExceptionToHandlerCode(exception));
                                }
                                context.decrementRetryAttempts();
                                return ProgressEvent.defaultInProgressHandler(callbackContext, getRetryTime(exception), resourceModel);
                            }
                            return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.GeneralServiceException);
                        })
                        .done((createPermissionSetRequest, createPermissionSetResponse, proxyInvocation, model, context) -> ProgressEvent.defaultSuccessHandler(null)));
    }
}
