package software.amazon.sso.permissionset;

import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.AccessDeniedException;
import software.amazon.awssdk.services.ssoadmin.model.DescribePermissionSetResponse;
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
import software.amazon.sso.permissionset.actionProxy.InlinePolicyProxy;
import software.amazon.sso.permissionset.actionProxy.ManagedPolicyAttachmentProxy;

import java.util.List;

import static software.amazon.sso.permissionset.utils.Constants.RETRY_ATTEMPTS;
import static software.amazon.sso.permissionset.utils.Constants.RETRY_ATTEMPTS_ZERO;
import static software.amazon.sso.permissionset.utils.TagsUtil.getResourceTags;

public class ReadHandler extends BaseHandlerStd {
    private static final String THROTTLE_MESSAGE = "Read request got throttled. Please add DependsOn attribute if you have large number of AWS SSO owned resources";
    private static final String ISE_MESSAGE = "Something went wrong while performing READ call";
    private Logger logger;
    private List<Tag> tags;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<SsoAdminClient> proxyClient,
            final Logger logger) {

        this.logger = logger;

        ManagedPolicyAttachmentProxy managedPolicyAttachmentProxy = new ManagedPolicyAttachmentProxy(proxy, proxyClient);
        InlinePolicyProxy inlinePolicyProxy = new InlinePolicyProxy(proxy, proxyClient);

        ResourceModel model = request.getDesiredResourceState();

        tags = model.getTags();

        return ProgressEvent.progress(model, callbackContext)
                .then(progress -> proxy.initiate("AWS-SSO-PermissionSet::Read", proxyClient, model, callbackContext)
                        .translateToServiceRequest(Translator::translateToReadRequest)
                        .makeServiceCall((readRequest, client) -> {
                            DescribePermissionSetResponse response = null;
                            int iseRetryAttempts = Integer.valueOf(RETRY_ATTEMPTS);
                            int throttlingReadAttempts = Integer.valueOf(RETRY_ATTEMPTS);
                            while (iseRetryAttempts > RETRY_ATTEMPTS_ZERO && throttlingReadAttempts > RETRY_ATTEMPTS_ZERO) {
                                try {
                                    response = proxy.injectCredentialsAndInvokeV2(readRequest, client.client()::describePermissionSet);
                                    if (tags == null || tags.isEmpty()) {
                                        tags = Translator.ConvertToModelTag(getResourceTags(readRequest.instanceArn(),
                                                model.getPermissionSetArn(),
                                                proxy,
                                                proxyClient));
                                    }

                                    logger.log(String.format("%s has successfully been read.", ResourceModel.TYPE_NAME));
                                    break;
                                } catch (ThrottlingException te) {
                                    throttlingReadAttempts = decrementAndWait(throttlingReadAttempts);
                                    continue;
                                } catch (InternalServerException ise) {
                                    iseRetryAttempts = decrementAndWait(iseRetryAttempts);
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
                        .handleError((describePermissionSetRequest, exception, client, resourceModel, context) -> {
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
                        .done((readRequest, readResponse, proxyInvocation, resourceModel, context) -> {
                            ResourceModel outputModel = Translator.translateFromReadResponse(readResponse, resourceModel.getInstanceArn(), tags);
                            context.resetRetryAttempts(RETRY_ATTEMPTS);
                            return ProgressEvent.defaultInProgressHandler(context, 0, outputModel);
                        })
                )
                .then(progress -> {
                    ResourceModel outputModel = progress.getResourceModel();
                    int iseRetryAttempts = Integer.valueOf(RETRY_ATTEMPTS);
                    int throttlingReadAttempts = Integer.valueOf(RETRY_ATTEMPTS);
                    while (iseRetryAttempts > RETRY_ATTEMPTS_ZERO && throttlingReadAttempts > RETRY_ATTEMPTS_ZERO) {
                        try {
                            outputModel.setManagedPolicies(managedPolicyAttachmentProxy.getAttachedManagedPolicies(outputModel.getInstanceArn(),
                                    outputModel.getPermissionSetArn()));
                            outputModel.setInlinePolicy(inlinePolicyProxy.getInlinePolicyForPermissionSet(outputModel.getInstanceArn(),
                                    outputModel.getPermissionSetArn()));
                            break;
                        } catch (ThrottlingException te) {
                            throttlingReadAttempts = decrementAndWait(throttlingReadAttempts);
                            continue;
                        } catch (InternalServerException ise) {
                            iseRetryAttempts = decrementAndWait(iseRetryAttempts);
                            continue;
                        }
                    }

                    if (throttlingReadAttempts == RETRY_ATTEMPTS_ZERO) {
                        return ProgressEvent.defaultFailureHandler(ThrottlingException.builder().message(THROTTLE_MESSAGE).build(), HandlerErrorCode.Throttling);
                    } else if(iseRetryAttempts == RETRY_ATTEMPTS_ZERO ) {
                        return ProgressEvent.defaultFailureHandler(InternalServerException.builder().message(ISE_MESSAGE).build(), HandlerErrorCode.InternalFailure);
                    } else {
                        return ProgressEvent.defaultSuccessHandler(outputModel);
                    }
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
