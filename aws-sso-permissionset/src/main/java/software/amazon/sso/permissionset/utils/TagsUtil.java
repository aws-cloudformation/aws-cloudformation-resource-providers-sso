package software.amazon.sso.permissionset.utils;

import com.amazonaws.util.StringUtils;
import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.ssoadmin.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.ssoadmin.model.Tag;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProxyClient;

import java.util.ArrayList;
import java.util.List;

public class TagsUtil {
    public static List<Tag> getResourceTags(String instanceArn, String resourceArn,
                                            AmazonWebServicesClientProxy proxy, ProxyClient<SsoAdminClient> proxyClient) {

        List<Tag> tags = new ArrayList<>();
        String nextToken = null;
        do {
            ListTagsForResourceRequest request = ListTagsForResourceRequest.builder()
                    .instanceArn(instanceArn)
                    .resourceArn(resourceArn)
                    .nextToken(nextToken)
                    .build();
            ListTagsForResourceResponse result
                    = proxy.injectCredentialsAndInvokeV2(request, proxyClient.client()::listTagsForResource);
            if (result.tags() != null && result.tags().size() > 0) {
                tags.addAll(result.tags());
            }
            nextToken = result.nextToken();
        } while (!StringUtils.isNullOrEmpty(nextToken));
        return tags;
    }
}
