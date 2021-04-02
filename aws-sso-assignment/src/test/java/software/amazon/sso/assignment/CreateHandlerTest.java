package software.amazon.sso.assignment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.AccountAssignment;
import software.amazon.awssdk.services.ssoadmin.model.AccountAssignmentOperationStatus;
import software.amazon.awssdk.services.ssoadmin.model.ConflictException;
import software.amazon.awssdk.services.ssoadmin.model.CreateAccountAssignmentRequest;
import software.amazon.awssdk.services.ssoadmin.model.CreateAccountAssignmentResponse;
import software.amazon.awssdk.services.ssoadmin.model.DescribeAccountAssignmentCreationStatusRequest;
import software.amazon.awssdk.services.ssoadmin.model.DescribeAccountAssignmentCreationStatusResponse;
import software.amazon.awssdk.services.ssoadmin.model.InternalServerException;
import software.amazon.awssdk.services.ssoadmin.model.ListAccountAssignmentsRequest;
import software.amazon.awssdk.services.ssoadmin.model.ListAccountAssignmentsResponse;
import software.amazon.awssdk.services.ssoadmin.model.PrincipalType;
import software.amazon.awssdk.services.ssoadmin.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.ssoadmin.model.StatusValues;
import software.amazon.awssdk.services.ssoadmin.model.TargetType;
import software.amazon.awssdk.services.ssoadmin.model.ThrottlingException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static software.amazon.sso.assignment.TestConstants.TEST_PERMISSION_SET_ARN;
import static software.amazon.sso.assignment.TestConstants.TEST_PRINCIPAL_ID;
import static software.amazon.sso.assignment.TestConstants.TEST_REQUEST_ID;
import static software.amazon.sso.assignment.TestConstants.TEST_SSO_INSTANCE_ARN;
import static software.amazon.sso.assignment.TestConstants.TEST_TARGET_ID;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<SsoAdminClient> proxyClient;

    @Mock
    SsoAdminClient sso;

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        sso = mock(SsoAdminClient.class);
        proxyClient = MOCK_PROXY(proxy, sso);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(TargetType.AWS_ACCOUNT.toString())
                .targetId(TEST_TARGET_ID)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        AccountAssignment assignment = AccountAssignment.builder()
                .accountId(TEST_TARGET_ID)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        ListAccountAssignmentsRequest listAssignRequest = ListAccountAssignmentsRequest.builder()
                .accountId(TEST_TARGET_ID)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListAccountAssignmentsResponse listAssignmentEmptyResponse = ListAccountAssignmentsResponse.builder().build();
        when(proxy.injectCredentialsAndInvokeV2(listAssignRequest, proxyClient.client()::listAccountAssignments))
                .thenReturn(listAssignmentEmptyResponse)
                .thenReturn(ListAccountAssignmentsResponse.builder().accountAssignments(assignment).build());

        CreateAccountAssignmentRequest createAssignmentRequest = CreateAccountAssignmentRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(TargetType.AWS_ACCOUNT.toString())
                .targetId(TEST_TARGET_ID)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(createAssignmentRequest, proxyClient.client()::createAccountAssignment))
                .thenReturn(CreateAccountAssignmentResponse.builder()
                        .accountAssignmentCreationStatus(AccountAssignmentOperationStatus.builder()
                                .status(StatusValues.IN_PROGRESS)
                                .requestId(TEST_REQUEST_ID)
                                .build())
                        .build());

        when(proxy.injectCredentialsAndInvokeV2(DescribeAccountAssignmentCreationStatusRequest.builder()
                .accountAssignmentCreationRequestId(TEST_REQUEST_ID)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .build(), proxyClient.client()::describeAccountAssignmentCreationStatus))
                .thenReturn(DescribeAccountAssignmentCreationStatusResponse.builder()
                        .accountAssignmentCreationStatus(AccountAssignmentOperationStatus.builder()
                                .status(StatusValues.IN_PROGRESS)
                                .build())
                        .build())
                .thenReturn(DescribeAccountAssignmentCreationStatusResponse.builder()
                        .accountAssignmentCreationStatus(AccountAssignmentOperationStatus.builder()
                                .status(StatusValues.SUCCEEDED)
                                .build())
                        .build());

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
    public void handleRequest_Retryable_Conflict() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(TargetType.AWS_ACCOUNT.toString())
                .targetId(TEST_TARGET_ID)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        AccountAssignment assignment = AccountAssignment.builder()
                .accountId(TEST_TARGET_ID)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        ListAccountAssignmentsRequest listAssignRequest = ListAccountAssignmentsRequest.builder()
                .accountId(TEST_TARGET_ID)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListAccountAssignmentsResponse listAssignmentEmptyResponse = ListAccountAssignmentsResponse.builder().build();
        when(proxy.injectCredentialsAndInvokeV2(listAssignRequest, proxyClient.client()::listAccountAssignments))
                .thenReturn(listAssignmentEmptyResponse)
                .thenReturn(ListAccountAssignmentsResponse.builder().accountAssignments(assignment).build());

        CreateAccountAssignmentRequest createAssignmentRequest = CreateAccountAssignmentRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(TargetType.AWS_ACCOUNT.toString())
                .targetId(TEST_TARGET_ID)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(createAssignmentRequest, proxyClient.client()::createAccountAssignment))
                .thenThrow(ConflictException.builder().message("Conflict operation").build());

        CallbackContext context = new CallbackContext();
        context.setHandlerInvoked(true);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, context, proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds() >= 60 && response.getCallbackDelaySeconds() <= 300).isEqualTo(true);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_Retryable_Throttled() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(TargetType.AWS_ACCOUNT.toString())
                .targetId(TEST_TARGET_ID)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        AccountAssignment assignment = AccountAssignment.builder()
                .accountId(TEST_TARGET_ID)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        ListAccountAssignmentsRequest listAssignRequest = ListAccountAssignmentsRequest.builder()
                .accountId(TEST_TARGET_ID)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListAccountAssignmentsResponse listAssignmentEmptyResponse = ListAccountAssignmentsResponse.builder().build();
        when(proxy.injectCredentialsAndInvokeV2(listAssignRequest, proxyClient.client()::listAccountAssignments))
                .thenReturn(listAssignmentEmptyResponse)
                .thenReturn(ListAccountAssignmentsResponse.builder().accountAssignments(assignment).build());

        CreateAccountAssignmentRequest createAssignmentRequest = CreateAccountAssignmentRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(TargetType.AWS_ACCOUNT.toString())
                .targetId(TEST_TARGET_ID)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(createAssignmentRequest, proxyClient.client()::createAccountAssignment))
                .thenThrow(ThrottlingException.builder().message("Operation throttled.").build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds() >= 60 && response.getCallbackDelaySeconds() <= 300).isEqualTo(true);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_Retryable_ISE() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(TargetType.AWS_ACCOUNT.toString())
                .targetId(TEST_TARGET_ID)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        AccountAssignment assignment = AccountAssignment.builder()
                .accountId(TEST_TARGET_ID)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        ListAccountAssignmentsRequest listAssignRequest = ListAccountAssignmentsRequest.builder()
                .accountId(TEST_TARGET_ID)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListAccountAssignmentsResponse listAssignmentEmptyResponse = ListAccountAssignmentsResponse.builder().build();
        when(proxy.injectCredentialsAndInvokeV2(listAssignRequest, proxyClient.client()::listAccountAssignments))
                .thenReturn(listAssignmentEmptyResponse)
                .thenReturn(ListAccountAssignmentsResponse.builder().accountAssignments(assignment).build());

        CreateAccountAssignmentRequest createAssignmentRequest = CreateAccountAssignmentRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(TargetType.AWS_ACCOUNT.toString())
                .targetId(TEST_TARGET_ID)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(createAssignmentRequest, proxyClient.client()::createAccountAssignment))
                .thenThrow(InternalServerException.builder().message("ISE.").build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds() >= 5 && response.getCallbackDelaySeconds() <= 100).isEqualTo(true);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_Retryable_NonRetryable() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(TargetType.AWS_ACCOUNT.toString())
                .targetId(TEST_TARGET_ID)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        AccountAssignment assignment = AccountAssignment.builder()
                .accountId(TEST_TARGET_ID)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        ListAccountAssignmentsRequest listAssignRequest = ListAccountAssignmentsRequest.builder()
                .accountId(TEST_TARGET_ID)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListAccountAssignmentsResponse listAssignmentEmptyResponse = ListAccountAssignmentsResponse.builder().build();
        when(proxy.injectCredentialsAndInvokeV2(listAssignRequest, proxyClient.client()::listAccountAssignments))
                .thenReturn(listAssignmentEmptyResponse)
                .thenReturn(ListAccountAssignmentsResponse.builder().accountAssignments(assignment).build());

        CreateAccountAssignmentRequest createAssignmentRequest = CreateAccountAssignmentRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(TargetType.AWS_ACCOUNT.toString())
                .targetId(TEST_TARGET_ID)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(createAssignmentRequest, proxyClient.client()::createAccountAssignment))
                .thenThrow(ServiceQuotaExceededException.builder().message("NonRetryable.").build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).contains("NonRetryable.");
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.GeneralServiceException);
    }

    @Test
    public void handleRequest_CreationFailed() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(TargetType.AWS_ACCOUNT.toString())
                .targetId(TEST_TARGET_ID)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        AccountAssignment assignment = AccountAssignment.builder()
                .accountId(TEST_TARGET_ID)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        ListAccountAssignmentsRequest listAssignRequest = ListAccountAssignmentsRequest.builder()
                .accountId(TEST_TARGET_ID)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListAccountAssignmentsResponse listAssignmentEmptyResponse = ListAccountAssignmentsResponse.builder().build();
        when(proxy.injectCredentialsAndInvokeV2(listAssignRequest, proxyClient.client()::listAccountAssignments))
                .thenReturn(listAssignmentEmptyResponse)
                .thenReturn(ListAccountAssignmentsResponse.builder().accountAssignments(assignment).build());

        CreateAccountAssignmentRequest createAssignmentRequest = CreateAccountAssignmentRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(TargetType.AWS_ACCOUNT.toString())
                .targetId(TEST_TARGET_ID)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(createAssignmentRequest, proxyClient.client()::createAccountAssignment))
                .thenReturn(CreateAccountAssignmentResponse.builder()
                        .accountAssignmentCreationStatus(AccountAssignmentOperationStatus.builder()
                                .status(StatusValues.IN_PROGRESS)
                                .requestId(TEST_REQUEST_ID)
                                .build())
                        .build());
        when(proxy.injectCredentialsAndInvokeV2(DescribeAccountAssignmentCreationStatusRequest.builder()
                .accountAssignmentCreationRequestId(TEST_REQUEST_ID)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .build(), proxyClient.client()::describeAccountAssignmentCreationStatus))
                .thenReturn(DescribeAccountAssignmentCreationStatusResponse.builder()
                        .accountAssignmentCreationStatus(AccountAssignmentOperationStatus.builder()
                                .status(StatusValues.FAILED)
                                .failureReason("Action failed due to issue.")
                                .build())
                        .build());

        assertThatThrownBy(() -> {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
        }).isInstanceOf(CfnGeneralServiceException.class).hasMessageContaining("Request " + TEST_REQUEST_ID + " failed due to: Action failed due to issue.");
    }

    @Test
    public void handleRequest_Failed_AssignmentAlreadyExist() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(TargetType.AWS_ACCOUNT.toString())
                .targetId(TEST_TARGET_ID)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        AccountAssignment assignment = AccountAssignment.builder()
                .accountId(TEST_TARGET_ID)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        ListAccountAssignmentsRequest listAssignRequest = ListAccountAssignmentsRequest.builder()
                .accountId(TEST_TARGET_ID)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAssignRequest, proxyClient.client()::listAccountAssignments))
                .thenReturn(ListAccountAssignmentsResponse.builder().accountAssignments(assignment).build());

        assertThatThrownBy(() -> {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
        }).isInstanceOf(CfnAlreadyExistsException.class).hasMessageContaining("Assignment already exists. Can't process creation.");
    }
}
