package software.amazon.sso.instanceaccesscontrolattributeconfiguration;

import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.AccessDeniedException;
import software.amazon.awssdk.services.ssoadmin.model.InternalServerException;
import software.amazon.awssdk.services.ssoadmin.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ssoadmin.model.ThrottlingException;
import software.amazon.awssdk.services.ssoadmin.model.ValidationException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

/**
 * Handler to describe InstanceAccessControlAttributeConfiguration for AWS SSO
 * Performs:
 * 1. Describe request to get current InstanceAccessControlAttributeConfiguration
 */
public class ReadHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<SsoAdminClient> proxyClient,
            final Logger logger) {

        this.logger = logger;

        return proxy.initiate("sso::describeInstanceAccessControlAttributeConfiguration", proxyClient, request.getDesiredResourceState(), callbackContext)
                .translateToServiceRequest(Translator::translateToDescribeRequest)
                .makeServiceCall((describeRequest, client) -> proxy.injectCredentialsAndInvokeV2(describeRequest,
                        client.client()::describeInstanceAccessControlAttributeConfiguration))
                .handleError((describeRequest, exception, client, model, context) -> {
                    if (exception instanceof ResourceNotFoundException) {
                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.NotFound);
                    } else if(exception instanceof AccessDeniedException){
                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.AccessDenied);
                    } else if (exception instanceof ValidationException) {
                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.InvalidRequest);
                    } else if (exception instanceof ThrottlingException || exception instanceof InternalServerException) {
                        if (context.getRetryAttempts() == RETRY_ATTEMPTS_ZERO) {
                            return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.GeneralServiceException);
                        }
                        context.decrementRetryAttempts();
                        return ProgressEvent.defaultInProgressHandler(context, 1, model);
                    } else {
                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.GeneralServiceException);
                    }
                })
                .done((describeRequest, describeResponse, proxyInvocation, model, context) -> {
                    String instanceArn = describeRequest.instanceArn();
                    return ProgressEvent.defaultSuccessHandler(Translator.translateFromDescribeResponse(describeResponse, instanceArn));
                });
    }
}
