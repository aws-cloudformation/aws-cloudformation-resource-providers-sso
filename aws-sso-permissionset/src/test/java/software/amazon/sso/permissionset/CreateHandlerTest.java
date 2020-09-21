package software.amazon.sso.permissionset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.AttachManagedPolicyToPermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.AttachedManagedPolicy;
import software.amazon.awssdk.services.ssoadmin.model.ConflictException;
import software.amazon.awssdk.services.ssoadmin.model.CreatePermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.CreatePermissionSetResponse;
import software.amazon.awssdk.services.ssoadmin.model.DescribePermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.DescribePermissionSetResponse;
import software.amazon.awssdk.services.ssoadmin.model.GetInlinePolicyForPermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.GetInlinePolicyForPermissionSetResponse;
import software.amazon.awssdk.services.ssoadmin.model.ListManagedPoliciesInPermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.ListManagedPoliciesInPermissionSetResponse;
import software.amazon.awssdk.services.ssoadmin.model.PermissionSet;
import software.amazon.awssdk.services.ssoadmin.model.PutInlinePolicyToPermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.Tag;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static software.amazon.sso.permissionset.TestConstants.TEST_ADMIN_MANAGED_POLICY;
import static software.amazon.sso.permissionset.TestConstants.TEST_CONFLICT_EXCEPTION_MESSAGE;
import static software.amazon.sso.permissionset.TestConstants.TEST_INLINE_POLICY;
import static software.amazon.sso.permissionset.TestConstants.TEST_PERMISSION_SET_ARN;
import static software.amazon.sso.permissionset.TestConstants.TEST_PERMISSION_SET_DESCRIPTION;
import static software.amazon.sso.permissionset.TestConstants.TEST_PERMISSION_SET_NAME;
import static software.amazon.sso.permissionset.TestConstants.TEST_READONLY_POLICY;
import static software.amazon.sso.permissionset.TestConstants.TEST_RELAY_STATE;
import static software.amazon.sso.permissionset.TestConstants.TEST_SESSION_DURATION;
import static software.amazon.sso.permissionset.TestConstants.TEST_SSO_INSTANCE_ARN;
import static software.amazon.sso.permissionset.utils.Constants.MANAGED_POLICIES_LIMIT_EXCEED_MESSAGE;

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
        when(proxyClient.client()).thenReturn(sso);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final CreateHandler handler = new CreateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> covertedTags = new ArrayList<>();
        covertedTags.add(Tag.builder().key("key").value("value").build());

        List<String> managedPolicyArns = new ArrayList<>();
        managedPolicyArns.add(TEST_ADMIN_MANAGED_POLICY);
        managedPolicyArns.add(TEST_READONLY_POLICY);

        List<AttachedManagedPolicy> attachedManagedPolicies = new ArrayList<>();
        attachedManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_ADMIN_MANAGED_POLICY).build());
        attachedManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_READONLY_POLICY).build());

        final ResourceModel model = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(managedPolicyArns)
                .inlinePolicy(TEST_INLINE_POLICY)
                .tags(tags)
                .build();

        PermissionSet testPermissionSet = PermissionSet.builder()
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .name(TEST_PERMISSION_SET_NAME)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .relayState(TEST_RELAY_STATE)
                .sessionDuration(TEST_SESSION_DURATION)
                .build();

        CreatePermissionSetRequest psCreateRequest = CreatePermissionSetRequest.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .relayState(TEST_RELAY_STATE)
                .sessionDuration(TEST_SESSION_DURATION)
                .tags(covertedTags)
                .build();
        CreatePermissionSetResponse ssoResponse = CreatePermissionSetResponse.builder()
                .permissionSet(testPermissionSet)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(psCreateRequest, proxyClient.client()::createPermissionSet))
                .thenReturn(ssoResponse);

        DescribePermissionSetRequest psDescribeRequest = DescribePermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        DescribePermissionSetResponse psDescribeResponse = DescribePermissionSetResponse.builder()
                .permissionSet(testPermissionSet)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(psDescribeRequest, proxyClient.client()::describePermissionSet))
                .thenReturn(psDescribeResponse);

        ListManagedPoliciesInPermissionSetRequest listAPRequest = ListManagedPoliciesInPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListManagedPoliciesInPermissionSetResponse listAPResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(Collections.emptyList())
                .build();
        ListManagedPoliciesInPermissionSetResponse afterAttachListResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(attachedManagedPolicies)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAPRequest, proxyClient.client()::listManagedPoliciesInPermissionSet))
                .thenReturn(listAPResponse)
                .thenReturn(afterAttachListResponse);

        GetInlinePolicyForPermissionSetRequest getIPRequest = GetInlinePolicyForPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        GetInlinePolicyForPermissionSetResponse getIPResponse = GetInlinePolicyForPermissionSetResponse.builder()
                .inlinePolicy(TEST_INLINE_POLICY)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(getIPRequest, proxyClient.client()::getInlinePolicyForPermissionSet))
                .thenReturn(getIPResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        ArgumentCaptor<AttachManagedPolicyToPermissionSetRequest> attachPolicyArgument = ArgumentCaptor.forClass(AttachManagedPolicyToPermissionSetRequest.class);

        verify(proxyClient.client(), times(2)).attachManagedPolicyToPermissionSet(attachPolicyArgument.capture());
        List<AttachManagedPolicyToPermissionSetRequest> attachPolicyRequests = attachPolicyArgument.getAllValues();
        assertThat(attachPolicyRequests.get(0).instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(attachPolicyRequests.get(0).permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(attachPolicyRequests.get(0).managedPolicyArn()).isEqualTo(TEST_READONLY_POLICY);

        assertThat(attachPolicyRequests.get(1).instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(attachPolicyRequests.get(1).permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(attachPolicyRequests.get(1).managedPolicyArn()).isEqualTo(TEST_ADMIN_MANAGED_POLICY);

        ArgumentCaptor<PutInlinePolicyToPermissionSetRequest> putInlinePolicyArgument = ArgumentCaptor.forClass(PutInlinePolicyToPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).putInlinePolicyToPermissionSet(putInlinePolicyArgument.capture());
        assertThat(putInlinePolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(putInlinePolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(putInlinePolicyArgument.getValue().inlinePolicy()).isEqualTo(TEST_INLINE_POLICY);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_SimpleSuccess_Without_AttachManagedPolicies_InlinePolicy() {
        final CreateHandler handler = new CreateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> covertedTags = new ArrayList<>();
        covertedTags.add(Tag.builder().key("key").value("value").build());

        final ResourceModel model = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(Collections.emptyList())
                .tags(tags)
                .build();

        PermissionSet testPermissionSet = PermissionSet.builder()
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .name(TEST_PERMISSION_SET_NAME)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .relayState(TEST_RELAY_STATE)
                .sessionDuration(TEST_SESSION_DURATION)
                .build();

        CreatePermissionSetRequest psCreateRequest = CreatePermissionSetRequest.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .relayState(TEST_RELAY_STATE)
                .sessionDuration(TEST_SESSION_DURATION)
                .tags(covertedTags)
                .build();
        CreatePermissionSetResponse ssoResponse = CreatePermissionSetResponse.builder()
                .permissionSet(testPermissionSet)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(psCreateRequest, proxyClient.client()::createPermissionSet))
                .thenReturn(ssoResponse);

        DescribePermissionSetRequest psDescribeRequest = DescribePermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        DescribePermissionSetResponse psDescribeResult = DescribePermissionSetResponse.builder()
                .permissionSet(testPermissionSet)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(psDescribeRequest, proxyClient.client()::describePermissionSet))
                .thenReturn(psDescribeResult);

        ListManagedPoliciesInPermissionSetRequest listRequest = ListManagedPoliciesInPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListManagedPoliciesInPermissionSetResponse listResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(Collections.emptyList())
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listRequest, proxyClient.client()::listManagedPoliciesInPermissionSet))
                .thenReturn(listResponse);

        ListManagedPoliciesInPermissionSetRequest listAPRequest = ListManagedPoliciesInPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListManagedPoliciesInPermissionSetResponse listAPResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(Collections.emptyList())
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAPRequest, proxyClient.client()::listManagedPoliciesInPermissionSet))
                .thenReturn(listAPResponse);

        GetInlinePolicyForPermissionSetRequest getIPRequest = GetInlinePolicyForPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        GetInlinePolicyForPermissionSetResponse getIPResponse = GetInlinePolicyForPermissionSetResponse.builder().build();
        when(proxy.injectCredentialsAndInvokeV2(getIPRequest, proxyClient.client()::getInlinePolicyForPermissionSet))
                .thenReturn(getIPResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(proxyClient.client(), never()).attachManagedPolicyToPermissionSet(any(AttachManagedPolicyToPermissionSetRequest.class));

        verify(proxyClient.client(), never()).putInlinePolicyToPermissionSet(any(PutInlinePolicyToPermissionSetRequest.class));

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_Failure_ManagedPolicyExceedLimit() {
        final CreateHandler handler = new CreateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> covertedTags = new ArrayList<>();
        covertedTags.add(Tag.builder().key("key").value("value").build());

        List<String> managedPolicyArns = new ArrayList<>();
        for(int i = 0; i < 21; i ++) {
            managedPolicyArns.add("ManagedPolicyArn" + i);
        }

        final ResourceModel model = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(managedPolicyArns)
                .tags(tags)
                .build();

        PermissionSet testPermissionSet = PermissionSet.builder()
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .name(TEST_PERMISSION_SET_NAME)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .relayState(TEST_RELAY_STATE)
                .sessionDuration(TEST_SESSION_DURATION)
                .build();

        CreatePermissionSetRequest psCreateRequest = CreatePermissionSetRequest.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .relayState(TEST_RELAY_STATE)
                .sessionDuration(TEST_SESSION_DURATION)
                .tags(covertedTags)
                .build();

        CreatePermissionSetResponse ssoResponse = CreatePermissionSetResponse.builder()
                .permissionSet(testPermissionSet)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(psCreateRequest, proxyClient.client()::createPermissionSet))
                .thenReturn(ssoResponse);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        assertThatThrownBy(() -> {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
        }).isInstanceOf(CfnServiceLimitExceededException.class).hasMessageContaining(MANAGED_POLICIES_LIMIT_EXCEED_MESSAGE);
    }

    @Test
    public void handleRequest_Failed_ResourceExistOrConflict() {
        final CreateHandler handler = new CreateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> covertedTags = new ArrayList<>();
        covertedTags.add(Tag.builder().key("key").value("value").build());

        final ResourceModel model = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .tags(tags)
                .build();

        CreatePermissionSetRequest psCreateRequest = CreatePermissionSetRequest.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .relayState(TEST_RELAY_STATE)
                .sessionDuration(TEST_SESSION_DURATION)
                .tags(covertedTags)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(psCreateRequest, proxyClient.client()::createPermissionSet))
                .thenThrow(ConflictException.builder().message(TEST_CONFLICT_EXCEPTION_MESSAGE).build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(proxyClient.client(), never()).attachManagedPolicyToPermissionSet(any(AttachManagedPolicyToPermissionSetRequest.class));

        verify(proxyClient.client(), never()).putInlinePolicyToPermissionSet(any(PutInlinePolicyToPermissionSetRequest.class));

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).contains(TEST_CONFLICT_EXCEPTION_MESSAGE);
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AlreadyExists);
    }
}
