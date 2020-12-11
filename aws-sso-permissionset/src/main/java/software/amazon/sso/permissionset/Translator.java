package software.amazon.sso.permissionset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.ssoadmin.model.CreatePermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.DeletePermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.DescribePermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.DescribePermissionSetResponse;
import software.amazon.awssdk.services.ssoadmin.model.PermissionSet;
import software.amazon.awssdk.services.ssoadmin.model.ProvisionPermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.ProvisionTargetType;
import software.amazon.awssdk.services.ssoadmin.model.Tag;
import software.amazon.awssdk.services.ssoadmin.model.TagResourceRequest;
import software.amazon.awssdk.services.ssoadmin.model.UntagResourceRequest;
import software.amazon.awssdk.services.ssoadmin.model.UpdatePermissionSetRequest;
import software.amazon.awssdk.services.ssoadmin.model.ValidationException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * This class is a centralized placeholder for
 *  - api request construction
 *  - object translation to/from aws sdk
 *  - resource model construction for read/list handlers
 */

public class Translator {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * Request to create a resource
   * @param model resource model
   * @return awsRequest the aws service request to create a resource
   */
  static CreatePermissionSetRequest translateToCreateRequest(final ResourceModel model) {
    return CreatePermissionSetRequest.builder()
            .name(model.getName())
            .description(model.getDescription())
            .instanceArn(model.getInstanceArn())
            .relayState(model.getRelayStateType())
            .sessionDuration(model.getSessionDuration())
            .tags(ConvertToSSOTag(model.getTags()))
            .build();
  }

  /**
   * Request to read a resource
   * @param model resource model
   * @return awsRequest the aws service request to describe a resource
   */
  static DescribePermissionSetRequest translateToReadRequest(final ResourceModel model) {
    return DescribePermissionSetRequest.builder()
            .instanceArn(model.getInstanceArn())
            .permissionSetArn(model.getPermissionSetArn())
            .build();
  }

  /**
   * Translates resource object from sdk into a resource model
   * @param awsResponse the aws service describe resource response
   * @return model resource model
   */
  static ResourceModel translateFromReadResponse(final DescribePermissionSetResponse readResult, String instanceArn,
                                                 List<software.amazon.sso.permissionset.Tag> tags) {
    PermissionSet returnedPermissionSet = readResult.permissionSet();
    ResourceModel returnedModel =  ResourceModel.builder()
            .permissionSetArn(returnedPermissionSet.permissionSetArn())
            .description(returnedPermissionSet.description())
            .name(returnedPermissionSet.name())
            .relayStateType(returnedPermissionSet.relayState())
            .sessionDuration(returnedPermissionSet.sessionDuration())
            .instanceArn(instanceArn)
            .build();
    if (tags != null && tags.size() > 0) {
      returnedModel.setTags(tags);
    }
    return returnedModel;
  }

  /**
   * Request to delete a resource
   * @param model resource model
   * @return awsRequest the aws service request to delete a resource
   */
  static DeletePermissionSetRequest translateToDeleteRequest(final ResourceModel model) {
    return DeletePermissionSetRequest.builder()
            .instanceArn(model.getInstanceArn())
            .permissionSetArn(model.getPermissionSetArn())
            .build();
  }

  /**
   * Request to update properties of a previously created resource
   * @param model resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static UpdatePermissionSetRequest translateToUpdateRequest(final ResourceModel model) {
    return UpdatePermissionSetRequest.builder()
            .description(model.getDescription())
            .permissionSetArn(model.getPermissionSetArn())
            .instanceArn(model.getInstanceArn())
            .relayState(model.getRelayStateType())
            .sessionDuration(model.getSessionDuration())
            .build();
  }

  /**
   * Request to untag resource
   * @param model resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static UntagResourceRequest translateToUntagResourceRequest(final ResourceModel model, List<String> tagKeys) {
    return UntagResourceRequest.builder()
            .instanceArn(model.getInstanceArn())
            .resourceArn(model.getPermissionSetArn())
            .tagKeys(tagKeys)
            .build();
  }

  /**
   * Request to tag resource
   * @param model resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static TagResourceRequest translateToTagResourceRequest(final ResourceModel model, List<Tag> tags) {
    return TagResourceRequest.builder()
            .instanceArn(model.getInstanceArn())
            .resourceArn(model.getPermissionSetArn())
            .tags(tags)
            .build();
  }

  static ProvisionPermissionSetRequest translateToProvsionPermissionSetRequest(final ResourceModel model) {
    return ProvisionPermissionSetRequest.builder()
            .instanceArn(model.getInstanceArn())
            .permissionSetArn(model.getPermissionSetArn())
            .targetType(ProvisionTargetType.ALL_PROVISIONED_ACCOUNTS)
            .build();
  }

  private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
    return Optional.ofNullable(collection)
        .map(Collection::stream)
        .orElseGet(Stream::empty);
  }

  static List<Tag> ConvertToSSOTag(List<software.amazon.sso.permissionset.Tag> tags) {
    List<Tag> ssoPermissionSetTags = new ArrayList<>();
    if (tags == null || tags.size() == 0) {
      return ssoPermissionSetTags;
    }
    for (software.amazon.sso.permissionset.Tag tag : tags) {
      ssoPermissionSetTags.add(Tag.builder().key(tag.getKey()).value(tag.getValue()).build());
    }
    return ssoPermissionSetTags;
  }

  static List<software.amazon.sso.permissionset.Tag> ConvertToModelTag(List<Tag> tags) {
    List<software.amazon.sso.permissionset.Tag> ssoPermissionSetTags = new ArrayList<>();
    if (tags == null) {
      return null;
    }
    for (Tag tag : tags) {
      ssoPermissionSetTags.add(new software.amazon.sso.permissionset.Tag().builder()
              .key(tag.key())
              .value(tag.value())
              .build());
    }
    return ssoPermissionSetTags;
  }

  static String processInlinePolicy(final Object policyDocument) {

    if(policyDocument == null) {
      return null;
    }

    if (policyDocument instanceof Map) {
      try {
        return OBJECT_MAPPER.writeValueAsString(policyDocument);
      } catch (final JsonProcessingException e) {
        throw ValidationException.builder().message(e.getMessage()).build();
      }
    }
    return (String)policyDocument;
  }
}
