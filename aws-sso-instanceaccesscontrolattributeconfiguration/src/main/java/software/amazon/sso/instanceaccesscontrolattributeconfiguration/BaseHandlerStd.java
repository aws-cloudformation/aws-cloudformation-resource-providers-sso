package software.amazon.sso.instanceaccesscontrolattributeconfiguration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.cloudformation.LambdaWrapper;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.net.URI;

/**
 * This is a base class for every handler
 */
public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {

  public final static int RETRY_ATTEMPTS_ZERO = 0;

  @Override
  public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(
          final AmazonWebServicesClientProxy proxy,
          final ResourceHandlerRequest<ResourceModel> request,
          final CallbackContext callbackContext,
          final Logger logger) {
    return handleRequest(
            proxy,
            request,
            callbackContext != null ? callbackContext : new CallbackContext(),
            proxy.newProxy(()-> getClient()),
            logger
    );
  }

  protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest(
          final AmazonWebServicesClientProxy proxy,
          final ResourceHandlerRequest<ResourceModel> request,
          final CallbackContext callbackContext,
          final ProxyClient<SsoAdminClient> proxyClient,
          final Logger logger);

  private static SsoAdminClient getClient() {
    return SsoAdminClient.builder()
            .httpClient(LambdaWrapper.HTTP_CLIENT)
            .build();
  }
}
