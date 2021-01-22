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

import static software.amazon.sso.instanceaccesscontrolattributeconfiguration.Translator.accessControlAttributeConfigsIsEquals;
import static software.amazon.sso.instanceaccesscontrolattributeconfiguration.Translator.convertToCFConfiguration;

/**
 * Handler to update InstanceAccessControlAttributeConfiguration for AWS SSO
 * Performs:
 * 1. Update request
 * 2. Describe request to make sure InstanceAccessControlAttributeConfiguration successfully updated.
 */
public class UpdateHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<SsoAdminClient> proxyClient,
            final Logger logger) {

        this.logger = logger;

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
                // STEP 1 [first update/stabilize progress chain - required for resource update]
                .then(progress ->
                        // STEP 1.0 initialize a proxy context
                        proxy.initiate("sso::updateInstanceAccessControlAttributeConfiguration", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                                .translateToServiceRequest(Translator::translateToUpdateRequest)
                                .makeServiceCall((updateRequest, client) -> proxy.injectCredentialsAndInvokeV2(updateRequest,
                                        client.client()::updateInstanceAccessControlAttributeConfiguration))
                                .stabilize((updateRequest, updateResponse, client, model, context) -> {
                                    DescribeInstanceAccessControlAttributeConfigurationRequest describeRequest = DescribeInstanceAccessControlAttributeConfigurationRequest
                                            .builder()
                                            .instanceArn(updateRequest.instanceArn())
                                            .build();
                                    DescribeInstanceAccessControlAttributeConfigurationResponse describeABACResponse = proxy.injectCredentialsAndInvokeV2(describeRequest,
                                            client.client()::describeInstanceAccessControlAttributeConfiguration);
                                    if (!accessControlAttributeConfigsIsEquals(model, convertToCFConfiguration(describeABACResponse))) {
                                        Throwable exception = new CfnGeneralServiceException("Failed to update attribute based access configuration");
                                        logger.log(String.format("Failed to stabilize update. RequestId: %s", describeABACResponse.responseMetadata().requestId()));
                                        throw new CfnGeneralServiceException(exception);
                                    } else {
                                        return true;
                                    }
                                })
                                .handleError((updateRequest, exception, client, model, context) -> {
                                    if (exception instanceof ConflictException) {
                                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.ResourceConflict);
                                    } else if(exception instanceof AccessDeniedException){
                                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.AccessDenied);
                                    } else if (exception instanceof ValidationException) {
                                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.InvalidRequest);
                                    } else if (exception instanceof ResourceNotFoundException) {
                                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.NotFound);
                                    } else if (exception instanceof ThrottlingException || exception instanceof InternalServerException) {
                                        if (context.getRetryAttempts() == RETRY_ATTEMPTS_ZERO) {
                                            throw exception;
                                        }
                                        context.decrementRetryAttempts();
                                        return ProgressEvent.defaultInProgressHandler(context, getRetryTime(exception), model);
                                    } else {
                                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.GeneralServiceException);
                                    }
                                })
                                .progress()
                ).then(progress -> new ReadHandler().handleRequest(proxy, request, new CallbackContext(), proxyClient, logger));
    }
}
