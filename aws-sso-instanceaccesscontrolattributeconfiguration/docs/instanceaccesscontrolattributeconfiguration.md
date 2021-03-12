# AWS::SSO::InstanceAccessControlAttributeConfiguration InstanceAccessControlAttributeConfiguration

The InstanceAccessControlAttributeConfiguration property has been deprecated but is still supported for backwards compatibility purposes. We recomend that you use  AccessControlAttributes property instead.

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "<a href="#accesscontrolattributes" title="AccessControlAttributes">AccessControlAttributes</a>" : <i>[ <a href="accesscontrolattribute.md">AccessControlAttribute</a>, ... ]</i>
}
</pre>

### YAML

<pre>
<a href="#accesscontrolattributes" title="AccessControlAttributes">AccessControlAttributes</a>: <i>
      - <a href="accesscontrolattribute.md">AccessControlAttribute</a></i>
</pre>

## Properties

#### AccessControlAttributes

_Required_: Yes

_Type_: List of <a href="accesscontrolattribute.md">AccessControlAttribute</a>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)
