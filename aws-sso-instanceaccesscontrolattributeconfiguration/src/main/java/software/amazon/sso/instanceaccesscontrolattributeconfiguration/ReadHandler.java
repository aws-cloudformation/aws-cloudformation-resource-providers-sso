package software.amazon.sso.instanceaccesscontrolattributeconfiguration;

import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.AccessDeniedException;
import software.amazon.awssdk.services.ssoadmin.model.DescribeInstanceAccessControlAttributeConfigurationResponse;
import software.amazon.awssdk.services.ssoadmin.model.InternalServerException;
import software.amazon.awssdk.services.ssoadmin.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ssoadmin.model.ThrottlingException;
import software.amazon.awssdk.services.ssoadmin.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.security.SecureRandom;

/**
 * Handler to describe InstanceAccessControlAttributeConfiguration for AWS SSO
 * Performs:
 * 1. Describe request to get current InstanceAccessControlAttributeConfiguration
 */
public class ReadHandler extends BaseHandlerStd {
    private static final String THROTTLE_MESSAGE = "Read request got throttled. Please add DependsOn attribute if you have large number of AWS SSO owned resources";
    private static final String ISE_MESSAGE = "Something went wrong while performing READ call";
    private static final int MAX_RETRY = 5;
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
                .makeServiceCall((describeRequest, client) -> {
                    DescribeInstanceAccessControlAttributeConfigurationResponse response = null;
                    int throttlingReadAttempts = MAX_RETRY;
                    int iseReadAttempts = MAX_RETRY;
                    while (throttlingReadAttempts > RETRY_ATTEMPTS_ZERO && iseReadAttempts > RETRY_ATTEMPTS_ZERO) {
                        try {
                            response = proxy.injectCredentialsAndInvokeV2(describeRequest,
                                    client.client()::describeInstanceAccessControlAttributeConfiguration);
                            logger.log(String.format("%s has successfully been read.", ResourceModel.TYPE_NAME));
                            break;
                        } catch (ThrottlingException te) {
                            throttlingReadAttempts = decrementAndWait(throttlingReadAttempts);
                            continue;
                        } catch (InternalServerException ise) {
                            iseReadAttempts = decrementAndWait(iseReadAttempts);
                            continue;
                        }
                    }
                    if (response != null) {
                        return response;
                    } else if (throttlingReadAttempts == RETRY_ATTEMPTS_ZERO) {
                        throw ThrottlingException.builder().message(THROTTLE_MESSAGE).build();
                    } else {
                        throw InternalServerException.builder().message(ISE_MESSAGE).build();
                    }
                })
                .handleError((describeRequest, exception, client, model, context) -> {
                    if (exception instanceof ResourceNotFoundException) {
                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.NotFound);
                    } else if(exception instanceof AccessDeniedException){
                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.AccessDenied);
                    } else if (exception instanceof ValidationException) {
                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.InvalidRequest);
                    } else if (exception instanceof ThrottlingException) {
                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.Throttling);
                    } else {
                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.InternalFailure);
                    }
                })
                .done((describeRequest, describeResponse, proxyInvocation, model, context) -> {
                    String instanceArn = describeRequest.instanceArn();
                    return ProgressEvent.defaultSuccessHandler(Translator.translateFromDescribeResponse(describeResponse, instanceArn));
                });
    }

    /**
     * Decrement context and wait for some time
     * Should wait between 1 and 5 second in case got throttled.
     */
    private int decrementAndWait(int attempts) {
        int timeToWait = SECURE_RANDOM.ints(1000, 5000).findFirst().getAsInt();
        try {
            Thread.sleep(timeToWait);
        } catch (InterruptedException e) {
            // Ignore if fail sleep
        }
        return attempts - 1;
    }
}
