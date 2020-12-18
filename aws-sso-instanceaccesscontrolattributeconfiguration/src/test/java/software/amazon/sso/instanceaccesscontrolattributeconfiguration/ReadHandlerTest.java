package software.amazon.sso.instanceaccesscontrolattributeconfiguration;

import java.time.Duration;
import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.AccessDeniedException;
import software.amazon.awssdk.services.ssoadmin.model.DescribeInstanceAccessControlAttributeConfigurationRequest;
import software.amazon.awssdk.services.ssoadmin.model.DescribeInstanceAccessControlAttributeConfigurationResponse;
import software.amazon.awssdk.services.ssoadmin.model.InternalServerException;
import software.amazon.awssdk.services.ssoadmin.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ssoadmin.model.SsoAdminException;
import software.amazon.awssdk.services.ssoadmin.model.ThrottlingException;
import software.amazon.awssdk.services.ssoadmin.model.ValidationException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<SsoAdminClient> proxyClient;

    @Mock
    private SsoAdminClient sdkClient;

    private final DescribeInstanceAccessControlAttributeConfigurationRequest describeRequest = DescribeInstanceAccessControlAttributeConfigurationRequest
            .builder()
            .instanceArn(SSO_INSTANCE_ARN)
            .build();


    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        sdkClient = mock(SsoAdminClient.class);
        proxyClient = MOCK_PROXY(proxy, sdkClient);
    }

    @AfterEach
    public void tear_down() {
        verify(sdkClient, atLeastOnce()).serviceName();
        verifyNoMoreInteractions(sdkClient);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DescribeInstanceAccessControlAttributeConfigurationResponse describeResponse = DescribeInstanceAccessControlAttributeConfigurationResponse
                .builder()
                .instanceAccessControlAttributeConfiguration(ssoAccessControlAttributeConfiguration)
                .status("ENABLED")
                .build();

        when(proxy.injectCredentialsAndInvokeV2(describeRequest, proxyClient.client()::describeInstanceAccessControlAttributeConfiguration))
                .thenReturn(describeResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_empty_configuration_SimpleSuccess() {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceArn(SSO_INSTANCE_ARN)
                .instanceAccessControlAttributeConfiguration(cfAccessControlAttributeConfiguration)
                .build();

        final ResourceModel updatedModel = ResourceModel.builder()
                .instanceArn(SSO_INSTANCE_ARN)
                .instanceAccessControlAttributeConfiguration(cfEmptyAccessControlAttributeConfiguration)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DescribeInstanceAccessControlAttributeConfigurationResponse describeResponse = DescribeInstanceAccessControlAttributeConfigurationResponse
                .builder()
                .instanceAccessControlAttributeConfiguration(ssoEmptyAccessControlAttributeConfiguration)
                .status("ENABLED")
                .build();

        when(proxy.injectCredentialsAndInvokeV2(describeRequest, proxyClient.client()::describeInstanceAccessControlAttributeConfiguration))
                .thenReturn(describeResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(updatedModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_ResourceNotFoundException() {

        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(describeRequest, proxyClient.client()::describeInstanceAccessControlAttributeConfiguration))
                .thenThrow(ResourceNotFoundException.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }

    @Test
    public void handleRequest_AccessDeniedException() {

        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(describeRequest, proxyClient.client()::describeInstanceAccessControlAttributeConfiguration))
                .thenThrow(AccessDeniedException.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AccessDenied);
    }

    @Test
    public void handleRequest_ValidationException() {

        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(describeRequest, proxyClient.client()::describeInstanceAccessControlAttributeConfiguration))
                .thenThrow(ValidationException.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
    }

    @Test
    public void handleRequest_ThrottlingException_then_Success() {

        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DescribeInstanceAccessControlAttributeConfigurationResponse describeResponse = DescribeInstanceAccessControlAttributeConfigurationResponse
                .builder()
                .instanceAccessControlAttributeConfiguration(ssoAccessControlAttributeConfiguration)
                .status("ENABLED")
                .build();


        when(proxy.injectCredentialsAndInvokeV2(describeRequest, proxyClient.client()::describeInstanceAccessControlAttributeConfiguration))
                .thenThrow(ThrottlingException.builder().build())
                .thenReturn(describeResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(1);
        assertThat(response.getCallbackContext().getRetryAttempts()).isEqualTo(RETRY_ATTEMPTS - 1);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        final ProgressEvent<ResourceModel, CallbackContext> secondResponse = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(secondResponse).isNotNull();
        assertThat(secondResponse.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(secondResponse.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(secondResponse.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(secondResponse.getResourceModels()).isNull();
        assertThat(secondResponse.getMessage()).isNull();
        assertThat(secondResponse.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_InternalServerException_then_Success() {

        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DescribeInstanceAccessControlAttributeConfigurationResponse describeResponse = DescribeInstanceAccessControlAttributeConfigurationResponse
                .builder()
                .instanceAccessControlAttributeConfiguration(ssoAccessControlAttributeConfiguration)
                .status("ENABLED")
                .build();


        when(proxy.injectCredentialsAndInvokeV2(describeRequest, proxyClient.client()::describeInstanceAccessControlAttributeConfiguration))
                .thenThrow(InternalServerException.builder().build())
                .thenReturn(describeResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(1);
        assertThat(response.getResourceModel()).isNotNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();

        final ProgressEvent<ResourceModel, CallbackContext> secondResponse = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(secondResponse).isNotNull();
        assertThat(secondResponse.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(secondResponse.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(secondResponse.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(secondResponse.getResourceModels()).isNull();
        assertThat(secondResponse.getMessage()).isNull();
        assertThat(secondResponse.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_unexpected_exception_then_CfnInternalFailureException() {

        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(describeRequest, proxyClient.client()::describeInstanceAccessControlAttributeConfiguration))
                .thenThrow(SsoAdminException.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.GeneralServiceException);
    }
}
