package software.amazon.sso.instanceaccesscontrolattributeconfiguration;
import com.google.common.collect.ImmutableMap;
import com.amazonaws.util.CollectionUtils;
import software.amazon.awssdk.services.ssoadmin.model.CreateInstanceAccessControlAttributeConfigurationRequest;
import software.amazon.awssdk.services.ssoadmin.model.DescribeInstanceAccessControlAttributeConfigurationRequest;
import software.amazon.awssdk.services.ssoadmin.model.DescribeInstanceAccessControlAttributeConfigurationResponse;
import software.amazon.awssdk.services.ssoadmin.model.UpdateInstanceAccessControlAttributeConfigurationRequest;
import software.amazon.awssdk.services.ssoadmin.model.DeleteInstanceAccessControlAttributeConfigurationRequest;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class is a centralized translation class for
 *  - api request construction
 *  - object translation to/from aws sdk
 *  - resource model construction for read/create/delete/update handlers
 */

public class Translator {

  /**
   * Request to create a resource
   * @param model resource model
   * @return CreateInstanceAccessControlAttributeConfigurationRequest the aws service request to create a resource
   */
  static CreateInstanceAccessControlAttributeConfigurationRequest translateToCreateRequest(final ResourceModel model) {
     validateModel(model);

        return CreateInstanceAccessControlAttributeConfigurationRequest.builder()
                .instanceArn(model.getInstanceArn())
                .instanceAccessControlAttributeConfiguration(convertToSSOConfiguration(model))
                .build();
        }

  /**
   * Request to read a resource
   * @param model resource model
   * @return DescribeInstanceAccessControlAttributeConfigurationRequest the aws service request to describe a resource
   */
  static DescribeInstanceAccessControlAttributeConfigurationRequest translateToDescribeRequest(final ResourceModel model) {
    return DescribeInstanceAccessControlAttributeConfigurationRequest.builder()
            .instanceArn(model.getInstanceArn())
            .build();
  }

  /**
   * Translates resource object from sdk into a resource model
   * @param readResult the DescribeInstanceAccessControlAttributeConfigurationResponse describe resource response
   * @return resource model
   */
  static ResourceModel translateFromDescribeResponse(final DescribeInstanceAccessControlAttributeConfigurationResponse readResult,
                                                     final String instanceArn) {
    List<AccessControlAttribute> listAttributes = readResult
            .instanceAccessControlAttributeConfiguration()
            .accessControlAttributes()
            .stream()
            .map(accessControlAttribute -> AccessControlAttribute
                    .builder()
                    .key(accessControlAttribute.key())
                    .value(AccessControlAttributeValue
                            .builder()
                            .source(accessControlAttribute.value().source())
                            .build())
                    .build())
            .collect(Collectors.toList());

    ResourceModel returnedModel =  ResourceModel.builder()
            .instanceArn(instanceArn)
            .accessControlAttributes(listAttributes)
            .build();
    return returnedModel;
  }

  /**
   * Request to delete a resource
   * @param model resource model
   * @return DeleteInstanceAccessControlAttributeConfigurationRequest the aws service request to delete a resource
   */
  static DeleteInstanceAccessControlAttributeConfigurationRequest translateToDeleteRequest(final ResourceModel model) {
    return DeleteInstanceAccessControlAttributeConfigurationRequest.builder()
            .instanceArn(model.getInstanceArn())
            .build();
  }

  /**
   * Request to update properties of a previously created resource
   * @param model resource model
   * @return UpdateInstanceAccessControlAttributeConfigurationRequest the aws service request to modify a resource
   */
  static UpdateInstanceAccessControlAttributeConfigurationRequest translateToUpdateRequest(final ResourceModel model) {
      validateModel(model);
      return UpdateInstanceAccessControlAttributeConfigurationRequest.builder()
              .instanceArn(model.getInstanceArn())
              .instanceAccessControlAttributeConfiguration(convertToSSOConfiguration(model))
              .build();
    }

