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
import software.amazon.awssdk.services.ssoadmin.model.DeleteInlinePolicyFromPermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.DescribePermissionSetProvisioningStatusRequest;
import software.amazon.awssdk.services.ssoadmin.model.DescribePermissionSetProvisioningStatusResponse;
import software.amazon.awssdk.services.ssoadmin.model.DescribePermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.DescribePermissionSetResponse;
import software.amazon.awssdk.services.ssoadmin.model.DetachManagedPolicyFromPermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.GetInlinePolicyForPermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.GetInlinePolicyForPermissionSetResponse;
import software.amazon.awssdk.services.ssoadmin.model.InternalServerException;
import software.amazon.awssdk.services.ssoadmin.model.ListManagedPoliciesInPermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.ListManagedPoliciesInPermissionSetResponse;
import software.amazon.awssdk.services.ssoadmin.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.ssoadmin.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.ssoadmin.model.PermissionSet;
import software.amazon.awssdk.services.ssoadmin.model.PermissionSetProvisioningStatus;
import software.amazon.awssdk.services.ssoadmin.model.ProvisionPermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.ProvisionPermissionSetResponse;
import software.amazon.awssdk.services.ssoadmin.model.ProvisionTargetType;
import software.amazon.awssdk.services.ssoadmin.model.PutInlinePolicyToPermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ssoadmin.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.ssoadmin.model.StatusValues;
import software.amazon.awssdk.services.ssoadmin.model.Tag;
import software.amazon.awssdk.services.ssoadmin.model.TagResourceRequest;
import software.amazon.awssdk.services.ssoadmin.model.ThrottlingException;
import software.amazon.awssdk.services.ssoadmin.model.UntagResourceRequest;
import software.amazon.awssdk.services.ssoadmin.model.UpdatePermissionSetRequest;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static software.amazon.sso.permissionset.TestConstants.ISE_MESSAGE;
import static software.amazon.sso.permissionset.TestConstants.TEST_ADMIN_MANAGED_POLICY;
import static software.amazon.sso.permissionset.TestConstants.TEST_INLINE_POLICY_2;
import static software.amazon.sso.permissionset.TestConstants.TEST_PERMISSION_SET_ARN;
import static software.amazon.sso.permissionset.TestConstants.TEST_PERMISSION_SET_DESCRIPTION;
import static software.amazon.sso.permissionset.TestConstants.TEST_PERMISSION_SET_NAME;
import static software.amazon.sso.permissionset.TestConstants.TEST_READONLY_POLICY;
import static software.amazon.sso.permissionset.TestConstants.TEST_RELAY_STATE;
import static software.amazon.sso.permissionset.TestConstants.TEST_SESSION_DURATION;
import static software.amazon.sso.permissionset.TestConstants.TEST_SSO_INSTANCE_ARN;
import static software.amazon.sso.permissionset.TestConstants.THROTTLING_MESSAGE;
import static software.amazon.sso.permissionset.utils.Constants.MANAGED_POLICIES_LIMIT_EXCEED_MESSAGE;

@ExtendWith(MockitoExtension.class)
public class UpdateHandlerTest extends AbstractTestBase {

    private static final String TEST_REQUEST_ID = "request_id";

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<SsoAdminClient> proxyClient;

    @Mock
    SsoAdminClient sso;

