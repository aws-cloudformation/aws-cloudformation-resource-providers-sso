package software.amazon.sso.permissionset;

import com.google.common.collect.Sets;
import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.ConflictException;
import software.amazon.awssdk.services.ssoadmin.model.DescribePermissionSetProvisioningStatusRequest;
import software.amazon.awssdk.services.ssoadmin.model.DescribePermissionSetProvisioningStatusResponse;
import software.amazon.awssdk.services.ssoadmin.model.InternalServerException;
import software.amazon.awssdk.services.ssoadmin.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ssoadmin.model.StatusValues;
import software.amazon.awssdk.services.ssoadmin.model.Tag;
import software.amazon.awssdk.services.ssoadmin.model.ThrottlingException;
import software.amazon.awssdk.services.ssoadmin.model.UpdatePermissionSetResponse;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.sso.permissionset.actionProxy.InlinePolicyProxy;
import software.amazon.sso.permissionset.actionProxy.ManagedPolicyAttachmentProxy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static software.amazon.sso.permissionset.utils.Constants.FAILED_WORKFLOW_REQUEST;
import static software.amazon.sso.permissionset.utils.Constants.RETRY_ATTEMPTS;
import static software.amazon.sso.permissionset.utils.Constants.RETRY_ATTEMPTS_ZERO;
import static software.amazon.sso.permissionset.utils.TagsUtil.getResourceTags;

