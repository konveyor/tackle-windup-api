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

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseBroadcaster;
import javax.ws.rs.sse.SseEventSink;

@Singleton
@Path("/windup/analysisSse")
public class WindupBroadcasterResource {

    private Sse sse;
    private volatile SseBroadcaster broadcaster;

    @Context
    public void setSse(final Sse sse) {
        this.sse = sse;
        if (broadcaster == null) this.broadcaster = sse.newBroadcaster();
    }

    public void broadcastMessage(String message) {
        if (sse != null && broadcaster != null) {
            final OutboundSseEvent event = sse.newEventBuilder()
                    .name("message")
                    .mediaType(MediaType.APPLICATION_JSON_TYPE)
                    .data(String.class, message)
                    .build();
            broadcaster.broadcast(event);
        }
    }

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void listenToBroadcast(@Context SseEventSink eventSink) {
        this.broadcaster.register(eventSink);
    }
}
