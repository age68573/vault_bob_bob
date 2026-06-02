package com.example.northstar.rest;

import com.example.northstar.config.ApplicationConfig;
import com.example.northstar.config.MongoClientProvider;
import com.example.northstar.notification.SmtpNotificationService;
import com.example.northstar.security.AuthSupport;
import com.example.northstar.security.JwtService;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/customers")
@Produces(MediaType.APPLICATION_JSON)
public class CustomerResource {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private final SmtpNotificationService smtpNotificationService = new SmtpNotificationService();

    @GET
    public Response list(@jakarta.ws.rs.core.Context HttpHeaders headers,
                         @QueryParam("limit") Integer requestedLimit) {
        JwtService.TokenValidation validation = AuthSupport.validate(headers);
        if (!validation.valid()) return AuthSupport.unauthorized(validation);
        int limit = requestedLimit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(requestedLimit, MAX_LIMIT));
        List<Map<String, Object>> customers = new ArrayList<>();

        collection().find()
                .sort(Sorts.descending("createdAt"))
                .limit(limit)
                .forEach(document -> customers.add(toResponse(document)));

        return Response.ok(customers).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@jakarta.ws.rs.core.Context HttpHeaders headers, JsonObject request) {
        JwtService.TokenValidation validation = AuthSupport.validate(headers);
        if (!validation.valid()) return AuthSupport.unauthorized(validation);
        String name = trimmed(request, "name");
        String email = trimmed(request, "email");

        if (name.isEmpty() || email.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "name and email are required"))
                    .build();
        }

        Document customer = new Document("_id", new ObjectId())
                .append("name", name)
                .append("email", email)
                .append("createdAt", Instant.now().toString());
        collection().insertOne(customer);

        SmtpNotificationService.NotificationResult notification = smtpNotificationService.sendWelcome(email, name);
        Map<String, Object> response = toResponse(customer);
        response.put("notificationSent", notification.sent());
        response.put("notificationMessage", notification.message());
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    private MongoCollection<Document> collection() {
        return MongoClientProvider.get().getDatabase(ApplicationConfig.mongodbDatabase())
                .getCollection("customers");
    }

    private static String trimmed(JsonObject request, String name) {
        return request.getString(name, "").trim();
    }

    private static Map<String, Object> toResponse(Document document) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", document.getObjectId("_id").toHexString());
        response.put("name", document.getString("name"));
        response.put("email", document.getString("email"));
        response.put("createdAt", document.getString("createdAt"));
        return response;
    }
}
