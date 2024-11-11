package com.example.demotestes;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @Description
 * @Author xr
 * @Date 2024/10/30 10:17
 */
@Component
public class CommonConfig implements DisposableBean {

    @Autowired
    ElasticsearchClient elasticsearchClient;

	// TODO  If commented out, the process did not exit when there was a port conflict
    @Override
    public void destroy() throws Exception {
//        elasticsearchClient._transport().close();
    }
}
