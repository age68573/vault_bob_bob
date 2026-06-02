package com.example.northstar.rest;

import com.example.northstar.config.ApplicationConfig;
import com.example.northstar.config.MongoClientProvider;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.bson.Document;

import java.util.LinkedHashMap;
import java.util.Map;

@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @GET
    public Response health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("application", "northstar-customer-center");

        try {
            MongoClientProvider.get().getDatabase(ApplicationConfig.mongodbDatabase())
                    .runCommand(new Document("ping", 1));
            status.put("status", "UP");
            status.put("mongodb", "UP");
            return Response.ok(status).build();
        } catch (RuntimeException | LinkageError exception) {
            status.put("status", "DOWN");
            status.put("mongodb", "DOWN");
            status.put("message", exception.getClass().getSimpleName());
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(status).build();
        }
    }
}
