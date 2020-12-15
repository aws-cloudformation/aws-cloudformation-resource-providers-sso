package software.amazon.sso.assignment;

import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.AccountAssignmentOperationStatus;
import software.amazon.awssdk.services.ssoadmin.model.ConflictException;
import software.amazon.awssdk.services.ssoadmin.model.CreateAccountAssignmentResponse;
import software.amazon.awssdk.services.ssoadmin.model.DescribeAccountAssignmentCreationStatusResponse;
import software.amazon.awssdk.services.ssoadmin.model.InternalServerException;
import software.amazon.awssdk.services.ssoadmin.model.StatusValues;
import software.amazon.awssdk.services.ssoadmin.model.ThrottlingException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.sso.assignment.actionProxy.AssignmentProxy;

import static software.amazon.sso.assignment.Translator.translateToDescribeCreationStatusRequest;
import static software.amazon.sso.assignment.Constants.FAILED_WORKFLOW_REQUEST;
import static software.amazon.sso.assignment.Constants.RETRY_ATTEMPTS;
import static software.amazon.sso.assignment.Constants.RETRY_ATTEMPTS_ZERO;


public class CreateHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<SsoAdminClient> proxyClient,
            final Logger logger) {

        this.logger = logger;

        AssignmentProxy assignmentProxy = new AssignmentProxy(proxy, proxyClient, logger);

        if (!callbackContext.isHandlerInvoked()) {
            callbackContext.setHandlerInvoked(true);
            callbackContext.setRetryAttempts(RETRY_ATTEMPTS);
        }

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
                .then(progress ->
                        proxy.initiate("sso::assignment-create", proxyClient,progress.getResourceModel(), progress.getCallbackContext())
                                .translateToServiceRequest(Translator::translateToCreateRequest)
                                .makeServiceCall((createRequest, client) -> {
                                    if (assignmentProxy.checkIfAssignmentAlreadyExist(createRequest.instanceArn(), createRequest.targetId(), createRequest.permissionSetArn(), createRequest.principalId(), createRequest.principalTypeAsString())) {
                                        throw new CfnAlreadyExistsException(ResourceModel.TYPE_NAME, "Assignment already exists. Can't process creation.");
                                    }
                                    logger.log("Assignment pre-existence check complete.");

                                    CreateAccountAssignmentResponse response = proxy.injectCredentialsAndInvokeV2(createRequest, proxyClient.client()::createAccountAssignment);

                                    logger.log(String.format("%s is in creating process.", ResourceModel.TYPE_NAME));
                                    return response;
                                })
                                .stabilize((modelRequest, response, client, model, context) -> {
                                    String statusTrackId = response.accountAssignmentCreationStatus().requestId();
                                    DescribeAccountAssignmentCreationStatusResponse checkStatusResponse = proxy.injectCredentialsAndInvokeV2(translateToDescribeCreationStatusRequest(model.getInstanceArn(), statusTrackId),
                                            proxyClient.client()::describeAccountAssignmentCreationStatus);
                                    AccountAssignmentOperationStatus creationStatus = checkStatusResponse.accountAssignmentCreationStatus();
                                    if (creationStatus.status().equals(StatusValues.SUCCEEDED)) {
                                        logger.log(String.format("%s [%s] has been stabilized.", ResourceModel.TYPE_NAME, model.getPrimaryIdentifier()));
                                        //reset the retry attemps for following read API
                                        context.setRetryAttempts(RETRY_ATTEMPTS);
                                        return true;
                                    } else if (creationStatus.status().equals(StatusValues.FAILED)) {
                                        throw new CfnGeneralServiceException(String.format(FAILED_WORKFLOW_REQUEST, statusTrackId, creationStatus.failureReason()));
                                    }
                                    return false;
                                })
                                .handleError((awsRequest, exception, client, resourceModel, context) -> {
                                    if (exception instanceof ConflictException || exception instanceof ThrottlingException) {
                                        return ProgressEvent.defaultInProgressHandler(callbackContext, 180, resourceModel);
                                    } else if (exception instanceof InternalServerException) {
                                        if (context.getRetryAttempts() == RETRY_ATTEMPTS_ZERO) {
                                            return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.InternalFailure);
                                        }
                                        context.decrementRetryAttempts();
                                        return ProgressEvent.defaultInProgressHandler(callbackContext, 5, resourceModel);
                                    }
                                    return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.GeneralServiceException);
                                })
                                .progress()
                )
                .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }
}
