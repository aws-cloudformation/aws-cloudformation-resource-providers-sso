package software.amazon.sso.assignment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.AccountAssignment;
import software.amazon.awssdk.services.ssoadmin.model.InternalServerException;
import software.amazon.awssdk.services.ssoadmin.model.ListAccountAssignmentsRequest;
import software.amazon.awssdk.services.ssoadmin.model.ListAccountAssignmentsResponse;
import software.amazon.awssdk.services.ssoadmin.model.PrincipalType;
import software.amazon.awssdk.services.ssoadmin.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ssoadmin.model.TargetType;
import software.amazon.awssdk.services.ssoadmin.model.ThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static software.amazon.sso.assignment.TestConstants.TEST_PERMISSION_SET_ARN;
import static software.amazon.sso.assignment.TestConstants.TEST_PRINCIPAL_ID;
import static software.amazon.sso.assignment.TestConstants.TEST_PRINCIPAL_ID_2;
import static software.amazon.sso.assignment.TestConstants.TEST_SSO_INSTANCE_ARN;
import static software.amazon.sso.assignment.TestConstants.TEST_TARGET_ID;
import static software.amazon.sso.assignment.TestConstants.TEST_TARGET_ID_2;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest extends AbstractTestBase {

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
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(TargetType.AWS_ACCOUNT.toString())
                .targetId(TEST_TARGET_ID)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        AccountAssignment assignment = AccountAssignment.builder()
                .accountId(TEST_TARGET_ID)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        ListAccountAssignmentsRequest listAssignRequest = ListAccountAssignmentsRequest.builder()
                .accountId(TEST_TARGET_ID)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListAccountAssignmentsResponse listAssignmentsResponse = ListAccountAssignmentsResponse.builder()
                .accountAssignments(assignment)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAssignRequest, proxyClient.client()::listAccountAssignments))
                .thenReturn(listAssignmentsResponse);

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
    public void handleRequest_Retry_Throttling() {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(TargetType.AWS_ACCOUNT.toString())
                .targetId(TEST_TARGET_ID)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        AccountAssignment assignment = AccountAssignment.builder()
                .accountId(TEST_TARGET_ID)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        ListAccountAssignmentsRequest listAssignRequest = ListAccountAssignmentsRequest.builder()
                .accountId(TEST_TARGET_ID)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAssignRequest, proxyClient.client()::listAccountAssignments))
                .thenThrow(ThrottlingException.builder().message("Operation throttled.").build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(5);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_Retry_ISE() {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(TargetType.AWS_ACCOUNT.toString())
                .targetId(TEST_TARGET_ID)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        AccountAssignment assignment = AccountAssignment.builder()
                .accountId(TEST_TARGET_ID)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        ListAccountAssignmentsRequest listAssignRequest = ListAccountAssignmentsRequest.builder()
                .accountId(TEST_TARGET_ID)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAssignRequest, proxyClient.client()::listAccountAssignments))
                .thenThrow(InternalServerException.builder().message("ISE.").build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(5);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }


    @Test
    public void handleRequest_Failed_ResourceNotFound() {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(TargetType.AWS_ACCOUNT.toString())
                .targetId(TEST_TARGET_ID)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        AccountAssignment accountAssignment = AccountAssignment.builder()
                .accountId(TEST_TARGET_ID_2)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .principalType(PrincipalType.USER.toString())
                .principalId(TEST_PRINCIPAL_ID_2)
                .build();

        ListAccountAssignmentsRequest listAssignRequest = ListAccountAssignmentsRequest.builder()
                .accountId(TEST_TARGET_ID)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListAccountAssignmentsRequest listAssignSecondRequest = ListAccountAssignmentsRequest.builder()
                .accountId(TEST_TARGET_ID)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .nextToken("nextToken")
                .build();
        ListAccountAssignmentsResponse listAssignmentResult = ListAccountAssignmentsResponse.builder()
                .accountAssignments(accountAssignment)
                .nextToken("nextToken")
                .build();
        ListAccountAssignmentsResponse listAssignmenEmptytResult = ListAccountAssignmentsResponse.builder()
                .build();

        when(proxy.injectCredentialsAndInvokeV2(listAssignRequest, proxyClient.client()::listAccountAssignments))
                .thenReturn(listAssignmentResult);
        when(proxy.injectCredentialsAndInvokeV2(listAssignSecondRequest, proxyClient.client()::listAccountAssignments))
                .thenReturn(listAssignmenEmptytResult);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).contains("Assignment not exists for given entity.");
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }

    @Test
    public void handleRequest_Failed_ResourceNotFoundException() {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(TargetType.AWS_ACCOUNT.toString())
                .targetId(TEST_TARGET_ID)
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
                .thenThrow(ResourceNotFoundException.builder().message("Resource not found.").build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).contains("Assignment not exists for given entity.");
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }
}
