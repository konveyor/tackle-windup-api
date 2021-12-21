package io.tackle.windup.rest.rest;

import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
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
