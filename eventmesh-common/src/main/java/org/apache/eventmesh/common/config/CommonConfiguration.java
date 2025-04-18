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

package org.apache.eventmesh.common.config;

import org.apache.eventmesh.common.utils.AssertUtils;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.common.utils.IPUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;

import lombok.Data;

@Data
public class CommonConfiguration {
    private transient String eventMeshEnv = "P";
    private transient String eventMeshIDC = "FT";
    private transient String eventMeshCluster = "LS";
    
    private transient String eventMeshName = "";
    private transient List<String> eventMeshProvideServerProtocols;
    private transient String sysID = "5477";
    private transient String eventMeshConnectorPluginType = "rocketmq";
    private transient String eventMeshSecurityPluginType = "security";
    private transient String eventMeshRegistryPluginType = "namesrv";
    private transient List<String> eventMeshMetricsPluginType;
    private transient String eventMeshTracePluginType;

    private transient String namesrvAddr = "";
    private transient String eventMeshRegistryPluginUsername = "";
    private transient String eventMeshRegistryPluginPassword = "";
    private transient Integer eventMeshRegisterIntervalInMills = 10 * 1000;
    private transient Integer eventMeshFetchRegistryAddrInterval = 10 * 1000;
    private transient String eventMeshServerIp = null;
    private transient boolean eventMeshServerSecurityEnable = false;
    
    private transient boolean eventMeshServerRegistryEnable = false;
    
    private transient boolean eventMeshServerTraceEnable = false;

    protected transient ConfigurationWrapper configurationWrapper;

    private transient String eventMeshWebhookOrigin = "eventmesh." + eventMeshIDC;

    public CommonConfiguration(ConfigurationWrapper configurationWrapper) {
        this.configurationWrapper = configurationWrapper;
    }
    

    public void init() {

        if (configurationWrapper != null) {
            eventMeshEnv = checkNotEmpty(ConfKeys.KEYS_EVENTMESH_ENV);

            sysID = checkNumeric(ConfKeys.KEYS_EVENTMESH_SYSID);

            eventMeshCluster = checkNotEmpty(ConfKeys.KEYS_EVENTMESH_SERVER_CLUSTER);

            eventMeshName = checkNotEmpty(ConfKeys.KEYS_EVENTMESH_SERVER_NAME);

            eventMeshIDC = checkNotEmpty(ConfKeys.KEYS_EVENTMESH_IDC);

            eventMeshServerIp = get(ConfKeys.KEYS_EVENTMESH_SERVER_HOST_IP, IPUtils::getLocalAddress);

            eventMeshConnectorPluginType = checkNotEmpty(ConfKeys.KEYS_ENENTMESH_CONNECTOR_PLUGIN_TYPE);

            eventMeshServerSecurityEnable = Boolean.parseBoolean(get(ConfKeys.KEYS_EVENTMESH_SECURITY_ENABLED, () -> "false"));

            eventMeshSecurityPluginType = checkNotEmpty(ConfKeys.KEYS_ENENTMESH_SECURITY_PLUGIN_TYPE);

            eventMeshServerRegistryEnable = Boolean.parseBoolean(get(ConfKeys.KEYS_EVENTMESH_REGISTRY_ENABLED, () -> "false"));

            eventMeshRegistryPluginType = checkNotEmpty(ConfKeys.KEYS_ENENTMESH_REGISTRY_PLUGIN_TYPE);

            namesrvAddr = checkNotEmpty(ConfKeys.KEYS_EVENTMESH_REGISTRY_PULGIN_SERVER_ADDR);

            eventMeshRegistryPluginUsername = 
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_REGISTRY_PULGIN_USERNAME, "");

            eventMeshRegistryPluginPassword = 
                    configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_REGISTRY_PULGIN_PASSWORD, "");