    @BeforeEach
    public void setup() {
        initMocks(proxyClient);
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        sso = mock(SsoAdminClient.class);
        when(proxyClient.client()).thenReturn(sso);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final UpdateHandler handler = new UpdateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> ssoTags = new ArrayList<>();
        ssoTags.add(Tag.builder().key("key1").value("value1").build());

        List<String> newManagedPolicyArns = new ArrayList<>();
        newManagedPolicyArns.add(TEST_ADMIN_MANAGED_POLICY);
        List<AttachedManagedPolicy> newManagedPolicies = new ArrayList<>();
        newManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_ADMIN_MANAGED_POLICY).build());

        List<String> existingManagedPolicyArns = new ArrayList<>();
        existingManagedPolicyArns.add(TEST_READONLY_POLICY);
        List<AttachedManagedPolicy> existingManagedPolicies = new ArrayList<>();
        existingManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_READONLY_POLICY).build());

        final ResourceModel inputModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(newManagedPolicyArns)
                .inlinePolicy(TEST_INLINE_POLICY_2)
                .tags(tags)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .build();

        ListTagsForResourceRequest listTagsRequest = ListTagsForResourceRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .resourceArn(TEST_PERMISSION_SET_ARN)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listTagsRequest, proxyClient.client()::listTagsForResource))
                .thenReturn(ListTagsForResourceResponse.builder().tags(ssoTags).build());

        PermissionSet testPermissionSet = PermissionSet.builder()
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .name(TEST_PERMISSION_SET_NAME)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .relayState(TEST_RELAY_STATE)
                .sessionDuration(TEST_SESSION_DURATION)
                .build();

        DescribePermissionSetRequest psDescribeRequest = DescribePermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        DescribePermissionSetResponse psDescribeResult = DescribePermissionSetResponse.builder()
                .permissionSet(testPermissionSet)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(psDescribeRequest, proxyClient.client()::describePermissionSet))
                .thenReturn(psDescribeResult);

        ListManagedPoliciesInPermissionSetRequest listAPRequest = ListManagedPoliciesInPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListManagedPoliciesInPermissionSetResponse listAttachedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(existingManagedPolicies)
                .build();
        ListManagedPoliciesInPermissionSetResponse listUpdatedManagedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(newManagedPolicies)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAPRequest, proxyClient.client()::listManagedPoliciesInPermissionSet))
                .thenReturn(listAttachedPoliciesResponse)
                .thenReturn(listUpdatedManagedPoliciesResponse);

        GetInlinePolicyForPermissionSetRequest getIPRequest = GetInlinePolicyForPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        GetInlinePolicyForPermissionSetResponse getIPResult = GetInlinePolicyForPermissionSetResponse.builder()
                .inlinePolicy(TEST_INLINE_POLICY_2)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(getIPRequest, proxyClient.client()::getInlinePolicyForPermissionSet))
                .thenReturn(getIPResult);

        ProvisionPermissionSetRequest provisionPsRequest = ProvisionPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(ProvisionTargetType.ALL_PROVISIONED_ACCOUNTS)
                .build();
        ProvisionPermissionSetResponse provisionPsResponse = ProvisionPermissionSetResponse.builder()
                .permissionSetProvisioningStatus(PermissionSetProvisioningStatus.builder().status(StatusValues.IN_PROGRESS).requestId(TEST_REQUEST_ID).build())
                .build();
        when(proxy.injectCredentialsAndInvokeV2(provisionPsRequest, proxyClient.client()::provisionPermissionSet))
                .thenReturn(provisionPsResponse);

        DescribePermissionSetProvisioningStatusRequest decribePsProvisionRequest = DescribePermissionSetProvisioningStatusRequest.builder()
                .provisionPermissionSetRequestId(TEST_REQUEST_ID)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .build();
        DescribePermissionSetProvisioningStatusResponse describePsProvisionInProgressResponse = DescribePermissionSetProvisioningStatusResponse.builder()
                .permissionSetProvisioningStatus(PermissionSetProvisioningStatus.builder().status(StatusValues.IN_PROGRESS).requestId(TEST_REQUEST_ID).build())
                .build();
        DescribePermissionSetProvisioningStatusResponse describePsProvisionResponse = DescribePermissionSetProvisioningStatusResponse.builder()
                .permissionSetProvisioningStatus(PermissionSetProvisioningStatus.builder().status(StatusValues.SUCCEEDED).requestId(TEST_REQUEST_ID).build())
                .build();
        when(proxy.injectCredentialsAndInvokeV2(decribePsProvisionRequest, proxyClient.client()::describePermissionSetProvisioningStatus))
                .thenReturn(describePsProvisionInProgressResponse)
                .thenReturn(describePsProvisionResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        ArgumentCaptor<UntagResourceRequest> untagArgument = ArgumentCaptor.forClass(UntagResourceRequest.class);
        verify(proxyClient.client(), times(1)).untagResource(untagArgument.capture());
        assertThat(untagArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(untagArgument.getValue().resourceArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(untagArgument.getValue().tagKeys()).isEqualTo(Arrays.asList("key1"));

        ArgumentCaptor<TagResourceRequest> tagArgument = ArgumentCaptor.forClass(TagResourceRequest.class);
        verify(proxyClient.client(), times(1)).tagResource(tagArgument.capture());
        assertThat(tagArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(tagArgument.getValue().resourceArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(tagArgument.getValue().tags()).isEqualTo(Translator.ConvertToSSOTag(tags));

        ArgumentCaptor<DetachManagedPolicyFromPermissionSetRequest> detachPolicyArgument = ArgumentCaptor.forClass(DetachManagedPolicyFromPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).detachManagedPolicyFromPermissionSet(detachPolicyArgument.capture());
        assertThat(detachPolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(detachPolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(detachPolicyArgument.getValue().managedPolicyArn()).isEqualTo(TEST_READONLY_POLICY);

        ArgumentCaptor<AttachManagedPolicyToPermissionSetRequest> attachPolicyArgument = ArgumentCaptor.forClass(AttachManagedPolicyToPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).attachManagedPolicyToPermissionSet(attachPolicyArgument.capture());
        assertThat(attachPolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(attachPolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(attachPolicyArgument.getValue().managedPolicyArn()).isEqualTo(TEST_ADMIN_MANAGED_POLICY);

        ArgumentCaptor<PutInlinePolicyToPermissionSetRequest> putInlinePolicyArgument = ArgumentCaptor.forClass(PutInlinePolicyToPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).putInlinePolicyToPermissionSet(putInlinePolicyArgument.capture());
        assertThat(putInlinePolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(putInlinePolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(putInlinePolicyArgument.getValue().inlinePolicy()).isEqualTo(TEST_INLINE_POLICY_2);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_SimpleSuccess_Delete_InlinePolicy() {
        final UpdateHandler handler = new UpdateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> covertedTags = new ArrayList<>();
        covertedTags.add(Tag.builder().key("key1").value("value1").build());

        List<String> newManagedPolicyArns = new ArrayList<>();
        newManagedPolicyArns.add(TEST_ADMIN_MANAGED_POLICY);
        List<AttachedManagedPolicy> newManagedPolicies = new ArrayList<>();
        newManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_ADMIN_MANAGED_POLICY).build());

        List<String> existingManagedPolicyArns = new ArrayList<>();
        existingManagedPolicyArns.add(TEST_READONLY_POLICY);
        List<AttachedManagedPolicy> existingManagedPolicies = new ArrayList<>();
        existingManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_READONLY_POLICY).build());

        final ResourceModel inputModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(newManagedPolicyArns)
                .tags(tags)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .build();

        ListTagsForResourceRequest listTagsRequest = ListTagsForResourceRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .resourceArn(TEST_PERMISSION_SET_ARN)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listTagsRequest, proxyClient.client()::listTagsForResource))
                .thenReturn(ListTagsForResourceResponse.builder().tags(covertedTags).build());

        PermissionSet testPermissionSet = PermissionSet.builder()
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .name(TEST_PERMISSION_SET_NAME)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .relayState(TEST_RELAY_STATE)
                .sessionDuration(TEST_SESSION_DURATION)
                .build();

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
        ListManagedPoliciesInPermissionSetResponse listAttachedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(existingManagedPolicies)
                .build();
        ListManagedPoliciesInPermissionSetResponse listUpdatedManagedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(newManagedPolicies)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAPRequest, proxyClient.client()::listManagedPoliciesInPermissionSet))
                .thenReturn(listAttachedPoliciesResponse)
                .thenReturn(listUpdatedManagedPoliciesResponse);

        GetInlinePolicyForPermissionSetRequest getIPRequest = GetInlinePolicyForPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        GetInlinePolicyForPermissionSetResponse getIPResult = GetInlinePolicyForPermissionSetResponse.builder().build();
        when(proxy.injectCredentialsAndInvokeV2(getIPRequest, proxyClient.client()::getInlinePolicyForPermissionSet))
                .thenReturn(getIPResult);

        ProvisionPermissionSetRequest provisionPsRequest = ProvisionPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(ProvisionTargetType.ALL_PROVISIONED_ACCOUNTS)
                .build();
        ProvisionPermissionSetResponse provisionPsResult = ProvisionPermissionSetResponse.builder()
                .permissionSetProvisioningStatus(PermissionSetProvisioningStatus.builder().status(StatusValues.IN_PROGRESS).requestId(TEST_REQUEST_ID).build())
                .build();
        when(proxy.injectCredentialsAndInvokeV2(provisionPsRequest, proxyClient.client()::provisionPermissionSet))
                .thenReturn(provisionPsResult);

        DescribePermissionSetProvisioningStatusRequest decribePsProvisionRequest = DescribePermissionSetProvisioningStatusRequest.builder()
                .provisionPermissionSetRequestId(TEST_REQUEST_ID)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .build();
        DescribePermissionSetProvisioningStatusResponse describePsProvisionResult = DescribePermissionSetProvisioningStatusResponse.builder()
                .permissionSetProvisioningStatus(PermissionSetProvisioningStatus.builder().status(StatusValues.SUCCEEDED).requestId(TEST_REQUEST_ID).build())
                .build();
        when(proxy.injectCredentialsAndInvokeV2(decribePsProvisionRequest, proxyClient.client()::describePermissionSetProvisioningStatus))
                .thenReturn(describePsProvisionResult);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        ArgumentCaptor<UntagResourceRequest> untagArgument = ArgumentCaptor.forClass(UntagResourceRequest.class);
        verify(proxyClient.client(), times(1)).untagResource(untagArgument.capture());
        assertThat(untagArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(untagArgument.getValue().resourceArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(untagArgument.getValue().tagKeys()).isEqualTo(Arrays.asList("key1"));

        ArgumentCaptor<TagResourceRequest> tagArgument = ArgumentCaptor.forClass(TagResourceRequest.class);
        verify(proxyClient.client(), times(1)).tagResource(tagArgument.capture());
        assertThat(tagArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(tagArgument.getValue().resourceArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(tagArgument.getValue().tags()).isEqualTo(Translator.ConvertToSSOTag(tags));

        ArgumentCaptor<DetachManagedPolicyFromPermissionSetRequest> detachPolicyArgument = ArgumentCaptor.forClass(DetachManagedPolicyFromPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).detachManagedPolicyFromPermissionSet(detachPolicyArgument.capture());
        assertThat(detachPolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(detachPolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(detachPolicyArgument.getValue().managedPolicyArn()).isEqualTo(TEST_READONLY_POLICY);

        ArgumentCaptor<AttachManagedPolicyToPermissionSetRequest> attachPolicyArgument = ArgumentCaptor.forClass(AttachManagedPolicyToPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).attachManagedPolicyToPermissionSet(attachPolicyArgument.capture());
        assertThat(attachPolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(attachPolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(attachPolicyArgument.getValue().managedPolicyArn()).isEqualTo(TEST_ADMIN_MANAGED_POLICY);

        ArgumentCaptor<DeleteInlinePolicyFromPermissionSetRequest> deleteInlinePolicyArgument = ArgumentCaptor.forClass(DeleteInlinePolicyFromPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).deleteInlinePolicyFromPermissionSet(deleteInlinePolicyArgument.capture());
        assertThat(deleteInlinePolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(deleteInlinePolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_Steps_Skipped() {
        final UpdateHandler handler = new UpdateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> ssoTags = new ArrayList<>();
        ssoTags.add(Tag.builder().key("key1").value("value1").build());

        List<String> newManagedPolicyArns = new ArrayList<>();
        newManagedPolicyArns.add(TEST_ADMIN_MANAGED_POLICY);
        List<AttachedManagedPolicy> newManagedPolicies = new ArrayList<>();
        newManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_ADMIN_MANAGED_POLICY).build());

        final ResourceModel inputModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(newManagedPolicyArns)
                .inlinePolicy(TEST_INLINE_POLICY_2)
                .tags(tags)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .build();

        PermissionSet testPermissionSet = PermissionSet.builder()
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .name(TEST_PERMISSION_SET_NAME)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .relayState(TEST_RELAY_STATE)
                .sessionDuration(TEST_SESSION_DURATION)
                .build();

        DescribePermissionSetRequest psDescribeRequest = DescribePermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        DescribePermissionSetResponse psDescribeResult = DescribePermissionSetResponse.builder()
                .permissionSet(testPermissionSet)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(psDescribeRequest, proxyClient.client()::describePermissionSet))
                .thenReturn(psDescribeResult);

        ListManagedPoliciesInPermissionSetRequest listAPRequest = ListManagedPoliciesInPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListManagedPoliciesInPermissionSetResponse listAttachedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(newManagedPolicies)
                .build();
        ListManagedPoliciesInPermissionSetResponse listUpdatedManagedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(newManagedPolicies)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAPRequest, proxyClient.client()::listManagedPoliciesInPermissionSet))
                .thenReturn(listAttachedPoliciesResponse)
                .thenReturn(listUpdatedManagedPoliciesResponse);

        GetInlinePolicyForPermissionSetRequest getIPRequest = GetInlinePolicyForPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        GetInlinePolicyForPermissionSetResponse getIPResult = GetInlinePolicyForPermissionSetResponse.builder()
                .inlinePolicy(TEST_INLINE_POLICY_2)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(getIPRequest, proxyClient.client()::getInlinePolicyForPermissionSet))
                .thenReturn(getIPResult);

        ProvisionPermissionSetRequest provisionPsRequest = ProvisionPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(ProvisionTargetType.ALL_PROVISIONED_ACCOUNTS)
                .build();
        ProvisionPermissionSetResponse provisionPsResponse = ProvisionPermissionSetResponse.builder()
                .permissionSetProvisioningStatus(PermissionSetProvisioningStatus.builder().status(StatusValues.IN_PROGRESS).requestId(TEST_REQUEST_ID).build())
                .build();
        when(proxy.injectCredentialsAndInvokeV2(provisionPsRequest, proxyClient.client()::provisionPermissionSet))
                .thenReturn(provisionPsResponse);

        DescribePermissionSetProvisioningStatusRequest decribePsProvisionRequest = DescribePermissionSetProvisioningStatusRequest.builder()
                .provisionPermissionSetRequestId(TEST_REQUEST_ID)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .build();
        DescribePermissionSetProvisioningStatusResponse describePsProvisionInProgressResponse = DescribePermissionSetProvisioningStatusResponse.builder()
                .permissionSetProvisioningStatus(PermissionSetProvisioningStatus.builder().status(StatusValues.IN_PROGRESS).requestId(TEST_REQUEST_ID).build())
                .build();
        DescribePermissionSetProvisioningStatusResponse describePsProvisionResponse = DescribePermissionSetProvisioningStatusResponse.builder()
                .permissionSetProvisioningStatus(PermissionSetProvisioningStatus.builder().status(StatusValues.SUCCEEDED).requestId(TEST_REQUEST_ID).build())
                .build();
        when(proxy.injectCredentialsAndInvokeV2(decribePsProvisionRequest, proxyClient.client()::describePermissionSetProvisioningStatus))
                .thenReturn(describePsProvisionInProgressResponse)
                .thenReturn(describePsProvisionResponse);

        CallbackContext context = new CallbackContext();
        context.setHandlerInvoked(true);
        context.setTagUpdated(true);
        context.setManagedPolicyUpdated(true);
        context.setInlinePolicyUpdated(true);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, context, proxyClient, logger);

        verify(proxyClient.client(), never()).untagResource(any(UntagResourceRequest.class));

        verify(proxyClient.client(), never()).tagResource(any(TagResourceRequest.class));

        verify(proxyClient.client(), never()).detachManagedPolicyFromPermissionSet(any(DetachManagedPolicyFromPermissionSetRequest.class));

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
    public void handleRequest_UpdatePs_Throttling() {
        final UpdateHandler handler = new UpdateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> covertedTags = new ArrayList<>();
        covertedTags.add(Tag.builder().key("key1").value("value1").build());

        List<String> newManagedPolicyArns = new ArrayList<>();
        newManagedPolicyArns.add(TEST_ADMIN_MANAGED_POLICY);
        List<AttachedManagedPolicy> newManagedPolicies = new ArrayList<>();
        newManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_ADMIN_MANAGED_POLICY).build());

        List<String> existingManagedPolicyArns = new ArrayList<>();
        existingManagedPolicyArns.add(TEST_READONLY_POLICY);
        List<AttachedManagedPolicy> existingManagedPolicies = new ArrayList<>();
        existingManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_READONLY_POLICY).build());

        final ResourceModel inputModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(newManagedPolicyArns)
                .tags(tags)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .build();

        UpdatePermissionSetRequest updatePermissionSetRequest = UpdatePermissionSetRequest.builder()
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayState(TEST_RELAY_STATE)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(updatePermissionSetRequest, proxyClient.client()::updatePermissionSet))
                .thenThrow(ThrottlingException.builder().message(THROTTLING_MESSAGE).build());

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
    public void handleRequest_UpdatePs_ISE() {
        final UpdateHandler handler = new UpdateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> covertedTags = new ArrayList<>();
        covertedTags.add(Tag.builder().key("key1").value("value1").build());

        List<String> newManagedPolicyArns = new ArrayList<>();
        newManagedPolicyArns.add(TEST_ADMIN_MANAGED_POLICY);
        List<AttachedManagedPolicy> newManagedPolicies = new ArrayList<>();
        newManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_ADMIN_MANAGED_POLICY).build());

        List<String> existingManagedPolicyArns = new ArrayList<>();
        existingManagedPolicyArns.add(TEST_READONLY_POLICY);
        List<AttachedManagedPolicy> existingManagedPolicies = new ArrayList<>();
        existingManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_READONLY_POLICY).build());

        final ResourceModel inputModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(newManagedPolicyArns)
                .tags(tags)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .build();

        UpdatePermissionSetRequest updatePermissionSetRequest = UpdatePermissionSetRequest.builder()
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayState(TEST_RELAY_STATE)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(updatePermissionSetRequest, proxyClient.client()::updatePermissionSet))
                .thenThrow(InternalServerException.builder().message(ISE_MESSAGE).build());

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
    public void handleRequest_UpdatePs_ConflictException() {
        final UpdateHandler handler = new UpdateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> covertedTags = new ArrayList<>();
        covertedTags.add(Tag.builder().key("key1").value("value1").build());

        List<String> newManagedPolicyArns = new ArrayList<>();
        newManagedPolicyArns.add(TEST_ADMIN_MANAGED_POLICY);
        List<AttachedManagedPolicy> newManagedPolicies = new ArrayList<>();
        newManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_ADMIN_MANAGED_POLICY).build());

        List<String> existingManagedPolicyArns = new ArrayList<>();
        existingManagedPolicyArns.add(TEST_READONLY_POLICY);
        List<AttachedManagedPolicy> existingManagedPolicies = new ArrayList<>();
        existingManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_READONLY_POLICY).build());

        final ResourceModel inputModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(newManagedPolicyArns)
                .tags(tags)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .build();

        UpdatePermissionSetRequest updatePermissionSetRequest = UpdatePermissionSetRequest.builder()
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayState(TEST_RELAY_STATE)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(updatePermissionSetRequest, proxyClient.client()::updatePermissionSet))
                .thenThrow(ConflictException.builder().message("Conflict exception.").build());

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
    public void handleRequest_UpdatePs_NonRetryable() {
        final UpdateHandler handler = new UpdateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> covertedTags = new ArrayList<>();
        covertedTags.add(Tag.builder().key("key1").value("value1").build());

        List<String> newManagedPolicyArns = new ArrayList<>();
        newManagedPolicyArns.add(TEST_ADMIN_MANAGED_POLICY);
        List<AttachedManagedPolicy> newManagedPolicies = new ArrayList<>();
        newManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_ADMIN_MANAGED_POLICY).build());

        List<String> existingManagedPolicyArns = new ArrayList<>();
        existingManagedPolicyArns.add(TEST_READONLY_POLICY);
        List<AttachedManagedPolicy> existingManagedPolicies = new ArrayList<>();
        existingManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_READONLY_POLICY).build());

        final ResourceModel inputModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(newManagedPolicyArns)
                .tags(tags)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .build();

        UpdatePermissionSetRequest updatePermissionSetRequest = UpdatePermissionSetRequest.builder()
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayState(TEST_RELAY_STATE)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(updatePermissionSetRequest, proxyClient.client()::updatePermissionSet))
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
    public void handleRequest_TagUpdate_Retryable() {
        final UpdateHandler handler = new UpdateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> ssoTags = new ArrayList<>();
        ssoTags.add(Tag.builder().key("key1").value("value1").build());

        List<String> newManagedPolicyArns = new ArrayList<>();
        newManagedPolicyArns.add(TEST_ADMIN_MANAGED_POLICY);
        List<AttachedManagedPolicy> newManagedPolicies = new ArrayList<>();
        newManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_ADMIN_MANAGED_POLICY).build());

        List<String> existingManagedPolicyArns = new ArrayList<>();
        existingManagedPolicyArns.add(TEST_READONLY_POLICY);
        List<AttachedManagedPolicy> existingManagedPolicies = new ArrayList<>();
        existingManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_READONLY_POLICY).build());

        final ResourceModel inputModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(newManagedPolicyArns)
                .inlinePolicy(TEST_INLINE_POLICY_2)
                .tags(tags)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .build();

        ListTagsForResourceRequest listTagsRequest = ListTagsForResourceRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .resourceArn(TEST_PERMISSION_SET_ARN)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listTagsRequest, proxyClient.client()::listTagsForResource))
                .thenReturn(ListTagsForResourceResponse.builder().tags(ssoTags).build());

        TagResourceRequest tagResourceRequest = TagResourceRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .resourceArn(TEST_PERMISSION_SET_ARN)
                .tags(Translator.ConvertToSSOTag(tags))
                .build();
        when(proxy.injectCredentialsAndInvokeV2(tagResourceRequest, proxyClient.client()::tagResource))
                .thenThrow(ThrottlingException.builder().message(THROTTLING_MESSAGE).build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        ArgumentCaptor<UntagResourceRequest> untagArgument = ArgumentCaptor.forClass(UntagResourceRequest.class);
        verify(proxyClient.client(), times(1)).untagResource(untagArgument.capture());
        assertThat(untagArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(untagArgument.getValue().resourceArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(untagArgument.getValue().tagKeys()).isEqualTo(Arrays.asList("key1"));

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(5);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_UpdateAttachment_Retryable() {
        final UpdateHandler handler = new UpdateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> ssoTags = new ArrayList<>();
        ssoTags.add(Tag.builder().key("key1").value("value1").build());

        List<String> newManagedPolicyArns = new ArrayList<>();
        newManagedPolicyArns.add(TEST_ADMIN_MANAGED_POLICY);
        List<AttachedManagedPolicy> newManagedPolicies = new ArrayList<>();
        newManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_ADMIN_MANAGED_POLICY).build());

        List<String> existingManagedPolicyArns = new ArrayList<>();
        existingManagedPolicyArns.add(TEST_READONLY_POLICY);
        List<AttachedManagedPolicy> existingManagedPolicies = new ArrayList<>();
        existingManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_READONLY_POLICY).build());

        final ResourceModel inputModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(newManagedPolicyArns)
                .inlinePolicy(TEST_INLINE_POLICY_2)
                .tags(tags)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .build();

        ListTagsForResourceRequest listTagsRequest = ListTagsForResourceRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .resourceArn(TEST_PERMISSION_SET_ARN)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listTagsRequest, proxyClient.client()::listTagsForResource))
                .thenReturn(ListTagsForResourceResponse.builder().tags(ssoTags).build());

        ListManagedPoliciesInPermissionSetRequest listAPRequest = ListManagedPoliciesInPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListManagedPoliciesInPermissionSetResponse listAttachedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(existingManagedPolicies)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAPRequest, proxyClient.client()::listManagedPoliciesInPermissionSet))
                .thenReturn(listAttachedPoliciesResponse);

        AttachManagedPolicyToPermissionSetRequest attachPolicyToPsRequest = AttachManagedPolicyToPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .managedPolicyArn(TEST_ADMIN_MANAGED_POLICY)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(attachPolicyToPsRequest, proxyClient.client()::attachManagedPolicyToPermissionSet))
                .thenThrow(ThrottlingException.builder().message(THROTTLING_MESSAGE).build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        ArgumentCaptor<UntagResourceRequest> untagArgument = ArgumentCaptor.forClass(UntagResourceRequest.class);
        verify(proxyClient.client(), times(1)).untagResource(untagArgument.capture());
        assertThat(untagArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(untagArgument.getValue().resourceArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(untagArgument.getValue().tagKeys()).isEqualTo(Arrays.asList("key1"));

        ArgumentCaptor<TagResourceRequest> tagArgument = ArgumentCaptor.forClass(TagResourceRequest.class);
        verify(proxyClient.client(), times(1)).tagResource(tagArgument.capture());
        assertThat(tagArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(tagArgument.getValue().resourceArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(tagArgument.getValue().tags()).isEqualTo(Translator.ConvertToSSOTag(tags));

        ArgumentCaptor<DetachManagedPolicyFromPermissionSetRequest> detachPolicyArgument = ArgumentCaptor.forClass(DetachManagedPolicyFromPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).detachManagedPolicyFromPermissionSet(detachPolicyArgument.capture());
        assertThat(detachPolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(detachPolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(detachPolicyArgument.getValue().managedPolicyArn()).isEqualTo(TEST_READONLY_POLICY);

        ArgumentCaptor<AttachManagedPolicyToPermissionSetRequest> attachPolicyArgument = ArgumentCaptor.forClass(AttachManagedPolicyToPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).attachManagedPolicyToPermissionSet(attachPolicyArgument.capture());
        assertThat(attachPolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(attachPolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(attachPolicyArgument.getValue().managedPolicyArn()).isEqualTo(TEST_ADMIN_MANAGED_POLICY);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(5);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_UpdateIp_Retryable() {
        final UpdateHandler handler = new UpdateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> ssoTags = new ArrayList<>();
        ssoTags.add(Tag.builder().key("key1").value("value1").build());

        List<String> newManagedPolicyArns = new ArrayList<>();
        newManagedPolicyArns.add(TEST_ADMIN_MANAGED_POLICY);
        List<AttachedManagedPolicy> newManagedPolicies = new ArrayList<>();
        newManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_ADMIN_MANAGED_POLICY).build());

        List<String> existingManagedPolicyArns = new ArrayList<>();
        existingManagedPolicyArns.add(TEST_READONLY_POLICY);
        List<AttachedManagedPolicy> existingManagedPolicies = new ArrayList<>();
        existingManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_READONLY_POLICY).build());

        final ResourceModel inputModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(newManagedPolicyArns)
                .inlinePolicy(TEST_INLINE_POLICY_2)
                .tags(tags)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .build();

        ListTagsForResourceRequest listTagsRequest = ListTagsForResourceRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .resourceArn(TEST_PERMISSION_SET_ARN)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listTagsRequest, proxyClient.client()::listTagsForResource))
                .thenReturn(ListTagsForResourceResponse.builder().tags(ssoTags).build());

        ListManagedPoliciesInPermissionSetRequest listAPRequest = ListManagedPoliciesInPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListManagedPoliciesInPermissionSetResponse listAttachedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(existingManagedPolicies)
                .build();
        ListManagedPoliciesInPermissionSetResponse listUpdatedManagedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(newManagedPolicies)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAPRequest, proxyClient.client()::listManagedPoliciesInPermissionSet))
                .thenReturn(listAttachedPoliciesResponse)
                .thenReturn(listUpdatedManagedPoliciesResponse);

        PutInlinePolicyToPermissionSetRequest putInlinePolicyToPsRequest = PutInlinePolicyToPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .inlinePolicy(TEST_INLINE_POLICY_2)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(putInlinePolicyToPsRequest, proxyClient.client()::putInlinePolicyToPermissionSet))
                .thenThrow(ThrottlingException.builder().message(THROTTLING_MESSAGE).build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        ArgumentCaptor<UntagResourceRequest> untagArgument = ArgumentCaptor.forClass(UntagResourceRequest.class);
        verify(proxyClient.client(), times(1)).untagResource(untagArgument.capture());
        assertThat(untagArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(untagArgument.getValue().resourceArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(untagArgument.getValue().tagKeys()).isEqualTo(Arrays.asList("key1"));

        ArgumentCaptor<TagResourceRequest> tagArgument = ArgumentCaptor.forClass(TagResourceRequest.class);
        verify(proxyClient.client(), times(1)).tagResource(tagArgument.capture());
        assertThat(tagArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(tagArgument.getValue().resourceArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(tagArgument.getValue().tags()).isEqualTo(Translator.ConvertToSSOTag(tags));

        ArgumentCaptor<DetachManagedPolicyFromPermissionSetRequest> detachPolicyArgument = ArgumentCaptor.forClass(DetachManagedPolicyFromPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).detachManagedPolicyFromPermissionSet(detachPolicyArgument.capture());
        assertThat(detachPolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(detachPolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(detachPolicyArgument.getValue().managedPolicyArn()).isEqualTo(TEST_READONLY_POLICY);

        ArgumentCaptor<AttachManagedPolicyToPermissionSetRequest> attachPolicyArgument = ArgumentCaptor.forClass(AttachManagedPolicyToPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).attachManagedPolicyToPermissionSet(attachPolicyArgument.capture());
        assertThat(attachPolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(attachPolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(attachPolicyArgument.getValue().managedPolicyArn()).isEqualTo(TEST_ADMIN_MANAGED_POLICY);

        ArgumentCaptor<PutInlinePolicyToPermissionSetRequest> putInlinePolicyArgument = ArgumentCaptor.forClass(PutInlinePolicyToPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).putInlinePolicyToPermissionSet(putInlinePolicyArgument.capture());
        assertThat(putInlinePolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(putInlinePolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(putInlinePolicyArgument.getValue().inlinePolicy()).isEqualTo(TEST_INLINE_POLICY_2);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(5);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_ProvisionPs_Retryable_Throttling() {
        final UpdateHandler handler = new UpdateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> ssoTags = new ArrayList<>();
        ssoTags.add(Tag.builder().key("key1").value("value1").build());

        List<String> newManagedPolicyArns = new ArrayList<>();
        newManagedPolicyArns.add(TEST_ADMIN_MANAGED_POLICY);
        List<AttachedManagedPolicy> newManagedPolicies = new ArrayList<>();
        newManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_ADMIN_MANAGED_POLICY).build());

        List<String> existingManagedPolicyArns = new ArrayList<>();
        existingManagedPolicyArns.add(TEST_READONLY_POLICY);
        List<AttachedManagedPolicy> existingManagedPolicies = new ArrayList<>();
        existingManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_READONLY_POLICY).build());

        final ResourceModel inputModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(newManagedPolicyArns)
                .inlinePolicy(TEST_INLINE_POLICY_2)
                .tags(tags)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .build();

        ListTagsForResourceRequest listTagsRequest = ListTagsForResourceRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .resourceArn(TEST_PERMISSION_SET_ARN)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listTagsRequest, proxyClient.client()::listTagsForResource))
                .thenReturn(ListTagsForResourceResponse.builder().tags(ssoTags).build());

        ListManagedPoliciesInPermissionSetRequest listAPRequest = ListManagedPoliciesInPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListManagedPoliciesInPermissionSetResponse listAttachedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(existingManagedPolicies)
                .build();
        ListManagedPoliciesInPermissionSetResponse listUpdatedManagedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(newManagedPolicies)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAPRequest, proxyClient.client()::listManagedPoliciesInPermissionSet))
                .thenReturn(listAttachedPoliciesResponse)
                .thenReturn(listUpdatedManagedPoliciesResponse);

        ProvisionPermissionSetRequest provisionPsRequest = ProvisionPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(ProvisionTargetType.ALL_PROVISIONED_ACCOUNTS)
                .build();
        ProvisionPermissionSetResponse provisionPsResponse = ProvisionPermissionSetResponse.builder()
                .permissionSetProvisioningStatus(PermissionSetProvisioningStatus.builder().status(StatusValues.IN_PROGRESS).requestId(TEST_REQUEST_ID).build())
                .build();
        when(proxy.injectCredentialsAndInvokeV2(provisionPsRequest, proxyClient.client()::provisionPermissionSet))
                .thenThrow(ThrottlingException.builder().message(THROTTLING_MESSAGE).build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        ArgumentCaptor<UntagResourceRequest> untagArgument = ArgumentCaptor.forClass(UntagResourceRequest.class);
        verify(proxyClient.client(), times(1)).untagResource(untagArgument.capture());
        assertThat(untagArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(untagArgument.getValue().resourceArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(untagArgument.getValue().tagKeys()).isEqualTo(Arrays.asList("key1"));

        ArgumentCaptor<TagResourceRequest> tagArgument = ArgumentCaptor.forClass(TagResourceRequest.class);
        verify(proxyClient.client(), times(1)).tagResource(tagArgument.capture());
        assertThat(tagArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(tagArgument.getValue().resourceArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(tagArgument.getValue().tags()).isEqualTo(Translator.ConvertToSSOTag(tags));

        ArgumentCaptor<DetachManagedPolicyFromPermissionSetRequest> detachPolicyArgument = ArgumentCaptor.forClass(DetachManagedPolicyFromPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).detachManagedPolicyFromPermissionSet(detachPolicyArgument.capture());
        assertThat(detachPolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(detachPolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(detachPolicyArgument.getValue().managedPolicyArn()).isEqualTo(TEST_READONLY_POLICY);

        ArgumentCaptor<AttachManagedPolicyToPermissionSetRequest> attachPolicyArgument = ArgumentCaptor.forClass(AttachManagedPolicyToPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).attachManagedPolicyToPermissionSet(attachPolicyArgument.capture());
        assertThat(attachPolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(attachPolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(attachPolicyArgument.getValue().managedPolicyArn()).isEqualTo(TEST_ADMIN_MANAGED_POLICY);

        ArgumentCaptor<PutInlinePolicyToPermissionSetRequest> putInlinePolicyArgument = ArgumentCaptor.forClass(PutInlinePolicyToPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).putInlinePolicyToPermissionSet(putInlinePolicyArgument.capture());
        assertThat(putInlinePolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(putInlinePolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(putInlinePolicyArgument.getValue().inlinePolicy()).isEqualTo(TEST_INLINE_POLICY_2);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(300);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_ProvisionPs_Retryable_ConflictException() {
        final UpdateHandler handler = new UpdateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> ssoTags = new ArrayList<>();
        ssoTags.add(Tag.builder().key("key1").value("value1").build());

        List<String> newManagedPolicyArns = new ArrayList<>();
        newManagedPolicyArns.add(TEST_ADMIN_MANAGED_POLICY);
        List<AttachedManagedPolicy> newManagedPolicies = new ArrayList<>();
        newManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_ADMIN_MANAGED_POLICY).build());

        List<String> existingManagedPolicyArns = new ArrayList<>();
        existingManagedPolicyArns.add(TEST_READONLY_POLICY);
        List<AttachedManagedPolicy> existingManagedPolicies = new ArrayList<>();
        existingManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_READONLY_POLICY).build());

        final ResourceModel inputModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(newManagedPolicyArns)
                .inlinePolicy(TEST_INLINE_POLICY_2)
                .tags(tags)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .build();

        ListTagsForResourceRequest listTagsRequest = ListTagsForResourceRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .resourceArn(TEST_PERMISSION_SET_ARN)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listTagsRequest, proxyClient.client()::listTagsForResource))
                .thenReturn(ListTagsForResourceResponse.builder().tags(ssoTags).build());

        ListManagedPoliciesInPermissionSetRequest listAPRequest = ListManagedPoliciesInPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListManagedPoliciesInPermissionSetResponse listAttachedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(existingManagedPolicies)
                .build();
        ListManagedPoliciesInPermissionSetResponse listUpdatedManagedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(newManagedPolicies)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAPRequest, proxyClient.client()::listManagedPoliciesInPermissionSet))
                .thenReturn(listAttachedPoliciesResponse)
                .thenReturn(listUpdatedManagedPoliciesResponse);

        ProvisionPermissionSetRequest provisionPsRequest = ProvisionPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(ProvisionTargetType.ALL_PROVISIONED_ACCOUNTS)
                .build();
        ProvisionPermissionSetResponse provisionPsResponse = ProvisionPermissionSetResponse.builder()
                .permissionSetProvisioningStatus(PermissionSetProvisioningStatus.builder().status(StatusValues.IN_PROGRESS).requestId(TEST_REQUEST_ID).build())
                .build();
        when(proxy.injectCredentialsAndInvokeV2(provisionPsRequest, proxyClient.client()::provisionPermissionSet))
                .thenThrow(ConflictException.builder().message("Conflict operation.").build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        ArgumentCaptor<UntagResourceRequest> untagArgument = ArgumentCaptor.forClass(UntagResourceRequest.class);
        verify(proxyClient.client(), times(1)).untagResource(untagArgument.capture());
        assertThat(untagArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(untagArgument.getValue().resourceArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(untagArgument.getValue().tagKeys()).isEqualTo(Arrays.asList("key1"));

        ArgumentCaptor<TagResourceRequest> tagArgument = ArgumentCaptor.forClass(TagResourceRequest.class);
        verify(proxyClient.client(), times(1)).tagResource(tagArgument.capture());
        assertThat(tagArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(tagArgument.getValue().resourceArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(tagArgument.getValue().tags()).isEqualTo(Translator.ConvertToSSOTag(tags));

        ArgumentCaptor<DetachManagedPolicyFromPermissionSetRequest> detachPolicyArgument = ArgumentCaptor.forClass(DetachManagedPolicyFromPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).detachManagedPolicyFromPermissionSet(detachPolicyArgument.capture());
        assertThat(detachPolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(detachPolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(detachPolicyArgument.getValue().managedPolicyArn()).isEqualTo(TEST_READONLY_POLICY);

        ArgumentCaptor<AttachManagedPolicyToPermissionSetRequest> attachPolicyArgument = ArgumentCaptor.forClass(AttachManagedPolicyToPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).attachManagedPolicyToPermissionSet(attachPolicyArgument.capture());
        assertThat(attachPolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(attachPolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(attachPolicyArgument.getValue().managedPolicyArn()).isEqualTo(TEST_ADMIN_MANAGED_POLICY);

        ArgumentCaptor<PutInlinePolicyToPermissionSetRequest> putInlinePolicyArgument = ArgumentCaptor.forClass(PutInlinePolicyToPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).putInlinePolicyToPermissionSet(putInlinePolicyArgument.capture());
        assertThat(putInlinePolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(putInlinePolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(putInlinePolicyArgument.getValue().inlinePolicy()).isEqualTo(TEST_INLINE_POLICY_2);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(300);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_ProvisionPs_Retryable_ISE() {
        final UpdateHandler handler = new UpdateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> ssoTags = new ArrayList<>();
        ssoTags.add(Tag.builder().key("key1").value("value1").build());

        List<String> newManagedPolicyArns = new ArrayList<>();
        newManagedPolicyArns.add(TEST_ADMIN_MANAGED_POLICY);
        List<AttachedManagedPolicy> newManagedPolicies = new ArrayList<>();
        newManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_ADMIN_MANAGED_POLICY).build());

        List<String> existingManagedPolicyArns = new ArrayList<>();
        existingManagedPolicyArns.add(TEST_READONLY_POLICY);
        List<AttachedManagedPolicy> existingManagedPolicies = new ArrayList<>();
        existingManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_READONLY_POLICY).build());

        final ResourceModel inputModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(newManagedPolicyArns)
                .inlinePolicy(TEST_INLINE_POLICY_2)
                .tags(tags)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .build();

        ListTagsForResourceRequest listTagsRequest = ListTagsForResourceRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .resourceArn(TEST_PERMISSION_SET_ARN)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listTagsRequest, proxyClient.client()::listTagsForResource))
                .thenReturn(ListTagsForResourceResponse.builder().tags(ssoTags).build());

        ListManagedPoliciesInPermissionSetRequest listAPRequest = ListManagedPoliciesInPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListManagedPoliciesInPermissionSetResponse listAttachedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(existingManagedPolicies)
                .build();
        ListManagedPoliciesInPermissionSetResponse listUpdatedManagedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(newManagedPolicies)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAPRequest, proxyClient.client()::listManagedPoliciesInPermissionSet))
                .thenReturn(listAttachedPoliciesResponse)
                .thenReturn(listUpdatedManagedPoliciesResponse);

        ProvisionPermissionSetRequest provisionPsRequest = ProvisionPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(ProvisionTargetType.ALL_PROVISIONED_ACCOUNTS)
                .build();
        ProvisionPermissionSetResponse provisionPsResponse = ProvisionPermissionSetResponse.builder()
                .permissionSetProvisioningStatus(PermissionSetProvisioningStatus.builder().status(StatusValues.IN_PROGRESS).requestId(TEST_REQUEST_ID).build())
                .build();
        when(proxy.injectCredentialsAndInvokeV2(provisionPsRequest, proxyClient.client()::provisionPermissionSet))
                .thenThrow(InternalServerException.builder().message("ISE").build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        ArgumentCaptor<UntagResourceRequest> untagArgument = ArgumentCaptor.forClass(UntagResourceRequest.class);
        verify(proxyClient.client(), times(1)).untagResource(untagArgument.capture());
        assertThat(untagArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(untagArgument.getValue().resourceArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(untagArgument.getValue().tagKeys()).isEqualTo(Arrays.asList("key1"));

        ArgumentCaptor<TagResourceRequest> tagArgument = ArgumentCaptor.forClass(TagResourceRequest.class);
        verify(proxyClient.client(), times(1)).tagResource(tagArgument.capture());
        assertThat(tagArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(tagArgument.getValue().resourceArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(tagArgument.getValue().tags()).isEqualTo(Translator.ConvertToSSOTag(tags));

        ArgumentCaptor<DetachManagedPolicyFromPermissionSetRequest> detachPolicyArgument = ArgumentCaptor.forClass(DetachManagedPolicyFromPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).detachManagedPolicyFromPermissionSet(detachPolicyArgument.capture());
        assertThat(detachPolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(detachPolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(detachPolicyArgument.getValue().managedPolicyArn()).isEqualTo(TEST_READONLY_POLICY);

        ArgumentCaptor<AttachManagedPolicyToPermissionSetRequest> attachPolicyArgument = ArgumentCaptor.forClass(AttachManagedPolicyToPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).attachManagedPolicyToPermissionSet(attachPolicyArgument.capture());
        assertThat(attachPolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(attachPolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(attachPolicyArgument.getValue().managedPolicyArn()).isEqualTo(TEST_ADMIN_MANAGED_POLICY);

        ArgumentCaptor<PutInlinePolicyToPermissionSetRequest> putInlinePolicyArgument = ArgumentCaptor.forClass(PutInlinePolicyToPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).putInlinePolicyToPermissionSet(putInlinePolicyArgument.capture());
        assertThat(putInlinePolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(putInlinePolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(putInlinePolicyArgument.getValue().inlinePolicy()).isEqualTo(TEST_INLINE_POLICY_2);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_ProvisionPs_NonRetryable() {
        final UpdateHandler handler = new UpdateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> ssoTags = new ArrayList<>();
        ssoTags.add(Tag.builder().key("key1").value("value1").build());

        List<String> newManagedPolicyArns = new ArrayList<>();
        newManagedPolicyArns.add(TEST_ADMIN_MANAGED_POLICY);
        List<AttachedManagedPolicy> newManagedPolicies = new ArrayList<>();
        newManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_ADMIN_MANAGED_POLICY).build());

        List<String> existingManagedPolicyArns = new ArrayList<>();
        existingManagedPolicyArns.add(TEST_READONLY_POLICY);
        List<AttachedManagedPolicy> existingManagedPolicies = new ArrayList<>();
        existingManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_READONLY_POLICY).build());

        final ResourceModel inputModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(newManagedPolicyArns)
                .inlinePolicy(TEST_INLINE_POLICY_2)
                .tags(tags)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .build();

        ListTagsForResourceRequest listTagsRequest = ListTagsForResourceRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .resourceArn(TEST_PERMISSION_SET_ARN)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listTagsRequest, proxyClient.client()::listTagsForResource))
                .thenReturn(ListTagsForResourceResponse.builder().tags(ssoTags).build());

        ListManagedPoliciesInPermissionSetRequest listAPRequest = ListManagedPoliciesInPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListManagedPoliciesInPermissionSetResponse listAttachedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(existingManagedPolicies)
                .build();
        ListManagedPoliciesInPermissionSetResponse listUpdatedManagedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(newManagedPolicies)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAPRequest, proxyClient.client()::listManagedPoliciesInPermissionSet))
                .thenReturn(listAttachedPoliciesResponse)
                .thenReturn(listUpdatedManagedPoliciesResponse);

        ProvisionPermissionSetRequest provisionPsRequest = ProvisionPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(ProvisionTargetType.ALL_PROVISIONED_ACCOUNTS)
                .build();
        ProvisionPermissionSetResponse provisionPsResponse = ProvisionPermissionSetResponse.builder()
                .permissionSetProvisioningStatus(PermissionSetProvisioningStatus.builder().status(StatusValues.IN_PROGRESS).requestId(TEST_REQUEST_ID).build())
                .build();
        when(proxy.injectCredentialsAndInvokeV2(provisionPsRequest, proxyClient.client()::provisionPermissionSet))
                .thenThrow(ServiceQuotaExceededException.builder().message("NonRetryable.").build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        ArgumentCaptor<UntagResourceRequest> untagArgument = ArgumentCaptor.forClass(UntagResourceRequest.class);
        verify(proxyClient.client(), times(1)).untagResource(untagArgument.capture());
        assertThat(untagArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(untagArgument.getValue().resourceArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(untagArgument.getValue().tagKeys()).isEqualTo(Arrays.asList("key1"));

        ArgumentCaptor<TagResourceRequest> tagArgument = ArgumentCaptor.forClass(TagResourceRequest.class);
        verify(proxyClient.client(), times(1)).tagResource(tagArgument.capture());
        assertThat(tagArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(tagArgument.getValue().resourceArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(tagArgument.getValue().tags()).isEqualTo(Translator.ConvertToSSOTag(tags));

        ArgumentCaptor<DetachManagedPolicyFromPermissionSetRequest> detachPolicyArgument = ArgumentCaptor.forClass(DetachManagedPolicyFromPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).detachManagedPolicyFromPermissionSet(detachPolicyArgument.capture());
        assertThat(detachPolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(detachPolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(detachPolicyArgument.getValue().managedPolicyArn()).isEqualTo(TEST_READONLY_POLICY);

        ArgumentCaptor<AttachManagedPolicyToPermissionSetRequest> attachPolicyArgument = ArgumentCaptor.forClass(AttachManagedPolicyToPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).attachManagedPolicyToPermissionSet(attachPolicyArgument.capture());
        assertThat(attachPolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(attachPolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(attachPolicyArgument.getValue().managedPolicyArn()).isEqualTo(TEST_ADMIN_MANAGED_POLICY);

        ArgumentCaptor<PutInlinePolicyToPermissionSetRequest> putInlinePolicyArgument = ArgumentCaptor.forClass(PutInlinePolicyToPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).putInlinePolicyToPermissionSet(putInlinePolicyArgument.capture());
        assertThat(putInlinePolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(putInlinePolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(putInlinePolicyArgument.getValue().inlinePolicy()).isEqualTo(TEST_INLINE_POLICY_2);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).contains("NonRetryable.");
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.GeneralServiceException);
    }

    @Test
    public void handleRequest_ProvisionPs_Failure_ResourceNotFound() {
        final UpdateHandler handler = new UpdateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> ssoTags = new ArrayList<>();
        ssoTags.add(Tag.builder().key("key1").value("value1").build());

        List<String> newManagedPolicyArns = new ArrayList<>();
        newManagedPolicyArns.add(TEST_ADMIN_MANAGED_POLICY);
        List<AttachedManagedPolicy> newManagedPolicies = new ArrayList<>();
        newManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_ADMIN_MANAGED_POLICY).build());

        List<String> existingManagedPolicyArns = new ArrayList<>();
        existingManagedPolicyArns.add(TEST_READONLY_POLICY);
        List<AttachedManagedPolicy> existingManagedPolicies = new ArrayList<>();
        existingManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_READONLY_POLICY).build());

        final ResourceModel inputModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(newManagedPolicyArns)
                .inlinePolicy(TEST_INLINE_POLICY_2)
                .tags(tags)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .build();

        ListTagsForResourceRequest listTagsRequest = ListTagsForResourceRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .resourceArn(TEST_PERMISSION_SET_ARN)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listTagsRequest, proxyClient.client()::listTagsForResource))
                .thenReturn(ListTagsForResourceResponse.builder().tags(ssoTags).build());

        ListManagedPoliciesInPermissionSetRequest listAPRequest = ListManagedPoliciesInPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListManagedPoliciesInPermissionSetResponse listAttachedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(existingManagedPolicies)
                .build();
        ListManagedPoliciesInPermissionSetResponse listUpdatedManagedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(newManagedPolicies)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAPRequest, proxyClient.client()::listManagedPoliciesInPermissionSet))
                .thenReturn(listAttachedPoliciesResponse)
                .thenReturn(listUpdatedManagedPoliciesResponse);

        ProvisionPermissionSetRequest provisionPsRequest = ProvisionPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(ProvisionTargetType.ALL_PROVISIONED_ACCOUNTS)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(provisionPsRequest, proxyClient.client()::provisionPermissionSet))
                .thenThrow(ResourceNotFoundException.builder().message("Permission set not found!").build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        ArgumentCaptor<UntagResourceRequest> untagArgument = ArgumentCaptor.forClass(UntagResourceRequest.class);
        verify(proxyClient.client(), times(1)).untagResource(untagArgument.capture());
        assertThat(untagArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(untagArgument.getValue().resourceArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(untagArgument.getValue().tagKeys()).isEqualTo(Arrays.asList("key1"));

        ArgumentCaptor<TagResourceRequest> tagArgument = ArgumentCaptor.forClass(TagResourceRequest.class);
        verify(proxyClient.client(), times(1)).tagResource(tagArgument.capture());
        assertThat(tagArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(tagArgument.getValue().resourceArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(tagArgument.getValue().tags()).isEqualTo(Translator.ConvertToSSOTag(tags));

        ArgumentCaptor<DetachManagedPolicyFromPermissionSetRequest> detachPolicyArgument = ArgumentCaptor.forClass(DetachManagedPolicyFromPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).detachManagedPolicyFromPermissionSet(detachPolicyArgument.capture());
        assertThat(detachPolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(detachPolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(detachPolicyArgument.getValue().managedPolicyArn()).isEqualTo(TEST_READONLY_POLICY);

        ArgumentCaptor<AttachManagedPolicyToPermissionSetRequest> attachPolicyArgument = ArgumentCaptor.forClass(AttachManagedPolicyToPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).attachManagedPolicyToPermissionSet(attachPolicyArgument.capture());
        assertThat(attachPolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(attachPolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(attachPolicyArgument.getValue().managedPolicyArn()).isEqualTo(TEST_ADMIN_MANAGED_POLICY);

        ArgumentCaptor<PutInlinePolicyToPermissionSetRequest> putInlinePolicyArgument = ArgumentCaptor.forClass(PutInlinePolicyToPermissionSetRequest.class);
        verify(proxyClient.client(), times(1)).putInlinePolicyToPermissionSet(putInlinePolicyArgument.capture());
        assertThat(putInlinePolicyArgument.getValue().instanceArn()).isEqualTo(TEST_SSO_INSTANCE_ARN);
        assertThat(putInlinePolicyArgument.getValue().permissionSetArn()).isEqualTo(TEST_PERMISSION_SET_ARN);
        assertThat(putInlinePolicyArgument.getValue().inlinePolicy()).isEqualTo(TEST_INLINE_POLICY_2);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isEqualTo("Permission set not found!");
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InternalFailure);
    }

    @Test
    public void handleRequest_Failure_ResourceNotFound() {
        final UpdateHandler handler = new UpdateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);

        List<String> newManagedPolicyArns = new ArrayList<>();
        newManagedPolicyArns.add(TEST_ADMIN_MANAGED_POLICY);
        List<AttachedManagedPolicy> newManagedPolicies = new ArrayList<>();
        newManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_ADMIN_MANAGED_POLICY).build());

        final ResourceModel inputModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(newManagedPolicyArns)
                .inlinePolicy(TEST_INLINE_POLICY_2)
                .tags(tags)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .build();

        UpdatePermissionSetRequest updatePsRequest = UpdatePermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .relayState(TEST_RELAY_STATE)
                .sessionDuration(TEST_SESSION_DURATION)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(updatePsRequest, proxyClient.client()::updatePermissionSet))
                .thenThrow(ResourceNotFoundException.builder().message("Permission set not found.").build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        verify(proxyClient.client(), never()).untagResource(any(UntagResourceRequest.class));
        verify(proxyClient.client(), never()).tagResource(any(TagResourceRequest.class));
        verify(proxyClient.client(), never()).detachManagedPolicyFromPermissionSet(any(DetachManagedPolicyFromPermissionSetRequest.class));
        verify(proxyClient.client(), never()).attachManagedPolicyToPermissionSet(any(AttachManagedPolicyToPermissionSetRequest.class));
        verify(proxyClient.client(), never()).putInlinePolicyToPermissionSet(any(PutInlinePolicyToPermissionSetRequest.class));

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).contains("Permission set not found.");
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }

    @Test
    public void handleRequest_Failure_ServiceLimitExceed() {
        final UpdateHandler handler = new UpdateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> covertedTags = new ArrayList<>();
        covertedTags.add(Tag.builder().key("key1").value("value1").build());

        List<String> newManagedPolicyArns = new ArrayList<>();
        for(int i = 0; i < 21; i ++) {
            newManagedPolicyArns.add("ManagedPolicyArn" + i);
        }

        final ResourceModel inputModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(newManagedPolicyArns)
                .inlinePolicy(TEST_INLINE_POLICY_2)
                .tags(tags)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .build();

        ListTagsForResourceRequest listTagsRequest = ListTagsForResourceRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .resourceArn(TEST_PERMISSION_SET_ARN)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listTagsRequest, proxyClient.client()::listTagsForResource))
                .thenReturn(ListTagsForResourceResponse.builder().tags(covertedTags).build());

        assertThatThrownBy(() -> {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
        }).isInstanceOf(CfnServiceLimitExceededException.class).hasMessageContaining(MANAGED_POLICIES_LIMIT_EXCEED_MESSAGE);
    }

    @Test
    public void handleRequest_Failure_ProvisionFailed() {
        final UpdateHandler handler = new UpdateHandler();

        List<software.amazon.sso.permissionset.Tag> tags = new ArrayList<>();

        software.amazon.sso.permissionset.Tag tag = new software.amazon.sso.permissionset.Tag();
        tag.setKey("key");
        tag.setValue("value");
        tags.add(tag);
        List<Tag> covertedTags = new ArrayList<>();
        covertedTags.add(Tag.builder().key("key1").value("value1").build());

        List<String> newManagedPolicyArns = new ArrayList<>();
        newManagedPolicyArns.add(TEST_ADMIN_MANAGED_POLICY);
        List<AttachedManagedPolicy> newManagedPolicies = new ArrayList<>();
        newManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_ADMIN_MANAGED_POLICY).build());

        List<String> existingManagedPolicyArns = new ArrayList<>();
        existingManagedPolicyArns.add(TEST_READONLY_POLICY);
        List<AttachedManagedPolicy> existingManagedPolicies = new ArrayList<>();
        existingManagedPolicies.add(AttachedManagedPolicy.builder().arn(TEST_READONLY_POLICY).build());

        final ResourceModel inputModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .managedPolicies(newManagedPolicyArns)
                .tags(tags)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(inputModel)
                .build();

        ListTagsForResourceRequest listTagsRequest = ListTagsForResourceRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .resourceArn(TEST_PERMISSION_SET_ARN)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listTagsRequest, proxyClient.client()::listTagsForResource))
                .thenReturn(ListTagsForResourceResponse.builder().tags(covertedTags).build());

        ListManagedPoliciesInPermissionSetRequest listAPResponse = ListManagedPoliciesInPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListManagedPoliciesInPermissionSetResponse listAttachedPoliciesResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(existingManagedPolicies)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAPResponse, proxyClient.client()::listManagedPoliciesInPermissionSet))
                .thenReturn(listAttachedPoliciesResponse);

        ProvisionPermissionSetRequest provisionPsRequest = ProvisionPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .targetType(ProvisionTargetType.ALL_PROVISIONED_ACCOUNTS)
                .build();
        ProvisionPermissionSetResponse provisionPsResponse = ProvisionPermissionSetResponse.builder()
                .permissionSetProvisioningStatus(PermissionSetProvisioningStatus.builder().status(StatusValues.IN_PROGRESS).requestId(TEST_REQUEST_ID).build())
                .build();
        when(proxy.injectCredentialsAndInvokeV2(provisionPsRequest, proxyClient.client()::provisionPermissionSet))
                .thenReturn(provisionPsResponse);


        DescribePermissionSetProvisioningStatusRequest decribePsProvisionRequest = DescribePermissionSetProvisioningStatusRequest.builder()
                .provisionPermissionSetRequestId(TEST_REQUEST_ID)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .build();
        DescribePermissionSetProvisioningStatusResponse describePsProvisionResponse = DescribePermissionSetProvisioningStatusResponse.builder()
                .permissionSetProvisioningStatus(PermissionSetProvisioningStatus.builder()
                        .status(StatusValues.FAILED)
                        .requestId(TEST_REQUEST_ID)
                        .failureReason("Something goes wrong.")
                        .build())
                .build();
        when(proxy.injectCredentialsAndInvokeV2(decribePsProvisionRequest, proxyClient.client()::describePermissionSetProvisioningStatus))
                .thenReturn(describePsProvisionResponse);

        assertThatThrownBy(() -> {
            handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);
        }).isInstanceOf(CfnGeneralServiceException.class).hasMessageContaining("Request " + TEST_REQUEST_ID + " failed due to: Something goes wrong.");
    }
}
