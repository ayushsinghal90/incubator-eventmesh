/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.runtime.core.protocol.http.processor;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.http.HttpEventWrapper;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestURI;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.common.EventMeshTrace;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.AbstractEventProcessor;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.apache.eventmesh.runtime.util.WebhookUtil;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

@EventMeshTrace(isEnable = false)
public class RemoteSubscribeEventProcessor extends AbstractEventProcessor {

    public Logger httpLogger = LoggerFactory.getLogger(EventMeshConstants.PROTOCOL_HTTP);

    public Logger aclLogger = LoggerFactory.getLogger(EventMeshConstants.ACL);


    public RemoteSubscribeEventProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        super(eventMeshHTTPServer);
    }

    @Override
    public void handler(HandlerService.HandlerSpecific handlerSpecific, HttpRequest httpRequest) throws Exception {

        AsyncContext<HttpEventWrapper> asyncContext = handlerSpecific.getAsyncContext();

        ChannelHandlerContext ctx = handlerSpecific.getCtx();

        HttpEventWrapper requestWrapper = asyncContext.getRequest();

        httpLogger.info("uri={}|{}|client2eventMesh|from={}|to={}", requestWrapper.getRequestURI(),
            EventMeshConstants.PROTOCOL_HTTP, RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtils.getLocalAddress()
        );

        // user request header
        Map<String, Object> userRequestHeaderMap = requestWrapper.getHeaderMap();
        String requestIp = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
        userRequestHeaderMap.put(ProtocolKey.ClientInstanceKey.IP, requestIp);

        // build sys header
        requestWrapper.buildSysHeaderForClient();


        Map<String, Object> responseHeaderMap = builderResponseHeaderMap(requestWrapper);

        Map<String, Object> sysHeaderMap = requestWrapper.getSysHeaderMap();

        Map<String, Object> responseBodyMap = new HashMap<>();

        //validate header
        if (validateSysHeader(sysHeaderMap)) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, responseHeaderMap,
                responseBodyMap, null);
            return;
        }


        //validate body
        byte[] requestBody = requestWrapper.getBody();

        Map<String, Object> requestBodyMap = Optional.ofNullable(JsonUtils.deserialize(
            new String(requestBody, Constants.DEFAULT_CHARSET),
            new TypeReference<HashMap<String, Object>>() {}
        )).orElse(Maps.newHashMap());


        if (requestBodyMap.get(EventMeshConstants.URL) == null
            || requestBodyMap.get(EventMeshConstants.MANAGE_TOPIC) == null
            || requestBodyMap.get(EventMeshConstants.CONSUMER_GROUP) == null) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                responseBodyMap, null);
            return;
        }

        String url = requestBodyMap.get(EventMeshConstants.URL).toString();
        String consumerGroup = requestBodyMap.get(EventMeshConstants.CONSUMER_GROUP).toString();
        String topic = JsonUtils.serialize(requestBodyMap.get(EventMeshConstants.MANAGE_TOPIC));


        // SubscriptionItem
        List<SubscriptionItem> subscriptionList = Optional.ofNullable(JsonUtils.deserialize(
            topic,
            new TypeReference<List<SubscriptionItem>>() {}
        )).orElse(Collections.emptyList());

        //do acl check
        if (eventMeshHTTPServer.getEventMeshHttpConfiguration().isEventMeshServerSecurityEnable()) {
            String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            String user = sysHeaderMap.get(ProtocolKey.ClientInstanceKey.USERNAME).toString();
            String pass = sysHeaderMap.get(ProtocolKey.ClientInstanceKey.PASSWD).toString();
            String subsystem = sysHeaderMap.get(ProtocolKey.ClientInstanceKey.SYS).toString();
            for (SubscriptionItem item : subscriptionList) {
                try {
                    Acl.doAclCheckInHttpReceive(remoteAddr, user, pass, subsystem, item.getTopic(),
                        requestWrapper.getRequestURI());
                } catch (Exception e) {
                    aclLogger.warn("CLIENT HAS NO PERMISSION,SubscribeProcessor subscribe failed", e);
                    handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_ACL_ERR, responseHeaderMap,
                        responseBodyMap, null);

                    return;
                }
            }
        }

        // validate URL
        try {
            if (!IPUtils.isValidDomainOrIp(url, eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIpv4BlackList,
                eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIpv6BlackList)) {
                httpLogger.error("subscriber url {} is not valid", url);
                handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                    responseBodyMap, null);
                return;
            }
        } catch (Exception e) {
            httpLogger.error("subscriber url {} is not valid, error {}", url, e.getMessage());
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                responseBodyMap, null);
            return;
        }

        // obtain webhook delivery agreement for Abuse Protection
        boolean isWebhookAllowed = WebhookUtil.obtainDeliveryAgreement(eventMeshHTTPServer.httpClientPool.getClient(),
            url, eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshWebhookOrigin());

        if (!isWebhookAllowed) {
            httpLogger.error("subscriber url {} is not allowed by the target system", url);
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                responseBodyMap, null);
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            // request to remote

            String env = eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEnv();
            String idc = eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIDC();
            String cluster = eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshCluster();
            String sysId = eventMeshHTTPServer.getEventMeshHttpConfiguration().getSysID();
            String meshGroup = env + "-" + idc + "-" + cluster + "-" + sysId;

            Map<String, String> remoteHeaderMap = new HashMap<>();
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.ENV, env);
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.IDC, idc);
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.IP, IPUtils.getLocalAddress());
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.PID, String.valueOf(ThreadUtils.getPID()));
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.SYS, sysId);
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.USERNAME, EventMeshConstants.USER_NAME);
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.PASSWD, EventMeshConstants.PASSWD);
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.PRODUCERGROUP, meshGroup);
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.CONSUMERGROUP, meshGroup);

            // local subscription url
            String localUrl = "http://" + IPUtils.getLocalAddress() + ":"
                + eventMeshHTTPServer.getEventMeshHttpConfiguration().httpServerPort
                + RequestURI.PUBLISH_BRIDGE.getRequestURI();

            Map<String, Object> remoteBodyMap = new HashMap<>();
            remoteBodyMap.put(EventMeshConstants.URL, localUrl);
            remoteBodyMap.put(EventMeshConstants.CONSUMER_GROUP, meshGroup);
            remoteBodyMap.put(EventMeshConstants.MANAGE_TOPIC, requestBodyMap.get(EventMeshConstants.MANAGE_TOPIC));

            String targetMesh = requestBodyMap.get("remoteMesh") == null ? "" : requestBodyMap.get("remoteMesh").toString();

            // Get mesh address from registry
            String meshAddress = getTargetMesh(consumerGroup, subscriptionList);
            if (StringUtils.isNotBlank(meshAddress)) {
                targetMesh = meshAddress;
            }


            CloseableHttpClient closeableHttpClient = eventMeshHTTPServer.httpClientPool.getClient();

            String remoteResult = post(closeableHttpClient, targetMesh, remoteHeaderMap, remoteBodyMap,
                response -> EntityUtils.toString(response.getEntity(), Constants.DEFAULT_CHARSET));

            Map<String, String> remoteResultMap = Optional.ofNullable(JsonUtils.deserialize(
                remoteResult,
                new TypeReference<Map<String, String>>() {}
            )).orElse(Maps.newHashMap());

            if (String.valueOf(EventMeshRetCode.SUCCESS.getRetCode()).equals(remoteResultMap.get(EventMeshConstants.RET_CODE))) {
                responseBodyMap.put(EventMeshConstants.RET_CODE, EventMeshRetCode.SUCCESS.getRetCode());
                responseBodyMap.put(EventMeshConstants.RET_MSG, EventMeshRetCode.SUCCESS.getErrMsg());

                handlerSpecific.sendResponse(responseHeaderMap, responseBodyMap);
            } else {
                handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_SUBSCRIBE_ERR, responseHeaderMap,
                    responseBodyMap, null);
            }

        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            httpLogger.error(
                "message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}"
                    + "|bizSeqNo={}|uniqueId={}", endTime - startTime,
                JsonUtils.serialize(subscriptionList), url, e);
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_SUBSCRIBE_ERR, responseHeaderMap,
                responseBodyMap, null);
        }
    }

    @Override
    public String[] paths() {
        return new String[] {RequestURI.SUBSCRIBE_REMOTE.getRequestURI()};
    }

    public static String post(CloseableHttpClient client, String uri,
                              Map<String, String> requestHeader, Map<String, Object> requestBody,
                              ResponseHandler<String> responseHandler) throws IOException {
        Preconditions.checkState(client != null, "client can't be null");
        Preconditions.checkState(StringUtils.isNotBlank(uri), "uri can't be null");
        Preconditions.checkState(requestHeader != null, "requestParam can't be null");
        Preconditions.checkState(responseHandler != null, "responseHandler can't be null");

        HttpPost httpPost = new HttpPost(uri);

        httpPost.addHeader("Content-Type", ContentType.APPLICATION_JSON.getMimeType());

        //header
        if (MapUtils.isNotEmpty(requestHeader)) {
            for (Map.Entry<String, String> entry : requestHeader.entrySet()) {
                httpPost.addHeader(entry.getKey(), entry.getValue());
            }
        }

        //body
        if (MapUtils.isNotEmpty(requestBody)) {
            String jsonStr = Optional.ofNullable(JsonUtils.serialize(requestBody)).orElse("");
            httpPost.setEntity(new StringEntity(jsonStr, ContentType.APPLICATION_JSON));
        }

        //ttl
        RequestConfig.Builder configBuilder = RequestConfig.custom();
        configBuilder.setSocketTimeout(Integer.parseInt(String.valueOf(Constants.DEFAULT_HTTP_TIME_OUT)))
            .setConnectTimeout(Integer.parseInt(String.valueOf(Constants.DEFAULT_HTTP_TIME_OUT)))
            .setConnectionRequestTimeout(Integer.parseInt(String.valueOf(Constants.DEFAULT_HTTP_TIME_OUT)));

        httpPost.setConfig(configBuilder.build());

        return client.execute(httpPost, responseHandler);
    }

}
