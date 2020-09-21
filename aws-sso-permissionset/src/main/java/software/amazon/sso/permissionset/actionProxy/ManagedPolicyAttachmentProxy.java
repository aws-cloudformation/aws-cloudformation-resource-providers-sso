package software.amazon.sso.permissionset.actionProxy;

import com.amazonaws.util.StringUtils;
import com.google.common.collect.Sets;
import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.AttachManagedPolicyToPermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.AttachedManagedPolicy;
import software.amazon.awssdk.services.ssoadmin.model.DetachManagedPolicyFromPermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.ListManagedPoliciesInPermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.ListManagedPoliciesInPermissionSetResponse;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.sso.permissionset.ResourceModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static software.amazon.sso.permissionset.utils.Constants.MANAGED_POLICIES_LIMIT_EXCEED_MESSAGE;

public class ManagedPolicyAttachmentProxy {

    private AmazonWebServicesClientProxy proxy;
    private ProxyClient<SsoAdminClient> proxyClient;

    public ManagedPolicyAttachmentProxy(AmazonWebServicesClientProxy proxy, ProxyClient<SsoAdminClient> proxyClient) {
        this.proxy = proxy;
        this.proxyClient = proxyClient;
    }

    public void updateManagedPolicyAttachment(String instanceArn,
                                              String permissionSetArn,
                                              List<String> updatedManagedPolicies) {
        if (updatedManagedPolicies != null && updatedManagedPolicies.size() > 0) {
            //This is set to match IAM hard limit of role managed policy attachment policy.
            if (updatedManagedPolicies.size() > 20) {
                throw new CfnServiceLimitExceededException(ResourceModel.TYPE_NAME, MANAGED_POLICIES_LIMIT_EXCEED_MESSAGE);
            }

            List<String> attachedManagedPolicies = getAttachedManagedPolicies(instanceArn, permissionSetArn);
            Set<String> previousManagedPolicies = new HashSet<>(attachedManagedPolicies);
            Set<String> newManagedPolicies = new HashSet<>(updatedManagedPolicies);

            Set<String> managedPoliciesToDetach = Sets.difference(previousManagedPolicies, newManagedPolicies);
            Set<String> managedPoliciesToAttach = Sets.difference(newManagedPolicies, previousManagedPolicies);

            detachManagedPolicies(managedPoliciesToDetach, instanceArn, permissionSetArn);
            attachManagedPolicies(managedPoliciesToAttach, instanceArn, permissionSetArn);
        }
    }

    public List<String> getAttachedManagedPolicies(String instanceArn,
                                                          String permissionSetArn) {
        List<String> existingPolicies = new ArrayList<>();

        String nextToken = null;
        do {
            ListManagedPoliciesInPermissionSetRequest listRequest = ListManagedPoliciesInPermissionSetRequest.builder()
                    .instanceArn(instanceArn)
                    .permissionSetArn(permissionSetArn)
                    .nextToken(null)
                    .build();
            ListManagedPoliciesInPermissionSetResponse result =
                    proxy.injectCredentialsAndInvokeV2(listRequest, proxyClient.client()::listManagedPoliciesInPermissionSet);
            if (result.attachedManagedPolicies() != null && result.attachedManagedPolicies().size() > 0) {
                for (AttachedManagedPolicy attachedManagedPolicy: result.attachedManagedPolicies()) {
                    existingPolicies.add(attachedManagedPolicy.arn());
                }
            }
            nextToken = result.nextToken();
        } while (!StringUtils.isNullOrEmpty(nextToken));

        return existingPolicies;
    }

    private void detachManagedPolicies(Set<String> managedPoliciesToDetach, String instanceArn, String permissionSetArn) {
        for(String managedPolicy: managedPoliciesToDetach) {

            DetachManagedPolicyFromPermissionSetRequest request = DetachManagedPolicyFromPermissionSetRequest.builder()
                    .instanceArn(instanceArn)
                    .permissionSetArn(permissionSetArn)
                    .managedPolicyArn(managedPolicy)
                    .build();
            proxy.injectCredentialsAndInvokeV2(request, proxyClient.client()::detachManagedPolicyFromPermissionSet);
        }
    }

    private void attachManagedPolicies(Set<String> managedPoliciesToAttach, String instanceArn, String permissionSetArn) {
        for(String managedPolicy: managedPoliciesToAttach) {
            AttachManagedPolicyToPermissionSetRequest request = AttachManagedPolicyToPermissionSetRequest.builder()
                    .instanceArn(instanceArn)
                    .permissionSetArn(permissionSetArn)
                    .managedPolicyArn(managedPolicy)
                    .build();
            proxy.injectCredentialsAndInvokeV2(request, proxyClient.client()::attachManagedPolicyToPermissionSet);
        }
    }
}
