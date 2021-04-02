package software.amazon.sso.assignment;

import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.AccessDeniedException;
import software.amazon.awssdk.services.ssoadmin.model.ConflictException;
import software.amazon.awssdk.services.ssoadmin.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ssoadmin.model.ThrottlingException;
import software.amazon.awssdk.services.ssoadmin.model.ValidationException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

// Placeholder for the functionality that could be shared across Create/Read/Update/Delete/List Handlers

public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {

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
      callbackContext != null ? callbackContext : new CallbackContext(),
      proxy.newProxy(ClientBuilder::getClient),
      logger
    );
  }

  protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest(
    final AmazonWebServicesClientProxy proxy,
    final ResourceHandlerRequest<ResourceModel> request,
    final CallbackContext callbackContext,
    final ProxyClient<SsoAdminClient> proxyClient,
    final Logger logger);

  protected int getRetryTime(Exception exception) {
    IntStream possibleNumber;
    if (exception instanceof ConflictException || exception instanceof ThrottlingException) {
      possibleNumber =  SECURE_RANDOM.ints(60, 300);
    } else {
      possibleNumber =  SECURE_RANDOM.ints(5, 100);
    }
    return possibleNumber.findAny().getAsInt();
  }

  protected HandlerErrorCode mapExceptionToHandlerCode(Exception exception) {
    if (exception instanceof ResourceNotFoundException) {
      return HandlerErrorCode.NotFound;
    } else if (exception instanceof AccessDeniedException) {
      return HandlerErrorCode.AccessDenied;
    } else if (exception instanceof ValidationException) {
      return HandlerErrorCode.InvalidRequest;
    } else if (exception instanceof ConflictException) {
      return HandlerErrorCode.AlreadyExists;
    } else if (exception instanceof ThrottlingException) {
      return HandlerErrorCode.Throttling;
    } else {
      return HandlerErrorCode.InternalFailure;
    }
  }

}
