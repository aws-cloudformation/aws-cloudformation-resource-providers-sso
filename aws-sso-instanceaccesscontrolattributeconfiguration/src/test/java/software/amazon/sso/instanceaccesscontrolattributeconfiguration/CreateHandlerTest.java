package software.amazon.sso.instanceaccesscontrolattributeconfiguration;

import java.time.Duration;
import java.util.Arrays;

import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.AccessDeniedException;
import software.amazon.awssdk.services.ssoadmin.model.ConflictException;
import software.amazon.awssdk.services.ssoadmin.model.CreateInstanceAccessControlAttributeConfigurationRequest;
import software.amazon.awssdk.services.ssoadmin.model.CreateInstanceAccessControlAttributeConfigurationResponse;
import software.amazon.awssdk.services.ssoadmin.model.DescribeInstanceAccessControlAttributeConfigurationRequest;
import software.amazon.awssdk.services.ssoadmin.model.DescribeInstanceAccessControlAttributeConfigurationResponse;
import software.amazon.awssdk.services.ssoadmin.model.InstanceAccessControlAttributeConfigurationStatus;
import software.amazon.awssdk.services.ssoadmin.model.InternalServerException;
import software.amazon.awssdk.services.ssoadmin.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ssoadmin.model.SsoAdminException;
import software.amazon.awssdk.services.ssoadmin.model.ThrottlingException;
import software.amazon.awssdk.services.ssoadmin.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<SsoAdminClient> proxyClient;

    @Mock
    private SsoAdminClient sdkClient;

    private final InstanceAccessControlAttributeConfiguration cfInstanceAccessControlAttributeConfiguration  = InstanceAccessControlAttributeConfiguration
            .builder()
            .accessControlAttributes(Arrays.asList(getCfFirsAccessControlAttribute(), getCfSecondAccessControlAttribute()))
            .build();

    private final CreateInstanceAccessControlAttributeConfigurationRequest createConfigurationRequest = CreateInstanceAccessControlAttributeConfigurationRequest
            .builder()
            .instanceAccessControlAttributeConfiguration(ssoAccessControlAttributeConfiguration)
            .instanceArn(SSO_INSTANCE_ARN)
            .build();

    private final CreateInstanceAccessControlAttributeConfigurationResponse createConfigurationResponse = CreateInstanceAccessControlAttributeConfigurationResponse
            .builder()
            .build();

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

    @Test
    public void handleRequest_SimpleSuccess() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfInstanceAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DescribeInstanceAccessControlAttributeConfigurationResponse describeResponse = DescribeInstanceAccessControlAttributeConfigurationResponse
                .builder()
                .instanceAccessControlAttributeConfiguration(ssoAccessControlAttributeConfiguration)
                .status(InstanceAccessControlAttributeConfigurationStatus.ENABLED)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(createConfigurationRequest, proxyClient.client()::createInstanceAccessControlAttributeConfiguration))
                .thenReturn(createConfigurationResponse);

        when(proxy.injectCredentialsAndInvokeV2(describeRequest, proxyClient.client()::describeInstanceAccessControlAttributeConfiguration))
                .thenReturn(describeResponse)
                .thenReturn(describeResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(expectedModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_with_list_Success() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .accessControlAttributes(Arrays.asList(getCfFirsAccessControlAttribute(), getCfSecondAccessControlAttribute()))
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DescribeInstanceAccessControlAttributeConfigurationResponse describeResponse = DescribeInstanceAccessControlAttributeConfigurationResponse
                .builder()
                .instanceAccessControlAttributeConfiguration(ssoAccessControlAttributeConfiguration)
                .status(InstanceAccessControlAttributeConfigurationStatus.ENABLED)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(createConfigurationRequest, proxyClient.client()::createInstanceAccessControlAttributeConfiguration))
                .thenReturn(createConfigurationResponse);

        when(proxy.injectCredentialsAndInvokeV2(describeRequest, proxyClient.client()::describeInstanceAccessControlAttributeConfiguration))
                .thenReturn(describeResponse)
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
    public void handleRequest_with_instance_and_list_Fails() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfInstanceAccessControlAttributeConfiguration)
                .accessControlAttributes(Arrays.asList(getCfFirsAccessControlAttribute(), getCfSecondAccessControlAttribute()))
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();


        assertThatThrownBy(() -> handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger))
                .isInstanceOf(CfnInvalidRequestException.class)
                .hasMessageContaining("Either an InstanceAccessControlAttributeConfiguration or an AccessControlAttributes property can be present in the schema, not both.");

    }

    @Test
    public void handleRequest_when_neither_instance_or_list_present_Fails() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();


        assertThatThrownBy(() -> handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger))
                .isInstanceOf(CfnInvalidRequestException.class)
                .hasMessageContaining("Resource model must contain an InstanceAccessControlAttributeConfiguration or an AccessControlAttributes property");
    }

    @Test
    public void handleRequest_when_Create_ConflictException_then_Failure() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfInstanceAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(createConfigurationRequest, proxyClient.client()::createInstanceAccessControlAttributeConfiguration))
                .thenThrow(ConflictException.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AlreadyExists);
    }

    @Test
    public void handleRequest_when_Create_AccessDeniedException_then_Failure() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfInstanceAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(createConfigurationRequest, proxyClient.client()::createInstanceAccessControlAttributeConfiguration))
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
    public void handleRequest_when_Create_ValidationException_then_Failure() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfInstanceAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(createConfigurationRequest, proxyClient.client()::createInstanceAccessControlAttributeConfiguration))
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
    public void handleRequest_when_Create_unexpected_exception_then_then_Failure() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfInstanceAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(createConfigurationRequest, proxyClient.client()::createInstanceAccessControlAttributeConfiguration))
                .thenThrow(SsoAdminException.builder().build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.GeneralServiceException);
    }

    @Test
    public void handleRequest_when_Create_ThrottlingException_then_Success() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfInstanceAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DescribeInstanceAccessControlAttributeConfigurationResponse describeResponse = DescribeInstanceAccessControlAttributeConfigurationResponse
                .builder()
                .instanceAccessControlAttributeConfiguration(ssoAccessControlAttributeConfiguration)
                .status(InstanceAccessControlAttributeConfigurationStatus.ENABLED)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(createConfigurationRequest, proxyClient.client()::createInstanceAccessControlAttributeConfiguration))
                .thenThrow(ThrottlingException.builder().build())
                .thenReturn(createConfigurationResponse);

        when(proxy.injectCredentialsAndInvokeV2(describeRequest, proxyClient.client()::describeInstanceAccessControlAttributeConfiguration))
                .thenReturn(describeResponse)
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
        assertThat(secondResponse.getResourceModel()).isEqualTo(expectedModel);
        assertThat(secondResponse.getResourceModels()).isNull();
        assertThat(secondResponse.getMessage()).isNull();
        assertThat(secondResponse.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_when_Create_InternalServerException_then_Success() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfInstanceAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DescribeInstanceAccessControlAttributeConfigurationResponse describeResponse = DescribeInstanceAccessControlAttributeConfigurationResponse
                .builder()
                .instanceAccessControlAttributeConfiguration(ssoAccessControlAttributeConfiguration)
                .status(InstanceAccessControlAttributeConfigurationStatus.ENABLED)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(createConfigurationRequest, proxyClient.client()::createInstanceAccessControlAttributeConfiguration))
                .thenThrow(InternalServerException.builder().build())
                .thenReturn(createConfigurationResponse);

        when(proxy.injectCredentialsAndInvokeV2(describeRequest, proxyClient.client()::describeInstanceAccessControlAttributeConfiguration))
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
        assertThat(secondResponse.getResourceModel()).isEqualTo(expectedModel);
        assertThat(secondResponse.getResourceModels()).isNull();
        assertThat(secondResponse.getMessage()).isNull();
        assertThat(secondResponse.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_when_Describe_AccessDeniedException_then_Failure() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfInstanceAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(createConfigurationRequest, proxyClient.client()::createInstanceAccessControlAttributeConfiguration))
                .thenReturn(createConfigurationResponse);

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
    public void handleRequest_when_Describe_ValidationException_then_Failure() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfInstanceAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(createConfigurationRequest, proxyClient.client()::createInstanceAccessControlAttributeConfiguration))
                .thenReturn(createConfigurationResponse);

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
    public void handleRequest_when_Describe_ResourceNotFoundException_then_Failure() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfInstanceAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(createConfigurationRequest, proxyClient.client()::createInstanceAccessControlAttributeConfiguration))
                .thenReturn(createConfigurationResponse);

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
    public void handleRequest_when_Describe_unexpected_exception_then_then_Failure() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfInstanceAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(createConfigurationRequest, proxyClient.client()::createInstanceAccessControlAttributeConfiguration))
                .thenReturn(createConfigurationResponse);

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

    @Test
    public void handleRequest_when_Update_InternalServerException_then_Success() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfInstanceAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DescribeInstanceAccessControlAttributeConfigurationResponse describeResponse = DescribeInstanceAccessControlAttributeConfigurationResponse
                .builder()
                .instanceAccessControlAttributeConfiguration(ssoAccessControlAttributeConfiguration)
                .status(InstanceAccessControlAttributeConfigurationStatus.ENABLED)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(createConfigurationRequest, proxyClient.client()::createInstanceAccessControlAttributeConfiguration))
                .thenReturn(createConfigurationResponse);

        when(proxy.injectCredentialsAndInvokeV2(describeRequest, proxyClient.client()::describeInstanceAccessControlAttributeConfiguration))
                .thenThrow(InternalServerException.builder().build())
                .thenReturn(describeResponse)
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
        assertThat(secondResponse.getResourceModel()).isEqualTo(expectedModel);
        assertThat(secondResponse.getResourceModels()).isNull();
        assertThat(secondResponse.getMessage()).isNull();
        assertThat(secondResponse.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_when_Update_ThrottlingException_then_Success() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfInstanceAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DescribeInstanceAccessControlAttributeConfigurationResponse describeResponse = DescribeInstanceAccessControlAttributeConfigurationResponse
                .builder()
                .instanceAccessControlAttributeConfiguration(ssoAccessControlAttributeConfiguration)
                .status(InstanceAccessControlAttributeConfigurationStatus.ENABLED)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(createConfigurationRequest, proxyClient.client()::createInstanceAccessControlAttributeConfiguration))
                .thenReturn(createConfigurationResponse);

        when(proxy.injectCredentialsAndInvokeV2(describeRequest, proxyClient.client()::describeInstanceAccessControlAttributeConfiguration))
                .thenThrow(ThrottlingException.builder().build())
                .thenReturn(describeResponse)
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
        assertThat(secondResponse.getResourceModel()).isEqualTo(expectedModel);
        assertThat(secondResponse.getResourceModels()).isNull();
        assertThat(secondResponse.getMessage()).isNull();
        assertThat(secondResponse.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_when_Describe_status_IN_PROGRES_then_Success() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfInstanceAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DescribeInstanceAccessControlAttributeConfigurationResponse describeInProgress = DescribeInstanceAccessControlAttributeConfigurationResponse
                .builder()
                .instanceAccessControlAttributeConfiguration(ssoAccessControlAttributeConfiguration)
                .status(InstanceAccessControlAttributeConfigurationStatus.CREATION_IN_PROGRESS)
                .build();

        final DescribeInstanceAccessControlAttributeConfigurationResponse describeResponse = DescribeInstanceAccessControlAttributeConfigurationResponse
                .builder()
                .instanceAccessControlAttributeConfiguration(ssoAccessControlAttributeConfiguration)
                .status(InstanceAccessControlAttributeConfigurationStatus.ENABLED)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(createConfigurationRequest, proxyClient.client()::createInstanceAccessControlAttributeConfiguration))
                .thenReturn(createConfigurationResponse);

        when(proxy.injectCredentialsAndInvokeV2(describeRequest, proxyClient.client()::describeInstanceAccessControlAttributeConfiguration))
                .thenReturn(describeInProgress)
                .thenReturn(describeResponse)
                .thenReturn(describeResponse);


        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(expectedModel);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_when_Describe_status_CREATION_FAILED_then_Failure() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceAccessControlAttributeConfiguration(cfInstanceAccessControlAttributeConfiguration)
                .instanceArn(SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DescribeInstanceAccessControlAttributeConfigurationResponse describeInProgress = DescribeInstanceAccessControlAttributeConfigurationResponse
                .builder()
                .instanceAccessControlAttributeConfiguration(ssoAccessControlAttributeConfiguration)
                .status(InstanceAccessControlAttributeConfigurationStatus.CREATION_IN_PROGRESS)
                .build();

        final DescribeInstanceAccessControlAttributeConfigurationResponse describeResponse = DescribeInstanceAccessControlAttributeConfigurationResponse
                .builder()
                .instanceAccessControlAttributeConfiguration(ssoAccessControlAttributeConfiguration)
                .status(InstanceAccessControlAttributeConfigurationStatus.CREATION_FAILED)
                .statusReason("test  reason")
                .build();

        when(proxy.injectCredentialsAndInvokeV2(createConfigurationRequest, proxyClient.client()::createInstanceAccessControlAttributeConfiguration))
                .thenReturn(createConfigurationResponse);

        when(proxy.injectCredentialsAndInvokeV2(describeRequest, proxyClient.client()::describeInstanceAccessControlAttributeConfiguration))
                .thenReturn(describeInProgress)
                .thenReturn(describeResponse);

        assertThatThrownBy(() -> {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
        }).isInstanceOf(CfnGeneralServiceException.class).hasMessageContaining("test  reason");
    }
}
