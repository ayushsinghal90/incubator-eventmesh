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

package org.apache.eventmesh.runtime.admin.handler;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.NetUtils;
import org.apache.eventmesh.runtime.admin.controller.HttpHandlerManager;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.common.EventHttpHandler;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;

@EventHttpHandler(path = "/clientManage/rejectClientBySubSystem")
public class RejectClientBySubSystemHandler extends AbstractHttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RejectClientBySubSystemHandler.class);

    private final transient EventMeshTCPServer eventMeshTCPServer;

    public RejectClientBySubSystemHandler(EventMeshTCPServer eventMeshTCPServer, HttpHandlerManager httpHandlerManager) {
        super(httpHandlerManager);
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    private String printClients(List<InetSocketAddress> clients) {
        if (clients == null || clients.isEmpty()) {
            return "no session had been closed";
        }
        StringBuilder sb = new StringBuilder();
        for (InetSocketAddress addr : clients) {
            sb.append(addr).append("|");
        }
        return sb.toString();
    }

    /**
     * remove c client by dcn and susysId
     *
     * @param httpExchange
     * @throws IOException
     */
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String result = "";
        OutputStream out = httpExchange.getResponseBody();
        try {
            String queryString = httpExchange.getRequestURI().getQuery();
            Map<String, String> queryStringInfo = NetUtils.formData2Dic(queryString);
            String subSystem = queryStringInfo.get(EventMeshConstants.MANAGE_SUBSYSTEM);

            if (StringUtils.isBlank(subSystem)) {
                NetUtils.sendSuccessResponseHeaders(httpExchange);
                result = "params illegal!";
                out.write(result.getBytes(Constants.DEFAULT_CHARSET));
                return;
            }

            LOGGER.info("rejectClientBySubSystem in admin,subsys:{}====================", subSystem);
            ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
            ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
            final List<InetSocketAddress> successRemoteAddrs = new ArrayList<InetSocketAddress>();
            try {
                if (!sessionMap.isEmpty()) {
                    for (Session session : sessionMap.values()) {
                        if (session.getClient().getSubsystem().equals(subSystem)) {
                            InetSocketAddress addr = EventMeshTcp2Client.serverGoodby2Client(eventMeshTCPServer, session,
                                    clientSessionGroupMapping);
                            if (addr != null) {
                                successRemoteAddrs.add(addr);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("clientManage|rejectClientBySubSystem|fail|subSystemId={},errMsg={}", subSystem, e);
                result = String.format("rejectClientBySubSystem fail! sessionMap size {%d}, had reject {%s} , {"
                                +
                                "subSystemId=%s}, errorMsg : %s", sessionMap.size(), printClients(successRemoteAddrs),
                        subSystem, e.getMessage());
                NetUtils.sendSuccessResponseHeaders(httpExchange);
                out.write(result.getBytes(Constants.DEFAULT_CHARSET));
                return;
            }
            result = String.format("rejectClientBySubSystem success! sessionMap size {%d}, had reject {%s} , {"
                    +
                    "subSystemId=%s}", sessionMap.size(), printClients(successRemoteAddrs), subSystem);
            NetUtils.sendSuccessResponseHeaders(httpExchange);
            out.write(result.getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            LOGGER.error("rejectClientBySubSystem fail...", e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    LOGGER.warn("out close failed...", e);
                }
            }
        }

    }
}
