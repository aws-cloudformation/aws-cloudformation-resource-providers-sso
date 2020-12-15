package software.amazon.sso.permissionset.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.awssdk.services.ssoadmin.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.ssoadmin.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.ssoadmin.model.Tag;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.sso.permissionset.AbstractTestBase;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static software.amazon.sso.permissionset.TestConstants.TEST_PERMISSION_SET_ARN;
import static software.amazon.sso.permissionset.TestConstants.TEST_SSO_INSTANCE_ARN;
import static software.amazon.sso.permissionset.utils.TagsUtil.getResourceTags;

@ExtendWith(MockitoExtension.class)
public class TagsUtilTest extends AbstractTestBase {

    private static final String TEST_NEXT_TOKEN = "nextToken";

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<SsoAdminClient> proxyClient;

    @Mock
    SsoAdminClient sso;

    @BeforeEach
    public void setup() {
        initMocks(proxyClient);
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        sso = mock(SsoAdminClient.class);
        when(proxyClient.client()).thenReturn(sso);
    }

    @Test
    public void listResourceMoreThanOneLoop() {
        List<Tag> ssoTags = new ArrayList<>();
        ssoTags.add(Tag.builder().key("key1").value("value1").build());

        ListTagsForResourceRequest listTagsRequest = ListTagsForResourceRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .resourceArn(TEST_PERMISSION_SET_ARN)
                .build();
        ListTagsForResourceRequest listTagsSecondRequest = ListTagsForResourceRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .resourceArn(TEST_PERMISSION_SET_ARN)
                .nextToken(TEST_NEXT_TOKEN)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listTagsRequest, proxyClient.client()::listTagsForResource))
                .thenReturn(ListTagsForResourceResponse.builder().tags(ssoTags).nextToken(TEST_NEXT_TOKEN).build());
        when(proxy.injectCredentialsAndInvokeV2(listTagsSecondRequest, proxyClient.client()::listTagsForResource))
                .thenReturn(ListTagsForResourceResponse.builder().build());

        List<Tag> tags = getResourceTags(TEST_SSO_INSTANCE_ARN, TEST_PERMISSION_SET_ARN, proxy, proxyClient);

        assertThat(Tag.builder().key("key1").value("value1").build()).isIn(tags);
    }

    @Test
    public void listResourceReturnEmptyList() {
        List<Tag> ssoTags = new ArrayList<>();

        ListTagsForResourceRequest listTagsRequest = ListTagsForResourceRequest.builder()
                .instanceArn(TEST_SSO_INSTANCE_ARN)
                .resourceArn(TEST_PERMISSION_SET_ARN)
                .build();
        when(proxy.injectCredentialsAndInvokeV2(listTagsRequest, proxyClient.client()::listTagsForResource))
                .thenReturn(ListTagsForResourceResponse.builder().tags(ssoTags).build());

        List<Tag> tags = getResourceTags(TEST_SSO_INSTANCE_ARN, TEST_PERMISSION_SET_ARN, proxy, proxyClient);

        assertThat(tags.size()).isEqualTo(0);
    }
}
