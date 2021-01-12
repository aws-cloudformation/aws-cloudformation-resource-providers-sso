package software.amazon.sso.permissionset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.AttachedManagedPolicy;
import software.amazon.awssdk.services.ssoadmin.model.DescribePermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.DescribePermissionSetResponse;
import software.amazon.awssdk.services.ssoadmin.model.GetInlinePolicyForPermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.GetInlinePolicyForPermissionSetResponse;
import software.amazon.awssdk.services.ssoadmin.model.ListManagedPoliciesInPermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.ListManagedPoliciesInPermissionSetResponse;
import software.amazon.awssdk.services.ssoadmin.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.ssoadmin.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.ssoadmin.model.PermissionSet;
import software.amazon.awssdk.services.ssoadmin.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ssoadmin.model.Tag;
import software.amazon.awssdk.services.ssoadmin.model.ThrottlingException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static software.amazon.sso.permissionset.TestConstants.TEST_ADMIN_MANAGED_POLICY;
import static software.amazon.sso.permissionset.TestConstants.TEST_INLINE_POLICY;
import static software.amazon.sso.permissionset.TestConstants.TEST_PERMISSION_SET_ARN;
import static software.amazon.sso.permissionset.TestConstants.TEST_PERMISSION_SET_DESCRIPTION;
import static software.amazon.sso.permissionset.TestConstants.TEST_PERMISSION_SET_NAME;
import static software.amazon.sso.permissionset.TestConstants.TEST_READONLY_POLICY;
import static software.amazon.sso.permissionset.TestConstants.TEST_RELAY_STATE;
import static software.amazon.sso.permissionset.TestConstants.TEST_SESSION_DURATION;
import static software.amazon.sso.permissionset.TestConstants.TEST_SSO_INSTANCE_ARN;
import static software.amazon.sso.permissionset.TestConstants.THROTTLING_MESSAGE;

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
        when(proxyClient.client()).thenReturn(sso);
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        final ReadHandler handler = new ReadHandler();

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
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .tags(tags)
                .managedPolicies(managedPolicyArns)
                .inlinePolicy(TEST_INLINE_POLICY)
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
        ListManagedPoliciesInPermissionSetResponse listAPResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(attachedManagedPolicies)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAPRequest, proxyClient.client()::listManagedPoliciesInPermissionSet))
                .thenReturn(listAPResponse);

        GetInlinePolicyForPermissionSetRequest getIPRequest = GetInlinePolicyForPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        GetInlinePolicyForPermissionSetResponse getIPResult = GetInlinePolicyForPermissionSetResponse.builder()
                .inlinePolicy(TEST_INLINE_POLICY)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(getIPRequest, proxyClient.client()::getInlinePolicyForPermissionSet))
                .thenReturn(getIPResult);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_Success_WithOnlyIdentifierInput() {
        final ReadHandler handler = new ReadHandler();

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

        final ResourceModel expectModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .tags(tags)
                .managedPolicies(managedPolicyArns)
                .inlinePolicy(TEST_INLINE_POLICY)
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

        ListTagsForResourceRequest listTagRequest = ListTagsForResourceRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .resourceArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListTagsForResourceResponse listTagResult = ListTagsForResourceResponse.builder()
                .tags(covertedTags)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listTagRequest, proxyClient.client()::listTagsForResource))
                .thenReturn(listTagResult);

        ListManagedPoliciesInPermissionSetRequest listAPRequest = ListManagedPoliciesInPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListManagedPoliciesInPermissionSetResponse listAttachedPoliciesResult = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(attachedManagedPolicies)
                .build();;
        when(proxy.injectCredentialsAndInvokeV2(listAPRequest, proxyClient.client()::listManagedPoliciesInPermissionSet))
                .thenReturn(listAttachedPoliciesResult);

        GetInlinePolicyForPermissionSetRequest getIPRequest = GetInlinePolicyForPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        GetInlinePolicyForPermissionSetResponse getIPResult = GetInlinePolicyForPermissionSetResponse.builder()
                .inlinePolicy(TEST_INLINE_POLICY)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(getIPRequest, proxyClient.client()::getInlinePolicyForPermissionSet))
                .thenReturn(getIPResult);


        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(ResourceModel.builder().instanceArn(TEST_SSO_INSTANCE_ARN)
                        .permissionSetArn(TEST_PERMISSION_SET_ARN)
                        .build())
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(expectModel);
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_Throttling_PS_Retryable_Exception() {
        final ReadHandler handler = new ReadHandler();

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

        DescribePermissionSetRequest psDescribeRequest = DescribePermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();

        when(proxy.injectCredentialsAndInvokeV2(psDescribeRequest, proxyClient.client()::describePermissionSet))
                .thenThrow(ThrottlingException.builder().message(THROTTLING_MESSAGE).build());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(ResourceModel.builder().instanceArn(TEST_SSO_INSTANCE_ARN)
                        .permissionSetArn(TEST_PERMISSION_SET_ARN)
                        .build())
                .build();

        assertThatThrownBy(() -> handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger))
                .isInstanceOf(CfnThrottlingException.class);
    }

    @Test
    public void handleRequest_Throttling_PS_Retryable_Success() {
        final ReadHandler handler = new ReadHandler();

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
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .tags(tags)
                .managedPolicies(managedPolicyArns)
                .inlinePolicy(TEST_INLINE_POLICY)
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
                .thenThrow(ThrottlingException.builder().message(THROTTLING_MESSAGE).build())
                .thenThrow(ThrottlingException.builder().message(THROTTLING_MESSAGE).build())
                .thenThrow(ThrottlingException.builder().message(THROTTLING_MESSAGE).build())
                .thenReturn(psDescribeResult);

        ListManagedPoliciesInPermissionSetRequest listAPRequest = ListManagedPoliciesInPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListManagedPoliciesInPermissionSetResponse listAPResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(attachedManagedPolicies)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAPRequest, proxyClient.client()::listManagedPoliciesInPermissionSet))
                .thenReturn(listAPResponse);

        GetInlinePolicyForPermissionSetRequest getIPRequest = GetInlinePolicyForPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        GetInlinePolicyForPermissionSetResponse getIPResult = GetInlinePolicyForPermissionSetResponse.builder()
                .inlinePolicy(TEST_INLINE_POLICY)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(getIPRequest, proxyClient.client()::getInlinePolicyForPermissionSet))
                .thenReturn(getIPResult);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_Throttling_Sequential_Read_Exception() {
        final ReadHandler handler = new ReadHandler();

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

        final ResourceModel expectModel = ResourceModel.builder()
                .name(TEST_PERMISSION_SET_NAME)
                .description(TEST_PERMISSION_SET_DESCRIPTION)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .tags(tags)
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

        ListTagsForResourceRequest listTagRequest = ListTagsForResourceRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .resourceArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListTagsForResourceResponse listTagResult = ListTagsForResourceResponse.builder()
                .tags(covertedTags)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listTagRequest, proxyClient.client()::listTagsForResource))
                .thenReturn(listTagResult);

        ListManagedPoliciesInPermissionSetRequest listAPRequest = ListManagedPoliciesInPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAPRequest, proxyClient.client()::listManagedPoliciesInPermissionSet))
                .thenThrow(ThrottlingException.builder().message(THROTTLING_MESSAGE).build());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(ResourceModel.builder().instanceArn(TEST_SSO_INSTANCE_ARN)
                        .permissionSetArn(TEST_PERMISSION_SET_ARN)
                        .build())
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);


        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).contains("Read request got throttled");
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.Throttling);
    }

    @Test
    public void handleRequest_Throttling_Sequential_Read_Success() {
        final ReadHandler handler = new ReadHandler();

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
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .sessionDuration(TEST_SESSION_DURATION)
                .relayStateType(TEST_RELAY_STATE)
                .tags(tags)
                .managedPolicies(managedPolicyArns)
                .inlinePolicy(TEST_INLINE_POLICY)
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
        ListManagedPoliciesInPermissionSetResponse listAPResponse = ListManagedPoliciesInPermissionSetResponse.builder()
                .attachedManagedPolicies(attachedManagedPolicies)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listAPRequest, proxyClient.client()::listManagedPoliciesInPermissionSet))
                .thenThrow(ThrottlingException.builder().message(THROTTLING_MESSAGE).build())
                .thenThrow(ThrottlingException.builder().message(THROTTLING_MESSAGE).build())
                .thenThrow(ThrottlingException.builder().message(THROTTLING_MESSAGE).build())
                .thenReturn(listAPResponse);

        GetInlinePolicyForPermissionSetRequest getIPRequest = GetInlinePolicyForPermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        GetInlinePolicyForPermissionSetResponse getIPResult = GetInlinePolicyForPermissionSetResponse.builder()
                .inlinePolicy(TEST_INLINE_POLICY)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(getIPRequest, proxyClient.client()::getInlinePolicyForPermissionSet))
                .thenReturn(getIPResult);

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_ResourceNotFound_Failed() {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder()
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        DescribePermissionSetRequest psDescribeRequest = DescribePermissionSetRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .permissionSetArn(TEST_PERMISSION_SET_ARN)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(psDescribeRequest, proxyClient.client()::describePermissionSet))
                .thenThrow(ResourceNotFoundException.builder().message("Permission set not found.").build());

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).contains("Permission set not found.");
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }
}
