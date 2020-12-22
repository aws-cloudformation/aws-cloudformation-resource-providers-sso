package software.amazon.sso.instanceaccesscontrolattributeconfiguration;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;

public class AbstractTestBase {
  public final static int RETRY_ATTEMPTS = 5;
  protected static final Credentials MOCK_CREDENTIALS;
  protected static final LoggerProxy logger;
  protected static final String SSO_INSTANCE_ARN = "arn:aws:sso:::instance/ssoins-72236b51344cf12c";
  protected final InstanceAccessControlAttributeConfiguration cfAccessControlAttributeConfiguration  = InstanceAccessControlAttributeConfiguration
          .builder()
          .accessControlAttributes(Arrays.asList(getCfFirsAccessControlAttribute(), getCfSecondAccessControlAttribute()))
          .build();

  protected final ResourceModel expectedModel = ResourceModel.builder()
          .accessControlAttributes(Arrays.asList(getCfFirsAccessControlAttribute(), getCfSecondAccessControlAttribute()))
          .instanceArn(SSO_INSTANCE_ARN)
          .build();

  protected final software.amazon.awssdk.services.ssoadmin.model.InstanceAccessControlAttributeConfiguration ssoAccessControlAttributeConfiguration  = software.amazon.awssdk.services.ssoadmin.model.InstanceAccessControlAttributeConfiguration
          .builder()
          .accessControlAttributes(Arrays.asList(getSsoFirsAccessControlAttribute(), getSSoSecondAccessControlAttribute()))
          .build();

  protected final software.amazon.awssdk.services.ssoadmin.model.InstanceAccessControlAttributeConfiguration ssoEmptyAccessControlAttributeConfiguration  = software.amazon.awssdk.services.ssoadmin.model.InstanceAccessControlAttributeConfiguration
          .builder()
          .accessControlAttributes(Collections.emptyList())
          .build();

  static {
    MOCK_CREDENTIALS = new Credentials("accessKey", "secretKey", "token");
    logger = new LoggerProxy();
  }
  static ProxyClient<SsoAdminClient> MOCK_PROXY(
          final AmazonWebServicesClientProxy proxy,
          final SsoAdminClient sdkClient) {
    return new ProxyClient<SsoAdminClient>() {
      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseT
      injectCredentialsAndInvokeV2(RequestT request, Function<RequestT, ResponseT> requestFunction) {
        return proxy.injectCredentialsAndInvokeV2(request, requestFunction);
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse>
      CompletableFuture<ResponseT>
      injectCredentialsAndInvokeV2Async(RequestT request, Function<RequestT, CompletableFuture<ResponseT>> requestFunction) {
        throw new UnsupportedOperationException();
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse, IterableT extends SdkIterable<ResponseT>>
      IterableT
      injectCredentialsAndInvokeIterableV2(RequestT request, Function<RequestT, IterableT> requestFunction) {
        return proxy.injectCredentialsAndInvokeIterableV2(request, requestFunction);
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseInputStream<ResponseT>
      injectCredentialsAndInvokeV2InputStream(RequestT requestT, Function<RequestT, ResponseInputStream<ResponseT>> function) {
        throw new UnsupportedOperationException();
      }

      @Override
      public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseBytes<ResponseT>
      injectCredentialsAndInvokeV2Bytes(RequestT requestT, Function<RequestT, ResponseBytes<ResponseT>> function) {
        throw new UnsupportedOperationException();
      }

      @Override
      public SsoAdminClient client() {
        return sdkClient;
      }
    };
  }

  protected AccessControlAttribute getCfFirsAccessControlAttribute() {
    return AccessControlAttribute.builder()
            .key("test1")
            .value(AccessControlAttributeValue.builder().source(Arrays.asList("test1Value1", "test1Value2")).build())
            .build();
  }

  protected AccessControlAttribute getCfSecondAccessControlAttribute() {
    return AccessControlAttribute.builder()
            .key("test2")
            .value(AccessControlAttributeValue.builder().source(Arrays.asList("test2Value1", "test2Value2")).build())
            .build();
  }

  protected software.amazon.awssdk.services.ssoadmin.model.AccessControlAttribute getSsoFirsAccessControlAttribute() {
    return software.amazon.awssdk.services.ssoadmin.model.AccessControlAttribute.builder()
            .key("test1")
            .value(software.amazon.awssdk.services.ssoadmin.model.AccessControlAttributeValue.builder()
                    .source(Arrays.asList("test1Value1", "test1Value2"))
                    .build())
            .build();
  }

  protected software.amazon.awssdk.services.ssoadmin.model.AccessControlAttribute getSSoSecondAccessControlAttribute() {
    return software.amazon.awssdk.services.ssoadmin.model.AccessControlAttribute.builder()
            .key("test2")
            .value(software.amazon.awssdk.services.ssoadmin.model.AccessControlAttributeValue.builder()
                    .source(Arrays.asList("test2Value1", "test2Value2"))
                    .build())
            .build();
  }
}
