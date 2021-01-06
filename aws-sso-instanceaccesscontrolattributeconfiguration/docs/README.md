# AWS::SSO::InstanceAccessControlAttributeConfiguration

Enables the attribute-based access control (ABAC) feature for the specified AWS SSO instance. You can also specify new attributes to add to your ABAC configuration during the enabling process. For more information about ABAC, see [Attribute-Based Access Control](https://docs.aws.amazon.com/singlesignon/latest/userguide/abac.html) in the AWS SSO User Guide.

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "Type" : "AWS::SSO::InstanceAccessControlAttributeConfiguration",
    "Properties" : {
        "<a href="#instancearn" title="InstanceArn">InstanceArn</a>" : <i>String</i>,
        "<a href="#accesscontrolattributes" title="AccessControlAttributes">AccessControlAttributes</a>" : <i>[ <a href="accesscontrolattribute.md">AccessControlAttribute</a>, ... ]</i>
    }
}
</pre>

### YAML

<pre>
Type: AWS::SSO::InstanceAccessControlAttributeConfiguration
Properties:
    <a href="#instancearn" title="InstanceArn">InstanceArn</a>: <i>String</i>
    <a href="#accesscontrolattributes" title="AccessControlAttributes">AccessControlAttributes</a>: <i>
      - <a href="accesscontrolattribute.md">AccessControlAttribute</a></i>
</pre>

## Properties

#### InstanceArn

The ARN of the AWS SSO instance under which the operation will be executed.

_Required_: Yes

_Type_: String

_Minimum_: <code>10</code>

_Maximum_: <code>1224</code>

_Pattern_: <code>arn:aws:sso:::instance/(sso)?ins-[a-zA-Z0-9-.]{16}</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)
#### AccessControlAttributes

Lists the attributes that are configured for ABAC in the specified AWS SSO instance.

_Required_: Yes (Unless deprecated field InstanceAccessControlAttributeConfiguration is in use, see deprecation notice)".

_Type_: List of <a href="accesscontrolattribute.md">AccessControlAttribute</a>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)
#### InstanceAccessControlAttributeConfiguration

The InstanceAccessControlAttributeConfiguration property has been deprecated but is still supported for backwards compatibility purposses. We recomend that you use  `AccessControlAttributes` property instead.

_Required_: No

_Type_: <a href="instanceaccesscontrolattributeconfiguration.md">InstanceAccessControlAttributeConfiguration</a>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

## Return Values

### Ref

When you pass the logical ID of this resource to the intrinsic `Ref` function, Ref returns the InstanceArn.
