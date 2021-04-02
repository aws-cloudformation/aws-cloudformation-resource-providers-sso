package software.amazon.sso.instanceaccesscontrolattributeconfiguration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.ConflictException;
import software.amazon.awssdk.services.ssoadmin.model.ThrottlingException;
import software.amazon.cloudformation.LambdaWrapper;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.net.URI;
import java.security.SecureRandom;
import java.util.stream.IntStream;

/**
 * This is a base class for every handler
 */
public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {

  public final static int RETRY_ATTEMPTS_ZERO = 0;
  public final static int RETRY_ATTEMPTS_MAX = 5;
  protected static final SecureRandom SECURE_RANDOM = new SecureRandom();

  @Override
  public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(
          final AmazonWebServicesClientProxy proxy,
          final ResourceHandlerRequest<ResourceModel> request,
          final CallbackContext callbackContext,
          final Logger logger) {
    return handleRequest(
            proxy,
            request,
            callbackContext != null ? callbackContext : new CallbackContext(RETRY_ATTEMPTS_MAX),
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

  protected int getRetryTime(Exception exception) {
    IntStream possibleNumber;
    if (exception instanceof ThrottlingException) {
      possibleNumber =  SECURE_RANDOM.ints(10, 200);
    } else {
      possibleNumber =  SECURE_RANDOM.ints(50, 250);
    }
    return possibleNumber.findAny().getAsInt();
  }
}
