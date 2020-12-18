# AWS::SSO::InstanceAccessControlAttributeConfiguration

Resource Type definition for SSO InstanceAccessControlAttributeConfiguration

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "Type" : "AWS::SSO::InstanceAccessControlAttributeConfiguration",
    "Properties" : {
        "<a href="#instancearn" title="InstanceArn">InstanceArn</a>" : <i>String</i>,
        "<a href="#instanceaccesscontrolattributeconfiguration" title="InstanceAccessControlAttributeConfiguration">InstanceAccessControlAttributeConfiguration</a>" : <i><a href="instanceaccesscontrolattributeconfiguration.md">InstanceAccessControlAttributeConfiguration</a></i>
    }
}
</pre>

### YAML

<pre>
Type: AWS::SSO::InstanceAccessControlAttributeConfiguration
Properties:
    <a href="#instancearn" title="InstanceArn">InstanceArn</a>: <i>String</i>
    <a href="#instanceaccesscontrolattributeconfiguration" title="InstanceAccessControlAttributeConfiguration">InstanceAccessControlAttributeConfiguration</a>: <i><a href="instanceaccesscontrolattributeconfiguration.md">InstanceAccessControlAttributeConfiguration</a></i>
</pre>

## Properties

#### InstanceArn

The sso instance that the InstanceAccessControlAttributeConfiguration will be owned.

_Required_: Yes

_Type_: String

_Minimum_: <code>10</code>

_Maximum_: <code>1224</code>

_Pattern_: <code>arn:aws:sso:::instance/(sso)?ins-[a-zA-Z0-9-.]{16}</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### InstanceAccessControlAttributeConfiguration

InstanceAccessControlAttributeConfiguration for  sso instance

_Required_: Yes

_Type_: <a href="instanceaccesscontrolattributeconfiguration.md">InstanceAccessControlAttributeConfiguration</a>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

## Return Values

### Ref

When you pass the logical ID of this resource to the intrinsic `Ref` function, Ref returns the InstanceArn.
