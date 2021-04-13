package software.amazon.sso.instanceaccesscontrolattributeconfiguration;

import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.AccessDeniedException;
import software.amazon.awssdk.services.ssoadmin.model.ConflictException;
import software.amazon.awssdk.services.ssoadmin.model.DescribeInstanceAccessControlAttributeConfigurationRequest;
import software.amazon.awssdk.services.ssoadmin.model.DescribeInstanceAccessControlAttributeConfigurationResponse;
import software.amazon.awssdk.services.ssoadmin.model.InternalServerException;
import software.amazon.awssdk.services.ssoadmin.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ssoadmin.model.ThrottlingException;
import software.amazon.awssdk.services.ssoadmin.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;


/**
 * Handler to perform deletion of the InstanceAccessControlAttributeConfiguration in AWS SSO
 * Performs:
 * 1. Delete request
 * 2. Describe request to make sure InstanceAccessControlAttributeConfiguration was successfully deleted.
 */
public class DeleteHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<SsoAdminClient> proxyClient,
            final Logger logger) {

        this.logger = logger;

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
                .then(progress ->
                        proxy.initiate("sso::deleteInstanceAccessControlAttributeConfiguration", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                                .translateToServiceRequest(Translator::translateToDeleteRequest)
                                .makeServiceCall((deleteRequest, client) -> proxy.injectCredentialsAndInvokeV2(deleteRequest,
                                        client.client()::deleteInstanceAccessControlAttributeConfiguration))
                                .stabilize((deleteRequest, deleteResponse, client, model, context) -> {
                                    DescribeInstanceAccessControlAttributeConfigurationRequest describeRequest = DescribeInstanceAccessControlAttributeConfigurationRequest
                                            .builder()
                                            .instanceArn(deleteRequest.instanceArn())
                                            .build();
                                    try {
                                        proxy.injectCredentialsAndInvokeV2(describeRequest,
                                                client.client()::describeInstanceAccessControlAttributeConfiguration);
                                    } catch (ResourceNotFoundException e) {
                                        logger.log("InstanceAccessControlAttributeConfiguration was deleted ");
                                        return true;
                                    } catch (ThrottlingException | InternalServerException e) {
                                        context.decrementRetryAttempts();
                                        return false;
                                    }
                                    logger.log(String.format("Failed to delete InstanceAccessControlAttributeConfiguration.  RequestId: %s", deleteResponse.responseMetadata().requestId()));
                                    throw new CfnGeneralServiceException("Fail to delete InstanceAccessControlAttributeConfiguration");
                                })
                                .handleError((deleteRequest, exception, client, model, context) -> {
                                    if (exception instanceof ResourceNotFoundException) {
                                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.NotFound);
                                    } else if (exception instanceof AccessDeniedException) {
                                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.AccessDenied);
                                    } else if (exception instanceof ValidationException) {
                                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.InvalidRequest);
                                    } else if (exception instanceof ConflictException || exception instanceof ThrottlingException
                                            || exception instanceof InternalServerException) {
                                        if (context.getRetryAttempts() == RETRY_ATTEMPTS_ZERO) {
                                            return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.GeneralServiceException);
                                        }
                                        context.decrementRetryAttempts();
                                        return ProgressEvent.defaultInProgressHandler(context, getRetryTime(exception), model);
                                    } else {
                                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.GeneralServiceException);
                                    }
                                })
                                .done((deleteRequest, deleteResponse, client, model, context) -> ProgressEvent.defaultSuccessHandler(null)));
    }
}
