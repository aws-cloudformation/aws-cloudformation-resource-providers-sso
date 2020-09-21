package software.amazon.sso.assignment;

import software.amazon.awssdk.services.ssoadmin.model.CreateAccountAssignmentRequest;
import software.amazon.awssdk.services.ssoadmin.model.DeleteAccountAssignmentRequest;
import software.amazon.awssdk.services.ssoadmin.model.DescribeAccountAssignmentCreationStatusRequest;
import software.amazon.awssdk.services.ssoadmin.model.DescribeAccountAssignmentDeletionStatusRequest;
import software.amazon.awssdk.services.ssoadmin.model.ListAccountAssignmentsRequest;

/**
 * This class is a centralized placeholder for
 *  - api request construction
 *  - object translation to/from aws sdk
 *  - resource model construction for read/list handlers
 */

public class Translator {

  /**
   * Request to create a resource
   */
  public static CreateAccountAssignmentRequest translateToCreateRequest(ResourceModel model) {
    return CreateAccountAssignmentRequest.builder()
            .instanceArn(model.getInstanceArn())
            .permissionSetArn(model.getPermissionSetArn())
            .targetType(model.getTargetType())
            .targetId(model.getTargetId())
            .principalType(model.getPrincipalType())
            .principalId(model.getPrincipalId())
            .build();
  }

  /**
   * Request to delete a resource
   */
  public static DeleteAccountAssignmentRequest translateToDeleteRequest(ResourceModel model) {
    return DeleteAccountAssignmentRequest.builder()
            .instanceArn(model.getInstanceArn())
            .permissionSetArn(model.getPermissionSetArn())
            .targetType(model.getTargetType())
            .targetId(model.getTargetId())
            .principalType(model.getPrincipalType())
            .principalId(model.getPrincipalId())
            .build();
  }

  public static DescribeAccountAssignmentCreationStatusRequest translateToDescribeCreationStatusRequest(String instanceArn, String requestId) {
    return DescribeAccountAssignmentCreationStatusRequest.builder()
            .instanceArn(instanceArn)
            .accountAssignmentCreationRequestId(requestId)
            .build();
  }

  public static DescribeAccountAssignmentDeletionStatusRequest translateToDescribeDeletionStatusRequest(String instanceArn, String requestId) {
    return DescribeAccountAssignmentDeletionStatusRequest.builder()
            .instanceArn(instanceArn)
            .accountAssignmentDeletionRequestId(requestId)
            .build();
  }

  /**
   * Request to list resources
   * @param nextToken token passed to the aws service list resources request
   * @return awsRequest the aws service request to list resources within aws account
   */
  public static ListAccountAssignmentsRequest translateToListRequest(String instanceArn, String targetId, String permissionSetArn, String nextToken) {
    return ListAccountAssignmentsRequest.builder()
            .accountId(targetId)
            .instanceArn(instanceArn)
            .permissionSetArn(permissionSetArn)
            .nextToken(nextToken)
            .build();
  }
}
