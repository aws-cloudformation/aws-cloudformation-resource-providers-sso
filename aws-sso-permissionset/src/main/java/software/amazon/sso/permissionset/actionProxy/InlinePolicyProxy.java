package software.amazon.sso.permissionset.actionProxy;

import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.DeleteInlinePolicyFromPermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.GetInlinePolicyForPermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.PutInlinePolicyToPermissionSetRequest;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProxyClient;

public class InlinePolicyProxy {

    private AmazonWebServicesClientProxy proxy;
    private ProxyClient<SsoAdminClient> proxyClient;

    public InlinePolicyProxy(AmazonWebServicesClientProxy proxy, ProxyClient<SsoAdminClient> proxyClient) {
        this.proxy = proxy;
        this.proxyClient = proxyClient;
    }

    public void putInlinePolicyToPermissionSet(String instanceArn, String permissionSetArn, String inlinePolicy) {
        PutInlinePolicyToPermissionSetRequest request = PutInlinePolicyToPermissionSetRequest.builder()
                .instanceArn(instanceArn)
                .permissionSetArn(permissionSetArn)
                .inlinePolicy(inlinePolicy)
                .build();
        proxy.injectCredentialsAndInvokeV2(request, proxyClient.client()::putInlinePolicyToPermissionSet);
    }

    public void deleteInlinePolicyFromPermissionSet(String instanceArn, String permissionSetArn) {
        DeleteInlinePolicyFromPermissionSetRequest request = DeleteInlinePolicyFromPermissionSetRequest.builder()
                .instanceArn(instanceArn)
                .permissionSetArn(permissionSetArn)
                .build();
        proxy.injectCredentialsAndInvokeV2(request, proxyClient.client()::deleteInlinePolicyFromPermissionSet);
    }

    public String getInlinePolicyForPermissionSet(String instanceArn, String permissionSetArn) {
        GetInlinePolicyForPermissionSetRequest request = GetInlinePolicyForPermissionSetRequest.builder()
                .permissionSetArn(permissionSetArn)
                .instanceArn(instanceArn)
                .build();
        return proxy.injectCredentialsAndInvokeV2(request, proxyClient.client()::getInlinePolicyForPermissionSet).inlinePolicy();
    }
}
