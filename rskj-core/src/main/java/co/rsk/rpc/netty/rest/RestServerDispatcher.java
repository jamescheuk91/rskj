/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.rpc.netty.rest;

import co.rsk.rpc.netty.rest.dto.RestModuleConfigDTO;
import co.rsk.rpc.netty.rest.modules.HealthCheckModule;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

public class RestServerDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(RestServerDispatcher.class);

    private RestModuleConfigDTO restModuleConfigDTO;
    private HealthCheckModule healthCheckModule;

    public RestServerDispatcher(RestModuleConfigDTO restModuleConfigDTO) {
        this.restModuleConfigDTO = restModuleConfigDTO;
        initModules();
    }

    public DefaultFullHttpResponse dispatch(HttpRequest request) throws URISyntaxException {

        String uri = new URI(request.getUri()).getPath();

        if (uri.startsWith("/health-check")) {
            if (!restModuleConfigDTO.isHealthCheckModuleEnabled()) {
                logger.info("Health check request received but module is disabled.");
                return RestUtils.createResponse("Not Found", HttpResponseStatus.NOT_FOUND);
            }
            logger.info("Health check request received. Dispatching.");
            return healthCheckModule.processRequest(uri, request.getMethod());
        }

        logger.info("Handler Not Found.");
        return RestUtils.createResponse("Not Found", HttpResponseStatus.NOT_FOUND);

    }

    private void initModules() {
        if (restModuleConfigDTO.isHealthCheckModuleEnabled()) {
            healthCheckModule = new HealthCheckModule();
        }
    }

}
