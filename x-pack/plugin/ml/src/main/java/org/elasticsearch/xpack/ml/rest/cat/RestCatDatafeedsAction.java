/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.ml.rest.cat;

import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.Table;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.action.RestResponseListener;
import org.elasticsearch.rest.action.cat.AbstractCatAction;
import org.elasticsearch.rest.action.cat.RestTable;
import org.elasticsearch.xpack.core.ml.action.GetDatafeedsStatsAction;
import org.elasticsearch.xpack.core.ml.datafeed.DatafeedConfig;
import org.elasticsearch.xpack.core.ml.datafeed.DatafeedTimingStats;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static org.elasticsearch.rest.RestRequest.Method.GET;

public class RestCatDatafeedsAction extends AbstractCatAction {

    @Override
    public List<Route> routes() {
        return unmodifiableList(asList(
            new Route(GET, "_cat/ml/datafeeds/{" + DatafeedConfig.ID.getPreferredName() + "}"),
            new Route(GET, "_cat/ml/datafeeds")));
    }

    @Override
    public String getName() {
        return "cat_ml_get_datafeeds_action";
    }

    @Override
    protected RestChannelConsumer doCatRequest(RestRequest restRequest, NodeClient client) {
        String datafeedId = restRequest.param(DatafeedConfig.ID.getPreferredName());
        if (Strings.isNullOrEmpty(datafeedId)) {
            datafeedId = GetDatafeedsStatsAction.ALL;
        }
        GetDatafeedsStatsAction.Request request = new GetDatafeedsStatsAction.Request(datafeedId);
        request.setAllowNoDatafeeds(restRequest.paramAsBoolean(GetDatafeedsStatsAction.Request.ALLOW_NO_DATAFEEDS.getPreferredName(),
            request.allowNoDatafeeds()));
        return channel -> client.execute(GetDatafeedsStatsAction.INSTANCE, request, new RestResponseListener<>(channel) {
            @Override
            public RestResponse buildResponse(GetDatafeedsStatsAction.Response getDatafeedsStatsRespons) throws Exception {
                return RestTable.buildResponse(buildTable(restRequest, getDatafeedsStatsRespons), channel);
            }
        });
    }

    @Override
    protected void documentation(StringBuilder sb) {
        sb.append("/_cat/ml/datafeeds\n");
        sb.append("/_cat/ml/datafeeds/{datafeed_id}\n");
    }

    @Override
    protected Table getTableWithHeader(RestRequest request) {
        Table table = new Table();
        table.startHeaders();

        // Datafeed Info
        table.addCell("id", TableColumnAttributeBuilder.builder().setDescription("the datafeed_id").build());
        table.addCell("state", TableColumnAttributeBuilder.builder()
            .setDescription("the datafeed state")
            .setAliases("s")
            .setTextAlignment(TableColumnAttributeBuilder.TextAlign.RIGHT)
            .build());
        table.addCell("assignment_explanation",
            TableColumnAttributeBuilder.builder("why the datafeed is or is not assigned to a node", false)
                .setAliases("ae")
                .build());

        // Timing stats
        table.addCell("bucket.count",
            TableColumnAttributeBuilder.builder("bucket count")
                .setAliases("bc", "bucketCount")
                .build());
        table.addCell("search.count",
            TableColumnAttributeBuilder.builder("number of searches ran by the datafeed")
                .setAliases("sc", "searchCount")
                .build());
        table.addCell("search.time",
            TableColumnAttributeBuilder.builder("the total search time", false)
                .setAliases("st", "searchTime")
                .build());
        table.addCell("search.bucket_avg",
            TableColumnAttributeBuilder.builder("the average search time per bucket (millisecond)", false)
                .setAliases("sba", "bucketTimeMin")
                .build());
        table.addCell("search.exp_avg_hour",
            TableColumnAttributeBuilder.builder("the exponential average search time per hour (millisecond)", false)
                .setAliases("seah", "searchExpAvgHour")
                .build());

        //Node info
        table.addCell("node.id",
            TableColumnAttributeBuilder.builder("id of the assigned node", false)
                .setAliases("ni", "nodeId")
                .build());
        table.addCell("node.name",
            TableColumnAttributeBuilder.builder("name of the assigned node", false)
                .setAliases("nn", "nodeName")
                .build());
        table.addCell("node.ephemeral_id",
            TableColumnAttributeBuilder.builder("ephemeral id of the assigned node", false)
                .setAliases("ne", "nodeEphemeralId")
                .build());
        table.addCell("node.address",
            TableColumnAttributeBuilder.builder("network address of the assigned node", false)
                .setAliases("na", "nodeAddress")
                .build());

        table.endHeaders();
        return table;
    }

    private Table buildTable(RestRequest request, GetDatafeedsStatsAction.Response dfStats) {
        Table table = getTableWithHeader(request);
        dfStats.getResponse().results().forEach(df -> {
            table.startRow();
            table.addCell(df.getDatafeedId());
            table.addCell(df.getDatafeedState().toString());
            table.addCell(df.getAssignmentExplanation());

            DatafeedTimingStats timingStats = df.getTimingStats();
            table.addCell(timingStats == null ? 0 : timingStats.getBucketCount());
            table.addCell(timingStats == null ? 0 : timingStats.getSearchCount());
            table.addCell(timingStats == null ?
                TimeValue.timeValueMillis(0) :
                TimeValue.timeValueMillis((long)timingStats.getTotalSearchTimeMs()));
            table.addCell(timingStats == null || timingStats.getBucketCount() == 0 ? 0.0 : timingStats.getAvgSearchTimePerBucketMs());
            table.addCell(timingStats == null ? 0.0 : timingStats.getExponentialAvgSearchTimePerHourMs());

            DiscoveryNode node = df.getNode();
            table.addCell(node == null ? null : node.getId());
            table.addCell(node == null ? null : node.getName());
            table.addCell(node == null ? null : node.getEphemeralId());
            table.addCell(node == null ? null : node.getAddress().toString());

            table.endRow();
        });
        return table;
    }
}
