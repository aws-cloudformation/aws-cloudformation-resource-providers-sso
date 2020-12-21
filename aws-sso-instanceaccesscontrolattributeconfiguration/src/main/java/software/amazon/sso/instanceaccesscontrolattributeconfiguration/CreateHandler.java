package software.amazon.sso.instanceaccesscontrolattributeconfiguration;

import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.AccessDeniedException;
import software.amazon.awssdk.services.ssoadmin.model.ConflictException;
import software.amazon.awssdk.services.ssoadmin.model.DescribeInstanceAccessControlAttributeConfigurationRequest;
import software.amazon.awssdk.services.ssoadmin.model.DescribeInstanceAccessControlAttributeConfigurationResponse;
import software.amazon.awssdk.services.ssoadmin.model.InstanceAccessControlAttributeConfigurationStatus;
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

import static software.amazon.sso.instanceaccesscontrolattributeconfiguration.Translator.compareIfAccessControlAttributeConfigsIsEquals;
import static software.amazon.sso.instanceaccesscontrolattributeconfiguration.Translator.convertToCFConfiguration;

/**
 * Handler to create InstanceAccessControlAttributeConfiguration for AWS SSO
 * Performs:
 * 1. Create request
 * 2. Stabilization of the configuration
 */
public class CreateHandler extends BaseHandlerStd {
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
                        proxy.initiate("sso::createInstanceAccessControlAttributeConfiguration", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                                .translateToServiceRequest(Translator::translateToCreateRequest)
                                .makeServiceCall((createRequest, client) -> proxy.injectCredentialsAndInvokeV2(createRequest,
                                        client.client()::createInstanceAccessControlAttributeConfiguration))
                                .stabilize((createRequest, createResponse, client, model, context) -> {
                                    DescribeInstanceAccessControlAttributeConfigurationRequest describeRequest = DescribeInstanceAccessControlAttributeConfigurationRequest
                                            .builder()
                                            .instanceArn(createRequest.instanceArn())
                                            .build();
                                    DescribeInstanceAccessControlAttributeConfigurationResponse describeABACResponse = proxy.injectCredentialsAndInvokeV2(describeRequest,
                                            client.client()::describeInstanceAccessControlAttributeConfiguration);
                                    if (InstanceAccessControlAttributeConfigurationStatus.CREATION_IN_PROGRESS.equals(describeABACResponse.status())) {
                                        logger.log(String.format("Attribute based access configuration creation is in progress."));
                                        return false;
                                    } else if (InstanceAccessControlAttributeConfigurationStatus.ENABLED.equals(describeABACResponse.status()) && compareIfAccessControlAttributeConfigsIsEquals(model.getInstanceAccessControlAttributeConfiguration(),
                                            convertToCFConfiguration(describeABACResponse.instanceAccessControlAttributeConfiguration()))) {
                                        return true;
                                    } else {
                                        if (!compareIfAccessControlAttributeConfigsIsEquals(model.getInstanceAccessControlAttributeConfiguration(),
                                                convertToCFConfiguration(describeABACResponse.instanceAccessControlAttributeConfiguration()))) {
                                            logger.log(String.format("Failed to stabilize create. RequestId: %s", createResponse.responseMetadata().requestId()));
                                            Throwable exception = new CfnGeneralServiceException("Failed to create desired attribute based access configuration");
                                            throw new CfnGeneralServiceException(exception);

                                        } else {
                                            throw new CfnGeneralServiceException(describeABACResponse.statusReason());
                                        }
                                    }
                                })
                                .handleError((createRequest, exception, client, model, context) -> {
                                    if (exception instanceof ResourceNotFoundException) {
                                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.NotFound);
                                    } else if (exception instanceof AccessDeniedException) {
                                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.AccessDenied);
                                    } else if (exception instanceof ValidationException) {
                                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.InvalidRequest);
                                    } else if (exception instanceof ConflictException) {
                                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.AlreadyExists);
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
                                .success()
                ).then(progress -> new ReadHandler().handleRequest(proxy, request, new CallbackContext(), proxyClient, logger));
    }
}
