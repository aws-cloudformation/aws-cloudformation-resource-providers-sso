package software.amazon.sso.permissionset;

import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.ConflictException;
import software.amazon.awssdk.services.ssoadmin.model.InternalServerException;
import software.amazon.awssdk.services.ssoadmin.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ssoadmin.model.ThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import static software.amazon.sso.permissionset.utils.Constants.RETRY_ATTEMPTS;
import static software.amazon.sso.permissionset.utils.Constants.RETRY_ATTEMPTS_ZERO;

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

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress ->
                proxy.initiate("sso::delete-permissionset", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                    .translateToServiceRequest(Translator::translateToDeleteRequest)
                    .makeServiceCall((deleteRequest, client) -> proxy.injectCredentialsAndInvokeV2(deleteRequest, client.client()::deletePermissionSet))
                    .handleError((deleteRequest, exception, client, model, context) -> {
                        if (exception instanceof ResourceNotFoundException) {
                            return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.NotFound);
                        } else if (exception instanceof ThrottlingException || exception instanceof ConflictException || exception instanceof InternalServerException) {
                            if (context.getRetryAttempts() == RETRY_ATTEMPTS_ZERO) {
                                return ProgressEvent.defaultFailureHandler(exception, mapExceptionToHandlerCode(exception));
                            }
                            context.decrementRetryAttempts();
                            return ProgressEvent.defaultInProgressHandler(callbackContext, getRetryTime(exception), model);
                        }
                        return ProgressEvent.defaultFailureHandler(exception, HandlerErrorCode.GeneralServiceException);
                    })
                    .done((deleteRequest, result, client, model, context) -> ProgressEvent.defaultSuccessHandler(null)));
    }
}