            String metricsPluginType = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_METRICS_PLUGIN_TYPE);
            if (StringUtils.isNotEmpty(metricsPluginType)) {
                eventMeshMetricsPluginType = Arrays
                        .stream(metricsPluginType.split(","))
                        .filter(StringUtils::isNotBlank)
                        .map(String::trim)
                        .collect(Collectors.toList());
            }

            eventMeshProvideServerProtocols = getProvideServerProtocols();

            eventMeshServerTraceEnable = Boolean.parseBoolean(get(ConfKeys.KEYS_EVENTMESH_TRACE_ENABLED, () -> "false"));
            if (eventMeshServerTraceEnable) {
                eventMeshTracePluginType = checkNotEmpty(ConfKeys.KEYS_EVENTMESH_TRACE_PLUGIN_TYPE);
            }
        }
    }

    private String checkNotEmpty(String key) {
        String value = configurationWrapper.getProp(key);
        if (value != null) {
            value = StringUtils.deleteWhitespace(value);
        }
        AssertUtils.notBlack(value, key + " is invalidated");
        return value;
    }

    private String checkNumeric(String key) {
        String value = configurationWrapper.getProp(key);
        if (value != null) {
            value = StringUtils.deleteWhitespace(value);
        }
        Preconditions.checkState(StringUtils.isNotEmpty(value) && StringUtils.isNumeric(value), key + " is invalidated");
        return value;
    }

    private String get(String key, Supplier<String> defaultValueSupplier) {
        String value = configurationWrapper.getProp(key);
        if (value != null) {
            value = StringUtils.deleteWhitespace(value);
        }
        return StringUtils.isEmpty(value) ? defaultValueSupplier.get() : value;
    }

    public List<String> getProvideServerProtocols() {
        String provideProtocols = configurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_SERVER_PROVIDE_PROTOCOLS);
        if (StringUtils.isNotEmpty(provideProtocols)) {
            return Arrays.stream(provideProtocols.split(",")).filter(StringUtils::isNotBlank).map(String::trim).collect(Collectors.toList());
        } else {
            return Collections.singletonList(ConfigurationContextUtil.HTTP);
        }
    }

    static class ConfKeys {
        public static final String KEYS_EVENTMESH_ENV = "eventMesh.server.env";

        public static final String KEYS_EVENTMESH_IDC = "eventMesh.server.idc";

        public static final String KEYS_EVENTMESH_SYSID = "eventMesh.sysid";

        public static final String KEYS_EVENTMESH_SERVER_CLUSTER = "eventMesh.server.cluster";

        public static final String KEYS_EVENTMESH_SERVER_NAME = "eventMesh.server.name";

        public static final String KEYS_EVENTMESH_SERVER_PROVIDE_PROTOCOLS = "eventMesh.server.provide.protocols";

        public static final String KEYS_EVENTMESH_SERVER_HOST_IP = "eventMesh.server.hostIp";

        public static final String KEYS_EVENTMESH_SERVER_REGISTER_INTERVAL = "eventMesh.server.registry.registerIntervalInMills";

        public static final String KEYS_EVENTMESH_SERVER_FETCH_REGISTRY_ADDR_INTERVAL = "eventMesh.server.registry.fetchRegistryAddrIntervalInMills";

        public static final String KEYS_ENENTMESH_CONNECTOR_PLUGIN_TYPE = "eventMesh.connector.plugin.type";

        public static final String KEYS_EVENTMESH_SECURITY_ENABLED = "eventMesh.server.security.enabled";

        public static final String KEYS_ENENTMESH_SECURITY_PLUGIN_TYPE = "eventMesh.security.plugin.type";

        public static final String KEYS_EVENTMESH_REGISTRY_ENABLED = "eventMesh.registry.plugin.enabled";

        public static final String KEYS_ENENTMESH_REGISTRY_PLUGIN_TYPE = "eventMesh.registry.plugin.type";

        public static final String KEYS_EVENTMESH_REGISTRY_PULGIN_SERVER_ADDR = "eventMesh.registry.plugin.server-addr";

        public static final String KEYS_EVENTMESH_REGISTRY_PULGIN_USERNAME = "eventMesh.registry.plugin.username";

        public static final String KEYS_EVENTMESH_REGISTRY_PULGIN_PASSWORD = "eventMesh.registry.plugin.password";

        public static final String KEYS_EVENTMESH_METRICS_PLUGIN_TYPE = "eventMesh.metrics.plugin";

        public static final String KEYS_EVENTMESH_TRACE_ENABLED = "eventMesh.server.trace.enabled";

        public static final String KEYS_EVENTMESH_TRACE_PLUGIN_TYPE = "eventMesh.trace.plugin";
    }
}