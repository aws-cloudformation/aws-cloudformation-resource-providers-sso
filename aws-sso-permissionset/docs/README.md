# AWS::SSO::PermissionSet

Resource Type definition for SSO PermissionSet

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "Type" : "AWS::SSO::PermissionSet",
    "Properties" : {
        "<a href="#name" title="Name">Name</a>" : <i>String</i>,
        "<a href="#description" title="Description">Description</a>" : <i>String</i>,
        "<a href="#instancearn" title="InstanceArn">InstanceArn</a>" : <i>String</i>,
        "<a href="#sessionduration" title="SessionDuration">SessionDuration</a>" : <i>String</i>,
        "<a href="#relaystatetype" title="RelayStateType">RelayStateType</a>" : <i>String</i>,
        "<a href="#managedpolicies" title="ManagedPolicies">ManagedPolicies</a>" : <i>[ String, ... ]</i>,
        "<a href="#inlinepolicy" title="InlinePolicy">InlinePolicy</a>" : <i>String</i>,
        "<a href="#tags" title="Tags">Tags</a>" : <i>[ <a href="tag.md">Tag</a>, ... ]</i>
    }
}
</pre>

### YAML

<pre>
Type: AWS::SSO::PermissionSet
Properties:
    <a href="#name" title="Name">Name</a>: <i>String</i>
    <a href="#description" title="Description">Description</a>: <i>String</i>
    <a href="#instancearn" title="InstanceArn">InstanceArn</a>: <i>String</i>
    <a href="#sessionduration" title="SessionDuration">SessionDuration</a>: <i>String</i>
    <a href="#relaystatetype" title="RelayStateType">RelayStateType</a>: <i>String</i>
    <a href="#managedpolicies" title="ManagedPolicies">ManagedPolicies</a>: <i>
      - String</i>
    <a href="#inlinepolicy" title="InlinePolicy">InlinePolicy</a>: <i>String</i>
    <a href="#tags" title="Tags">Tags</a>: <i>
      - <a href="tag.md">Tag</a></i>
</pre>

## Properties

#### Name

The name you want to assign to this permission set.

_Required_: Yes

_Type_: String

_Minimum_: <code>1</code>

_Maximum_: <code>32</code>

_Pattern_: <code>[\w+=,.@-]+</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### Description

The permission set description.

_Required_: No

_Type_: String

_Minimum_: <code>1</code>

_Maximum_: <code>700</code>

_Pattern_: <code>[\p{L}\p{M}\p{Z}\p{S}\p{N}\p{P}]*</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### InstanceArn

The sso instance arn that the permission set is owned.

_Required_: Yes

_Type_: String

_Minimum_: <code>10</code>

_Maximum_: <code>1224</code>

_Pattern_: <code>arn:aws:sso:::instance/(sso)?ins-[a-zA-Z0-9-.]{16}</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### SessionDuration

The length of time that a user can be signed in to an AWS account.

_Required_: No

_Type_: String

_Minimum_: <code>1</code>

_Maximum_: <code>100</code>

_Pattern_: <code>^(-?)P(?=\d|T\d)(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)([DW]))?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### RelayStateType

The relay state URL that redirect links to any service in the AWS Management Console.

_Required_: No

_Type_: String

_Minimum_: <code>1</code>

_Maximum_: <code>240</code>

_Pattern_: <code>[a-zA-Z0-9&amp;$@#\/%?=~\-_'&quot;|!:,.;*+\[\]\ \(\)\{\}]+</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### ManagedPolicies

_Required_: No

_Type_: List of String

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### InlinePolicy

The inline policy to put in permission set.

_Required_: No

_Type_: String

_Minimum_: <code>1</code>

_Maximum_: <code>10240</code>

_Pattern_: <code>[\w+=,.@-]+</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Tags

_Required_: No

_Type_: List of <a href="tag.md">Tag</a>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

## Return Values

### Fn::GetAtt

The `Fn::GetAtt` intrinsic function returns a value for a specified attribute of this type. The following are the available attributes and sample return values.

For more information about using the `Fn::GetAtt` intrinsic function, see [Fn::GetAtt](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/intrinsic-function-reference-getatt.html).

#### PermissionSetArn

The permission set that the policy will be attached to

