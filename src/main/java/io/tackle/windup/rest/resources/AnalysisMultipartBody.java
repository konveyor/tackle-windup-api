/*
 * Copyright © 2021 the Konveyor Contributors (https://konveyor.io/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.tackle.windup.rest.resources;

import org.jboss.resteasy.annotations.providers.multipart.PartType;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.core.MediaType;
import java.io.InputStream;

public class AnalysisMultipartBody {
    @FormParam("application") @PartType(MediaType.APPLICATION_OCTET_STREAM) InputStream applicationFile;
    @FormParam("applicationFileName") @PartType(MediaType.TEXT_PLAIN) String applicationFileName;
    @FormParam("sources") @PartType(MediaType.TEXT_PLAIN) @DefaultValue("") String sources;
    @FormParam("targets") @PartType(MediaType.TEXT_PLAIN) String targets;
    @FormParam("packages") @PartType(MediaType.TEXT_PLAIN) @DefaultValue("") String packages;
    @FormParam("sourceMode") @PartType(MediaType.TEXT_PLAIN) @DefaultValue("false") Boolean sourceMode;
}