  /**
   * Convert resource model to SSO model
   * @param model  ResourceModel
   * @return software.amazon.awssdk.services.ssoadmin.model.InstanceAccessControlAttributeConfiguration
   */
  static software.amazon.awssdk.services.ssoadmin.model.InstanceAccessControlAttributeConfiguration convertToSSOConfiguration(ResourceModel model) {
      List<AccessControlAttribute> attributes;

      if (model.getInstanceAccessControlAttributeConfiguration() == null) {
          attributes = model.getAccessControlAttributes();
      } else {
          attributes = model.getInstanceAccessControlAttributeConfiguration().getAccessControlAttributes();
      }

      List<software.amazon.awssdk.services.ssoadmin.model.AccessControlAttribute> ssoConfiguration = attributes
              .stream()
              .map(accessControlAttribute -> software.amazon.awssdk.services.ssoadmin.model.AccessControlAttribute
                      .builder()
                      .key(accessControlAttribute.getKey())
                      .value(software.amazon.awssdk.services.ssoadmin.model.AccessControlAttributeValue
                              .builder()
                              .source(accessControlAttribute.getValue().getSource())
                              .build())
                      .build())
              .collect(Collectors.toList());

      return software.amazon.awssdk.services.ssoadmin.model.InstanceAccessControlAttributeConfiguration
              .builder()
              .accessControlAttributes(ssoConfiguration)
              .build();
  }

  /**
   * Convert SSO model to resource model
   * @param describeResponse DescribeInstanceAccessControlAttributeConfigurationResponse
   * @return ResourceModel
   */
  static ResourceModel convertToCFConfiguration(DescribeInstanceAccessControlAttributeConfigurationResponse describeResponse) {

        List<software.amazon.sso.instanceaccesscontrolattributeconfiguration.AccessControlAttribute> ssoConfiguration = describeResponse
                .instanceAccessControlAttributeConfiguration()
                .accessControlAttributes()
                .stream()
                .map(accessControlAttribute -> AccessControlAttribute
                        .builder()
                        .key(accessControlAttribute.key())
                        .value(AccessControlAttributeValue
                                .builder()
                                .source(accessControlAttribute.value().source())
                                .build())
                        .build())
                .collect(Collectors.toList());
    
        return ResourceModel
                .builder()
                .accessControlAttributes(ssoConfiguration)
                .build();
      }

  /**
   * Make sure InstanceAccessControlAttributeConfigurations are equals
   * @param model  current ResourceModel
   * @param expected desired ResourceModel
   * @return boolean
   */
  static boolean accessControlAttributeConfigsIsEquals(ResourceModel model, ResourceModel expected){

        List<AccessControlAttribute> attributes;
        List<AccessControlAttribute> expectedAttributes;
    
        if (model.getInstanceAccessControlAttributeConfiguration() == null) {
          attributes = model.getAccessControlAttributes();
        } else {
          attributes = model.getInstanceAccessControlAttributeConfiguration().getAccessControlAttributes();
        }
    
        if (expected.getInstanceAccessControlAttributeConfiguration() == null) {
          expectedAttributes = expected.getAccessControlAttributes();
        } else {
          expectedAttributes = expected.getInstanceAccessControlAttributeConfiguration().getAccessControlAttributes();
        }
    
        ImmutableMap<String, Set<String>> actualValues = ImmutableMap.<String, Set<String>>builder()
                .putAll(attributes
                        .stream()
                        .collect(Collectors.toMap(
                                accessControlAttribute -> accessControlAttribute.getKey(),
                                accessControlAttribute -> accessControlAttribute.getValue().getSource().stream().collect(Collectors.toSet())
                        )))
                .build();
    
        ImmutableMap<String, Set<String> > valuesFromRResponse = ImmutableMap.<String, Set<String>>builder()
                .putAll(expectedAttributes
                        .stream()
                        .collect(Collectors.toMap(
                                accessControlAttribute -> accessControlAttribute.getKey(),
                                accessControlAttribute -> accessControlAttribute.getValue().getSource().stream().collect(Collectors.toSet())
                        )))
                .build();
    
        return actualValues.equals(valuesFromRResponse);
      }

  private static void validateModel(ResourceModel model) {
      if (model.getInstanceAccessControlAttributeConfiguration() == null && CollectionUtils.isNullOrEmpty(model.getAccessControlAttributes())) {
          throw new CfnInvalidRequestException("Resource model must contain an InstanceAccessControlAttributeConfiguration or an AccessControlAttributes property");
      }

      if (model.getInstanceAccessControlAttributeConfiguration() != null && !CollectionUtils.isNullOrEmpty(model.getAccessControlAttributes())) {
          throw new CfnInvalidRequestException("Either an InstanceAccessControlAttributeConfiguration or an AccessControlAttributes property can be present in the schema, not both.");
      }
  }
}
