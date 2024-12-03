package com.example.demotestes;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

@SpringBootApplication
public class DemoTestEsApplication {

    @Autowired
    ElasticsearchProperties property;

    public static void main(String[] args) {
        SpringApplication.run(DemoTestEsApplication.class, args);
    }

    /**
     * 获取elasticsearch客户端
     *
     * @return {@link ElasticsearchClient}
     */
    @Bean
    public ElasticsearchClient esClient(RestClient restClient) {
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }

    @Bean
    public RestClient restClient(ElasticsearchProperties properties) {
        HttpHost[] hosts = properties.getUris().stream().map(this::createHttpHost).toArray(HttpHost[]::new);
        RestClientBuilder restClient = RestClient.builder(hosts);
        PropertyMapper map = PropertyMapper.get();
        map.from(properties::getUsername).whenHasText().to((username) -> {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            Credentials credentials = new UsernamePasswordCredentials(properties.getUsername(),
                    properties.getPassword());
            credentialsProvider.setCredentials(AuthScope.ANY, credentials);
            restClient.setHttpClientConfigCallback(
                    (httpClientBuilder) -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        });
        restClient.setRequestConfigCallback((requestConfigBuilder) -> {
            map.from(properties::getConnectionTimeout).whenNonNull().asInt(Duration::toMillis)
                    .to(requestConfigBuilder::setConnectTimeout);
            map.from(properties::getSocketTimeout).whenNonNull().asInt(Duration::toMillis)
                    .to(requestConfigBuilder::setSocketTimeout);
            return requestConfigBuilder;
        });
        return restClient.build();
    }

    // ---------------private method------------------------------------------------------------------------

    private HttpHost createHttpHost(String uri) {
        try {
            return createHttpHost(URI.create(uri));
        } catch (IllegalArgumentException ex) {
            return HttpHost.create(uri);
        }
    }

    private HttpHost createHttpHost(URI uri) {
        if (!StringUtils.hasLength(uri.getUserInfo())) {
            return HttpHost.create(uri.toString());
        }
        try {
            return HttpHost.create(new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(),
                    uri.getQuery(), uri.getFragment()).toString());
        } catch (URISyntaxException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
