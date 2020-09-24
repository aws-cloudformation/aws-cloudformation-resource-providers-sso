package software.amazon.sso.assignment.actionProxy;

import com.amazonaws.util.StringUtils;
import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.AccountAssignment;
import software.amazon.awssdk.services.ssoadmin.model.ListAccountAssignmentsResponse;
import software.amazon.awssdk.services.ssoadmin.model.ResourceNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProxyClient;

import java.util.List;

import static software.amazon.sso.assignment.Translator.translateToListRequest;

public class AssignmentProxy {

    private AmazonWebServicesClientProxy proxy;
    private ProxyClient<SsoAdminClient> proxyClient;
    private Logger logger;

    public AssignmentProxy(AmazonWebServicesClientProxy proxy, ProxyClient<SsoAdminClient> proxyClient, Logger logger) {
        this.proxy = proxy;
        this.proxyClient = proxyClient;
        this.logger = logger;
    }

    public boolean checkIfAssignmentAlreadyExist(String instanceArn, String targetId, String permissionSetArn, String principalId, String principalType) {

        String nextToken = null;
        do {
            try {
                ListAccountAssignmentsResponse listAccountAssignmentsResponse = proxy.injectCredentialsAndInvokeV2(translateToListRequest(instanceArn, targetId, permissionSetArn, nextToken),
                        proxyClient.client()::listAccountAssignments);
                if (listAccountAssignmentsResponse.accountAssignments() != null && listAccountAssignmentsResponse.accountAssignments().size() > 0) {
                    if (checkAssignmentExistsInList(targetId, permissionSetArn, principalId, principalType, listAccountAssignmentsResponse.accountAssignments())) {
                        return true;
                    }
                }
                nextToken = listAccountAssignmentsResponse.nextToken();
            } catch (ResourceNotFoundException e) {
                return false;
            }
        } while (!StringUtils.isNullOrEmpty(nextToken));

        return false;
    }

    public boolean checkAssignmentExistsInList(String targetId, String permissionSetArn, String principalId, String principalType, List<AccountAssignment> existingAssignments) {
        AccountAssignment assignment = AccountAssignment.builder()
                .accountId(targetId)
                .permissionSetArn(permissionSetArn)
                .principalId(principalId)
                .principalType(principalType)
                .build();
        if (existingAssignments.contains(assignment)) {
            return true;
        } else {
            return false;
        }
    }
}
