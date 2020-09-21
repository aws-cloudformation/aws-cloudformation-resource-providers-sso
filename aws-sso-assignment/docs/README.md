# AWS::SSO::Assignment

Resource Type definition for SSO assignmet

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "Type" : "AWS::SSO::Assignment",
    "Properties" : {
        "<a href="#instancearn" title="InstanceArn">InstanceArn</a>" : <i>String</i>,
        "<a href="#targetid" title="TargetId">TargetId</a>" : <i>String</i>,
        "<a href="#targettype" title="TargetType">TargetType</a>" : <i>String</i>,
        "<a href="#permissionsetarn" title="PermissionSetArn">PermissionSetArn</a>" : <i>String</i>,
        "<a href="#principaltype" title="PrincipalType">PrincipalType</a>" : <i>String</i>,
        "<a href="#principalid" title="PrincipalId">PrincipalId</a>" : <i>String</i>
    }
}
</pre>

### YAML

<pre>
Type: AWS::SSO::Assignment
Properties:
    <a href="#instancearn" title="InstanceArn">InstanceArn</a>: <i>String</i>
    <a href="#targetid" title="TargetId">TargetId</a>: <i>String</i>
    <a href="#targettype" title="TargetType">TargetType</a>: <i>String</i>
    <a href="#permissionsetarn" title="PermissionSetArn">PermissionSetArn</a>: <i>String</i>
    <a href="#principaltype" title="PrincipalType">PrincipalType</a>: <i>String</i>
    <a href="#principalid" title="PrincipalId">PrincipalId</a>: <i>String</i>
</pre>

## Properties

#### InstanceArn

The sso instance that the permission set is owned.

_Required_: Yes

_Type_: String

_Minimum_: <code>10</code>

_Maximum_: <code>1224</code>

_Pattern_: <code>arn:aws:sso:::instance/(sso)?ins-[a-zA-Z0-9-.]{16}</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### TargetId

The account id to be provisioned.

_Required_: Yes

_Type_: String

_Pattern_: <code>\d{12}</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### TargetType

The type of resource to be provsioned to, only aws account now

_Required_: Yes

_Type_: String

_Allowed Values_: <code>AWS_ACCOUNT</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### PermissionSetArn

The permission set that the assignemt will be assigned

_Required_: Yes

_Type_: String

_Minimum_: <code>10</code>

_Maximum_: <code>1224</code>

_Pattern_: <code>arn:aws:sso:::permissionSet/(sso)?ins-[a-zA-Z0-9-.]{16}/ps-[a-zA-Z0-9-./]{16}</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### PrincipalType

The assignee's type, user/group

_Required_: Yes

_Type_: String

_Allowed Values_: <code>USER</code> | <code>GROUP</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### PrincipalId

The assignee's identifier, user id/group id

_Required_: Yes

_Type_: String

_Minimum_: <code>1</code>

_Maximum_: <code>47</code>

_Pattern_: <code>^([0-9a-f]{10}-|)[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}$</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

