package software.amazon.sso.permissionset;

import com.amazonaws.util.StringUtils;
import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.ConflictException;
import software.amazon.awssdk.services.ssoadmin.model.InternalServerException;
import software.amazon.awssdk.services.ssoadmin.model.ThrottlingException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.sso.permissionset.actionProxy.InlinePolicyProxy;
import software.amazon.sso.permissionset.actionProxy.ManagedPolicyAttachmentProxy;

import static software.amazon.sso.permissionset.utils.Constants.RETRY_ATTEMPTS;
import static software.amazon.sso.permissionset.utils.Constants.RETRY_ATTEMPTS_ZERO;

public class CreateHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<SsoAdminClient> proxyClient,
        final Logger logger) {

        this.logger = logger;
        ManagedPolicyAttachmentProxy managedPolicyAttachmentProxy = new ManagedPolicyAttachmentProxy(proxy, proxyClient);
        InlinePolicyProxy inlinePolicyProxy = new InlinePolicyProxy(proxy, proxyClient);

        logger.log("Starting PermissionSet creation process.");

        if (!callbackContext.isHandlerInvoked()) {
            callbackContext.setHandlerInvoked(true);
            callbackContext.setRetryAttempts(RETRY_ATTEMPTS);
        }

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
                .then(progress -> proxy.initiate("sso::create-permissionset", proxyClient, request.getDesiredResourceState(), callbackContext)
                        .translateToServiceRequest(Translator::translateToCreateRequest)
                        .makeServiceCall((createPermissionSetRequest, client) -> proxy.injectCredentialsAndInvokeV2(createPermissionSetRequest, client.client()::createPermissionSet))
                        .handleError((createPermissionSetRequest, exception, client, model, context) -> {
                            if (exception instanceof ConflictException) {
                                return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.AlreadyExists);
                            } else if (exception instanceof ThrottlingException || exception instanceof InternalServerException) {
                                if (context.getRetryAttempts() == RETRY_ATTEMPTS_ZERO) {
                                    throw exception;
                                }
                                context.decrementRetryAttempts();
                                return ProgressEvent.defaultInProgressHandler(callbackContext, 1, model);
                            }
                            throw exception;
                        })
                        .done((createPermissionSetRequest, createPermissionSetResponse, proxyInvocation, model, context) -> {
                            if (!StringUtils.isNullOrEmpty(createPermissionSetResponse.permissionSet().permissionSetArn())) {
                                model.setPermissionSetArn(createPermissionSetResponse.permissionSet().permissionSetArn());
                                logger.log(String.format("%s successfully created.", ResourceModel.TYPE_NAME));
                                //Reset the retry attempts for next action
                                context.resetRetryAttempts(RETRY_ATTEMPTS);
                                return ProgressEvent.defaultInProgressHandler(context, 0, model);
                            } else {
                                throw new CfnInternalFailureException(InternalServerException.builder().message("There is an internal service error with permission set creation.").build());
                            }
                        })
                )
                .then(progress -> {
                    ResourceModel model = progress.getResourceModel();

                    if (!callbackContext.isManagedPolicyUpdated()) {
                        try {
                            managedPolicyAttachmentProxy.updateManagedPolicyAttachment(model.getInstanceArn(),
                                    model.getPermissionSetArn(),
                                    model.getManagedPolicies());
                        } catch (ThrottlingException | InternalServerException | ConflictException e) {
                            if (callbackContext.getRetryAttempts() == RETRY_ATTEMPTS_ZERO) {
                                throw e;
                            }
                            callbackContext.decrementRetryAttempts();
                            return ProgressEvent.defaultInProgressHandler(callbackContext, 1, model);
                        }
                        callbackContext.setManagedPolicyUpdated(true);
                        //Reset the retry attempts for next action
                        callbackContext.resetRetryAttempts(RETRY_ATTEMPTS);
                    }
                    logger.log("Managed policies attached successfully.");
                    return progress;
                })
                .then(progress -> {
                    ResourceModel model = progress.getResourceModel();

                    if (!callbackContext.isInlinePolicyUpdated()) {
                        if (model.getInlinePolicy() != null && !model.getInlinePolicy().isEmpty()) {
                            try {
                                inlinePolicyProxy.putInlinePolicyToPermissionSet(model.getInstanceArn(), model.getPermissionSetArn(), model.getInlinePolicy());
                            } catch (ThrottlingException | InternalServerException | ConflictException e) {
                                if (callbackContext.getRetryAttempts() == RETRY_ATTEMPTS_ZERO) {
                                    throw e;
                                }
                                callbackContext.decrementRetryAttempts();
                                return ProgressEvent.defaultInProgressHandler(callbackContext, 1, model);
                            }
                        }
                        callbackContext.setInlinePolicyUpdated(true);
                    }
                    logger.log("Inline policy added successfully.");
                    return progress;
                })
                .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }
}