public class UpdateHandler extends BaseHandlerStd {
    private Logger logger;

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<SsoAdminClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        ResourceModel model = request.getDesiredResourceState();
        ManagedPolicyAttachmentProxy managedPolicyAttachmentProxy = new ManagedPolicyAttachmentProxy(proxy, proxyClient);
        InlinePolicyProxy inlinePolicyProxy = new InlinePolicyProxy(proxy, proxyClient);

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
                .then(progress -> proxy.initiate("sso::update-permissionset", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                        .translateToServiceRequest(Translator::translateToUpdateRequest)
                        .makeServiceCall((updateRequest, client) -> {
                            UpdatePermissionSetResponse response = proxy.injectCredentialsAndInvokeV2(updateRequest, client.client()::updatePermissionSet);

                            //Reset attempts for next action
                            callbackContext.resetRetryAttempts(RETRY_ATTEMPTS);
                            return response;
                        })
                        .handleError((describePermissionSetRequest, exception, client, resourceModel, context) -> {
                            if (exception instanceof ResourceNotFoundException) {
                                return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.NotFound);
                            } else if (exception instanceof ThrottlingException || exception instanceof InternalServerException || exception instanceof ConflictException) {
                                if (context.getRetryAttempts() == RETRY_ATTEMPTS_ZERO) {
                                    throw exception;
                                }
                                context.decrementRetryAttempts();
                                return ProgressEvent.defaultInProgressHandler(callbackContext, 1, model);
                            }
                            throw exception;
                        })
                        .progress())
                .then(progress -> {
                    if (!callbackContext.isTagUpdateds()) {
                        try {
                            updateTags(model, proxy, proxyClient);
                        } catch (ThrottlingException | InternalServerException | ConflictException e) {
                            if (callbackContext.getRetryAttempts() == RETRY_ATTEMPTS_ZERO) {
                                throw e;
                            }
                            callbackContext.decrementRetryAttempts();
                            return ProgressEvent.defaultInProgressHandler(callbackContext, 1, model);
                        }
                        //Reset attempts for next action
                        callbackContext.resetRetryAttempts(RETRY_ATTEMPTS);
                        callbackContext.setTagUpdateds(true);
                    }

                    logger.log(String.format("%s tags have been successfully updated.", ResourceModel.TYPE_NAME));
                    return progress;
                })
                .then(progress -> {
                    if (!callbackContext.isManagedPolicyUpdated()) {
                        //Update related policies
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
                        //Reset attempts for next action
                        callbackContext.resetRetryAttempts(RETRY_ATTEMPTS);
                        callbackContext.setManagedPolicyUpdated(true);
                    }
                    logger.log(String.format("%s managed policies have been successfully updated.", ResourceModel.TYPE_NAME));
                    return progress;
                })
                .then(progress -> {
                    if (!callbackContext.isInlinePolicyUpdated()) {
                        try {
                            if (model.getInlinePolicy() != null && !model.getInlinePolicy().isEmpty()) {
                                inlinePolicyProxy.putInlinePolicyToPermissionSet(model.getInstanceArn(), model.getPermissionSetArn(), model.getInlinePolicy());
                            } else {
                                inlinePolicyProxy.deleteInlinePolicyFromPermissionSet(model.getInstanceArn(), model.getPermissionSetArn());
                            }
                        } catch (ThrottlingException | InternalServerException | ConflictException e) {
                            if (callbackContext.getRetryAttempts() == RETRY_ATTEMPTS_ZERO) {
                                throw e;
                            }
                            callbackContext.decrementRetryAttempts();
                            return ProgressEvent.defaultInProgressHandler(callbackContext, 1, model);
                        }
                        //Reset attempts for next action
                        callbackContext.resetRetryAttempts(RETRY_ATTEMPTS);
                        callbackContext.setInlinePolicyUpdated(true);
                    }
                    logger.log(String.format("%s inline policy has successfully been updated.", ResourceModel.TYPE_NAME));
                    return progress;
                })
                .then(progress -> proxy.initiate("sso::provision-permissionset", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                        .translateToServiceRequest(Translator::translateToProvsionPermissionSetRequest)
                        .makeServiceCall((provisionRequest, client) -> proxy.injectCredentialsAndInvokeV2(provisionRequest, proxyClient.client()::provisionPermissionSet))
                        .stabilize((provisionRequest, provisionResult, client, progressModel, context) -> {
                            logger.log("Stabilizing the provision status.");
                            String statusTrackId = provisionResult.permissionSetProvisioningStatus().requestId();
                            DescribePermissionSetProvisioningStatusRequest statusRequest = DescribePermissionSetProvisioningStatusRequest.builder()
                                    .provisionPermissionSetRequestId(statusTrackId)
                                    .instanceArn(progressModel.getInstanceArn())
                                    .build();
                            DescribePermissionSetProvisioningStatusResponse statusResult
                                    = proxy.injectCredentialsAndInvokeV2(statusRequest, client.client()::describePermissionSetProvisioningStatus);
                            if (statusResult.permissionSetProvisioningStatus().status().equals(StatusValues.SUCCEEDED)) {
                                logger.log(String.format("%s [%s] has been stabilized.", ResourceModel.TYPE_NAME, model.getPrimaryIdentifier()));
                                return true;
                            } else if (statusResult.permissionSetProvisioningStatus().status().equals(StatusValues.FAILED)) {
                                String failedReason = statusResult.permissionSetProvisioningStatus().failureReason();
                                throw new CfnGeneralServiceException(String.format(FAILED_WORKFLOW_REQUEST, statusTrackId, failedReason));
                            }
                            return false;
                        })
                        .handleError((awsRequest, exception, client, resourceModel, context) -> {
                            if (exception instanceof ConflictException || exception instanceof ThrottlingException) {
                                return ProgressEvent.defaultInProgressHandler(callbackContext, 300, model);
                            } else if (exception instanceof ResourceNotFoundException) {
                                return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.InternalFailure);
                            } else if (exception instanceof InternalServerException) {
                                if (context.getRetryAttempts() == RETRY_ATTEMPTS_ZERO) {
                                    throw exception;
                                }
                                context.decrementRetryAttempts();
                                return ProgressEvent.defaultInProgressHandler(callbackContext, 1, model);
                            }
                            throw exception;
                        })
                        .progress()
                )
                .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private void updateTags(ResourceModel model, AmazonWebServicesClientProxy proxy, ProxyClient<SsoAdminClient> proxyClient) {
        Set<Tag> previousTags = new HashSet<>(getResourceTags(model.getInstanceArn(),
                model.getPermissionSetArn(),
                proxy,
                proxyClient));
        Set<Tag> newTags = new HashSet<>(Translator.ConvertToSSOTag(model.getTags()));

        final Set<Tag> tagsToRemove = Sets.difference(previousTags, newTags);
        final Set<Tag> tagsToAdd  = Sets.difference(newTags, previousTags);

        if (!tagsToRemove.isEmpty()) {
            List<String> tagKeys = new ArrayList<>();
            for (Tag tag : tagsToRemove) {
                tagKeys.add(tag.key());
            }
            proxy.injectCredentialsAndInvokeV2(Translator.translateToUntagResourceRequest(model, tagKeys),
                    proxyClient.client()::untagResource);
        }
        if (!tagsToAdd.isEmpty()) {
            proxy.injectCredentialsAndInvokeV2(Translator.translateToTagResourceRequest(model, new ArrayList<>(tagsToAdd)),
                    proxyClient.client()::tagResource);
        }
    }
}
